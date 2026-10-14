package com.drnas.console;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Attempt a real but bounded `wine --version` startup with a user's staged
 * Android/Bionic Wine package. This is NOT wineboot and does not launch games.
 * No arbitrary file execution: only whitelisted Wine entrypoints with
 * verified 64-bit ARM ELF headers are eligible, and Android's own linker64
 * is used to request a dedicated process under the app UID.
 */
public final class WineLaunchProbe {
    private static final long MAX_ELF_BYTES=64L*1024*1024;
    // The GameNative ARM64EC upstream build generates bin/wine as a
    // symlink to ../lib/wine/aarch64-unix/wine (build-step-arm64ec.sh).
    // Some WCP producers turn that symlink into an empty placeholder.
    // The real loader is therefore also probed by its fixed library path.
    private static final String[] LAUNCH_CANDIDATES={
            "bin/wine", "bin/wine64",
            "lib/wine/aarch64-unix/wine", "lib/wine/aarch64-unix/wine64"
    };
    private static final String[] SUPPORT_CANDIDATES={
            "bin/wine-preloader", "lib/wine/aarch64-unix/wine-preloader",
            "bin/wineserver", "lib/wine/aarch64-unix/wineserver",
            "bin/wineboot"
    };
    private static final long TIMEOUT_SECONDS=12;

    public static final class Result {
        public final String status;
        public final boolean versionPassed;
        public final String candidate;
        Result(String status,boolean passed,String candidate) {
            this.status=status;this.versionPassed=passed;this.candidate=candidate;
        }
    }
    static final class Binary {
        final String description;
        final String interp;
        final boolean runnable;
        Binary(String description,String interp,boolean runnable){
            this.description=description;this.interp=interp;this.runnable=runnable;
        }
    }
    private static String firstBytes(byte[] bytes,int n) {
        StringBuilder s=new StringBuilder();
        for(int i=0;i<Math.min(n,24);i++)
            s.append(String.format(Locale.US,"%02x",bytes[i]&255));
        return s.toString();
    }
    private static boolean elf(byte[] h,int count) {
        return count>=20 && (h[0]&255)==0x7f && h[1]=='E' && h[2]=='L' && h[3]=='F';
    }
    private static long le64(RandomAccessFile file) throws IOException {
        long low=Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
        long high=Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
        return low | (high<<32);
    }

    /**
     * Read ELF's PT_INTERP instead of assuming Wine needs glibc.
     * Android/Bionic Wine is reported separately from glibc Wine.
     * This is informational and intentionally does not map executable pages.
     */
    static Binary inspect(File f) throws IOException {
        if(!f.isFile())return new Binary("not a regular file",null,false);
        if(f.length()<4 || f.length()>MAX_ELF_BYTES)
            return new Binary("size="+f.length()+" (outside ELF probe range)",null,false);
        try(RandomAccessFile raf=new RandomAccessFile(f,"r")) {
            byte[] h=new byte[64];int n=raf.read(h);
            String magic=firstBytes(h,n);
            if(!elf(h,n)) {
                String type=n>=2 && h[0]=='M' && h[1]=='Z' ? "Windows PE/MZ" :
                        n>=2 && h[0]=='#' && h[1]=='!' ? "shebang script" :
                        "non-ELF/unknown";
                String printable=new String(h,0,Math.min(n,24),StandardCharsets.ISO_8859_1)
                        .replaceAll("[^\\x20-\\x7e]","?");
                return new Binary(type+" magic="+magic+" ascii="+printable,null,false);
            }
            if(n<64)return new Binary("truncated ELF magic="+magic,null,false);
            int klass=h[4]&255,endianness=h[5]&255;
            int machine=(h[18]&255)|((h[19]&255)<<8);
            if(klass!=2 || endianness!=1 || machine!=183)
                return new Binary("ELF class="+klass+" data="+endianness+
                        " machine="+machine+" (expected ELF64 AArch64)",null,false);
            int type=(h[16]&255)|((h[17]&255)<<8);
            long phoff=0;
            for(int i=0;i<8;i++)phoff|=((long)h[32+i]&255)<<(8*i);
            int phentsize=(h[54]&255)|((h[55]&255)<<8);
            int phnum=(h[56]&255)|((h[57]&255)<<8);
            if(phentsize!=56 || phnum>512 || phoff<0 ||
                    phoff>f.length() || phnum*56L>f.length()-phoff)
                return new Binary("AArch64 ELF malformed program headers",null,false);
            String interpreter=null;
            for(int i=0;i<phnum;i++) {
                raf.seek(phoff+56L*i);
                int pt=Integer.reverseBytes(raf.readInt());
                if(pt!=3)continue; // PT_INTERP
                raf.seek(phoff+56L*i+8);
                long offset=le64(raf);
                raf.seek(phoff+56L*i+32);
                long size=le64(raf);
                if(offset<0 || size<2 || size>256 || offset>f.length() ||
                        size>f.length()-offset)
                    return new Binary("ELF64 AArch64 invalid PT_INTERP",null,false);
                byte[] value=new byte[(int)size];
                raf.seek(offset);raf.readFully(value);
                int len=0;while(len<value.length&&value[len]!=0)len++;
                interpreter=new String(value,0,len,StandardCharsets.US_ASCII);
                break;
            }
            // No dynamic interpreter is *not* proof of runnable Wine.
            boolean bionic=interpreter!=null && (interpreter.contains("linker64") ||
                    interpreter.contains("com.android.runtime"));
            String mode=bionic?"Android/Bionic":"unconfirmed target";
            return new Binary("ELF64 AArch64 type="+type+" interp="+
                    (interpreter==null?"(none)":interpreter)+" ["+mode+"]",
                    interpreter,bionic && (type==2||type==3));
        }
    }

