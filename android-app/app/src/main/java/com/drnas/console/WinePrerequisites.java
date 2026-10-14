package com.drnas.console;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Offline Wine prerequisites: an explicitly imported MSI/EXE (or APK-bundled
 * wine-mono.msi) is installed once per content hash AND per persistent prefix.
 * A failed installer is never recorded as installed.
 */
final class WinePrerequisites {
    static final String MONO="mono";
    static final String VC="vc";
    private static final long MAX_PACKAGE=400L*1024*1024;
    private static final int TIMEOUT_SEC=210;

    private static File vault(Context context) throws IOException {
        File root=new File(context.getFilesDir(),"wine-prerequisites");
        if(Files.isSymbolicLink(root.toPath()) ||
                (!root.isDirectory()&&!root.mkdirs()))
            throw new IOException("Unsafe prerequisite vault");
        return root;
    }
    private static String filename(String kind) {
        if(MONO.equals(kind))return "wine-mono.msi";
        if(VC.equals(kind))return "vc_redist.x64.exe";
        throw new IllegalArgumentException("Unknown prerequisite "+kind);
    }
    private static String sha(File file) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) {
            byte[] block=new byte[65536];int n;
            while((n=in.read(block))!=-1)if(n>0)md.update(block,0,n);
        }
        StringBuilder out=new StringBuilder();
        for(byte b:md.digest())out.append(String.format(Locale.US,"%02x",b&255));
        return out.toString();
    }
    private static void validate(File file,String kind) throws IOException {
        if(file.length()<8 || file.length()>MAX_PACKAGE)
            throw new IOException("Installer missing or outside 8 B–400 MiB limit");
        byte[] signature=new byte[8];
        try(InputStream input=new FileInputStream(file)) {
            if(input.read(signature)!=8)throw new IOException("Installer truncated");
        }
        if(MONO.equals(kind)) {
            byte[] ole={(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1};
            if(!Arrays.equals(signature,ole))
                throw new IOException("Wine Mono needs an MSI installer (OLE compound file)");
        } else if(signature[0]!='M' || signature[1]!='Z')
            throw new IOException("VC Redist must be a Windows .exe (MZ header)");
    }
    private static void copy(InputStream input,File dest,String kind) throws Exception {
        File temp=new File(dest.getParentFile(),".incoming-"+System.nanoTime());
        try {
            try(OutputStream output=new FileOutputStream(temp)) {
                byte[] bytes=new byte[65536];int n;long total=0;
                while((n=input.read(bytes))!=-1) {
                    if(n==0)continue;
                    total+=n;if(total>MAX_PACKAGE)throw new IOException("Installer exceeds 400 MiB");
                    output.write(bytes,0,n);
                }
            }
            validate(temp,kind);
            Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp.toPath());}
    }
    static String importUri(Context ctx,Uri uri,String kind) throws Exception {
        File target=new File(vault(ctx),filename(kind));
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)) {
            if(in==null)throw new IOException("Android file picker returned no data");
            copy(in,target,kind);
        }
        return "Ready for one-time Wine install: "+filename(kind)+" SHA-256 "+sha(target).substring(0,12);
    }
    static void stageBundledMono(Context ctx,Consumer<String> log) throws Exception {
        File target=new File(vault(ctx),filename(MONO));
        if(target.isFile())return;
        try(InputStream in=ctx.getAssets().open("wine-mono.msi")) {
            copy(in,target,MONO);
            log.accept("Bundled Wine Mono MSI staged for first Wine start.");
        } catch(FileNotFoundException absent) {
            // Explicitly do not claim Mono is installed when the APK lacks it.
        }
    }

    static void installPending(Context ctx,File prefix,File linker,File wine,
                               File cwd,Map<String,String> env,RawWineTrace raw,
                               Consumer<String> log) throws Exception {
        stageBundledMono(ctx,log);
        WinePrefixLayout.ensure(prefix,log);
        File state=new File(prefix,".drnas-prerequisite-state.properties");
        if(Files.isSymbolicLink(state.toPath()))throw new IOException("Unsafe installer marker");
        Properties done=new Properties();
        if(state.isFile())try(InputStream input=new FileInputStream(state)){done.load(input);}
        File root=vault(ctx);
        for(String kind:new String[]{MONO,VC}) {
            File source=new File(root,filename(kind));
            if(!source.isFile()) {
                if(MONO.equals(kind))log.accept("Wine Mono MSI not supplied/bundled yet; installer pending.");
                continue;
            }
            validate(source,kind);
            String fingerprint=sha(source);
            if(fingerprint.equals(done.getProperty(kind)))continue;
            if(Thread.currentThread().isInterrupted())return;
            String display=MONO.equals(kind)?"Wine Mono":"Microsoft VC++ Redistributable";
            log.accept("Installing "+display+" offline (one-time prerequisite)...");
            File setup=new File(prefix,"drive_c/DRNAS-Setup");
            if(Files.isSymbolicLink(setup.toPath()) ||
                    (!setup.isDirectory()&&!setup.mkdirs()))
                throw new IOException("Unsafe setup directory");
            File staged=new File(setup,filename(kind));
            if(Files.isSymbolicLink(staged.toPath()))
                throw new IOException("Unsafe installer destination");
            try {
                Files.copy(source.toPath(),staged.toPath(),StandardCopyOption.REPLACE_EXISTING);
                List<String> args=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
                args.set(args.size()-1,MONO.equals(kind)?"msiexec.exe":
                        "C:\\DRNAS-Setup\\vc_redist.x64.exe");
                if(MONO.equals(kind)) {
                    args.add("/i");args.add("C:\\DRNAS-Setup\\wine-mono.msi");
                    args.add("/qn");args.add("/norestart");
                } else {
                    args.add("/install");args.add("/quiet");args.add("/norestart");
                }
                ProcessBuilder builder=new ProcessBuilder(args).directory(cwd).redirectErrorStream(true);
                builder.environment().clear();builder.environment().putAll(env);
                Process process=builder.start();
                Thread stream=WineServerDiagnostics.pipe(process,"[prereq "+kind+"] ",
                        log,24,raw);
                int exit=-1;
                try {
                    if(process.waitFor(TIMEOUT_SEC,TimeUnit.SECONDS)) exit=process.exitValue();
                    else {
                        process.destroyForcibly();
                        process.waitFor(3,TimeUnit.SECONDS);
                        log.accept(display+" timed out; retry next session.");
                    }
                } finally {
                    if(process.isAlive())process.destroyForcibly();
                    stream.join(1500);
                    process.getInputStream().close();
                }
                if(exit==0 || exit==3010) {
                    done.setProperty(kind,fingerprint);
                    File temporary=new File(prefix,".drnas-prerequisite-state.tmp");
                    try(OutputStream output=new FileOutputStream(temporary)) {
                        done.store(output,"One-time Wine install exit status; not a dependency compatibility guarantee");
                    }
                    Files.move(temporary.toPath(),state.toPath(),StandardCopyOption.REPLACE_EXISTING);
                    log.accept(display+" installer exited "+exit+"; recorded for this prefix.");
                } else log.accept(display+" installer exit="+exit+"; NOT marked installed.");
            } catch(Exception failure) {
                log.accept(display+" install failed: "+failure.getMessage()+"; will retry.");
            } finally {
                Files.deleteIfExists(staged.toPath());
            }
        }
    }
    private WinePrerequisites(){}
}