    /** Android linker64 direct launch: the first argument must be an absolute
     * executable path (AOSP bionic linker_main.cpp, not GNU ld-linux). */
    static List<String> linkerCommand(File linker, File wine) {
        if(!linker.isAbsolute() || !wine.isAbsolute())
            throw new IllegalArgumentException("Android linker requires absolute paths");
        List<String> command=new ArrayList<>();
        command.add(linker.getAbsolutePath());
        command.add(wine.getAbsolutePath());
        command.add("--version");
        return command;
    }

    /**
     * Android linker64 remains /proc/self/exe for a Wine ELF started as
     * linker64 <wine>. Wine's bin/wine loader consequently derives an invalid
     * /apex/.../../lib/wine/aarch64-unix/ntdll.so default path. Wine 10's
     * load_ntdll() also searches WINEDLLPATH, so point it at this package's
     * own lib/wine root (NOT the architecture suffix). WINELOADER is used
     * later for child launching. No global library paths are modified.
     */
    static void configureWineDllLookup(Map<String,String> env,File libdir,File wine) {
        env.put("WINEDLLPATH",new File(libdir,"wine").getAbsolutePath());
        env.put("WINELOADER",wine.getAbsolutePath());
    }

    private static File prefixRoot(File prefix) {
        File nested=new File(prefix,".wine");
        return new File(nested,"drive_c").isDirectory()?nested:prefix;
    }

    private static File pathInside(File root,String name) throws IOException {
        String base=root.getCanonicalPath()+File.separator;
        File f=new File(root,name);
        if(!f.getCanonicalPath().startsWith(base))
            throw new IOException("Wine entry escaped staged component: "+name);
        return f;
    }

    /**
     * Select an actual Bionic-linked Wine ELF from known GameNative paths.
     * Empty bin/wine placeholders are diagnostic evidence, not launchers.
     * There is no arbitrary repository-wide executable search.
     */
    static File selectWine(File root,Consumer<String> report) throws Exception {
        File selected=null;
        for(String candidate:LAUNCH_CANDIDATES) {
            File f=pathInside(root,candidate);
            if(!f.isFile()) {
                report.accept("Wine entry "+candidate+": absent");
                continue;
            }
            Binary info=inspect(f);
            report.accept("Wine entry "+candidate+": "+info.description);
            if(f.length()==0)
                report.accept("Empty Wine placeholder at "+candidate+
                        " (upstream bin/wine may be a symlink)");
            if(selected==null && info.runnable) selected=f;
        }
        for(String candidate:SUPPORT_CANDIDATES) {
            File f=pathInside(root,candidate);
            if(f.isFile())
                report.accept("Supporting "+candidate+": "+inspect(f).description);
        }
        if(selected!=null)
            report.accept("Selected actual Bionic Wine entry: "+
                    root.toPath().relativize(selected.toPath()).toString());
        return selected;
    }

    /**
     * Returns a result only after a real Process.waitFor and exit-code check.
     * Failure is expected with Android SELinux/app-data execution policies.
     */
    public static Result run(File protonDir,JSONObject config,File prefix,
                             Consumer<String> report) throws Exception {
        String bin=WcpRuntimeStager.clean(config.getString("binPath"),false);
        String lib=WcpRuntimeStager.clean(config.getString("libPath"),false);
        File bindir=new File(protonDir,bin),libdir=new File(protonDir,lib);
        if(!bindir.isDirectory() || !libdir.isDirectory())
            return new Result("WINE_PATHS_MISSING",false,"");
        report.accept("GameNative/Bionic Wine probe — including lib/wine/aarch64-unix loader");
        File target=selectWine(protonDir,report);
        if(target==null) {
            report.accept("No valid Bionic ARM64 Wine loader at known bin/ or lib/wine/aarch64-unix paths.");
            return new Result("NO_BIONIC_ELF_WINE_ENTRY",false,"");
        }
        File linker=new File("/system/bin/linker64");
        if(!linker.isFile())return new Result("ANDROID_LINKER64_NOT_FOUND",false,target.getName());
        File truePrefix=prefixRoot(prefix);
        File tmp=new File(prefix.getParentFile(),"tmp");
        if(!tmp.isDirectory()&&!tmp.mkdirs())
            throw new IOException("Cannot prepare temp directory");
        StringBuilder libPath=new StringBuilder(libdir.getAbsolutePath());
        for(String sub:new String[]{"wine/aarch64-unix","wine","aarch64-unix"}) {
            File folder=new File(libdir,sub);
            if(folder.isDirectory())libPath.append(":").append(folder.getAbsolutePath());
        }
        // Android's linker64 is NOT GNU ld-linux: direct invocation syntax is
        // linker64 ABSOLUTE_ELF_PATH [ARGS...]. It has no --library-path
        // option; linker_main.cpp treats argv[1] as the executable path.
        // Set LD_LIBRARY_PATH in the child environment instead.
        List<String> args=linkerCommand(linker,target);
        report.accept("TRY REAL BIONIC WINE --version via Android linker64 ("+
                TIMEOUT_SECONDS+" second watchdog), from "+target.getAbsolutePath());
        report.accept("linker64 argv[1] = "+args.get(1));
        ProcessBuilder builder=new ProcessBuilder(args);
        builder.directory(protonDir);
        builder.redirectErrorStream(true);
        Map<String,String> env=builder.environment();
        env.put("WINEPREFIX",truePrefix.getAbsolutePath());
        env.put("WINEARCH","win64");
        env.put("HOME",prefix.getParentFile().getAbsolutePath());
        env.put("TMPDIR",tmp.getAbsolutePath());
        env.put("XDG_RUNTIME_DIR",tmp.getAbsolutePath());
        // Bionic parses LD_LIBRARY_PATH when dynamically loading guest ELF.
        // Passing --library-path on the linker command line is invalid.
        env.put("LD_LIBRARY_PATH",libPath.toString());
        configureWineDllLookup(env,libdir,target);
        File unixNtdll=new File(libdir,"wine/aarch64-unix/ntdll.so");
        report.accept("Wine UNIX ntdll.so at staged Proton path: "+
                (unixNtdll.isFile()?"present":"MISSING")+
                " | WINEDLLPATH="+env.get("WINEDLLPATH"));
        env.put("LC_ALL","C");
        env.put("WINEDEBUG","-all");
        env.put("PROTON_NO_NTSYNC","1"); // deterministic first test
        Process process;
        try {process=builder.start();}
        catch(IOException e) {
            report.accept("Android linker launch rejected: "+e);
            return new Result("ANDROID_LINKER_EXEC_DENIED",false,target.getName());
        }
        final Process running=process;
        final java.util.concurrent.atomic.AtomicBoolean versionSeen=
                new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread output=new Thread(()->{
            int lines=0;
            try(BufferedReader input=new BufferedReader(new InputStreamReader(
                    running.getInputStream(),StandardCharsets.UTF_8))) {
                String line;
                while((line=input.readLine())!=null) {
                    if(line.toLowerCase(Locale.ROOT).matches(".*\\bwine-[0-9]+\\.[0-9]+.*"))
                        versionSeen.set(true);
                    if(lines++ < 55)report.accept("[wine stdout] "+line.substring(0,Math.min(500,line.length())));
                }
            }catch(IOException ex){report.accept("Wine pipe: "+ex.getMessage());}
        },"drnas-wine-output");
        output.setDaemon(true);
        output.start();
        boolean ended;
        try { ended=process.waitFor(TIMEOUT_SECONDS,TimeUnit.SECONDS); }
        catch(InterruptedException e) {
            process.destroyForcibly();Thread.currentThread().interrupt();
            throw e;
        }
        if(!ended) {process.destroyForcibly();process.waitFor(2,TimeUnit.SECONDS);}
        output.join(1500);
        if(!ended) {
            report.accept("Wine --version watchdog timed out; process killed.");
            return new Result("WINE_VERSION_TIMEOUT",false,target.getName());
        }
        int exit=process.exitValue();
        report.accept("Wine --version real process exit="+exit+
                " versionBannerSeen="+versionSeen.get()+
                " (Android linker invocation; not Windows execution)");
        boolean passed=exit==0 && versionSeen.get();
        return new Result(passed?"WINE_VERSION_OK":exit!=0?"WINE_VERSION_EXIT_"+exit:
                "WINE_EXIT_0_WITHOUT_VERSION_BANNER",passed,target.getName());
    }
    private WineLaunchProbe(){}
}
