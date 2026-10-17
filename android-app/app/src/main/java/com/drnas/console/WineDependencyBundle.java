package com.drnas.console;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Offline installers imported by the owner from Dependencies.zip.
 * Deliberately does not download dependencies or execute anything from the Internet.
 * Only known redistributables are executed; .NET and all web setup files are excluded.
 * Each installer is attempted once per content fingerprint / persistent Wine prefix,
 * and failures are not recorded as successful installs.
 */
final class WineDependencyBundle {
    static final String FILENAME="Dependencies.zip";
    private static final long MAX_ARCHIVE=400L*1024*1024;
    private static final long MAX_TOTAL_EXPANDED=300L*1024*1024;
    private static final String BASE="_CommonRedist/";
    private static final String DX=BASE+"DirectX/Jun2010/";
    private static final String[] INSTALLERS={
        BASE+"vcredist_x86.exe",BASE+"vcredist_x64.exe",
        BASE+"vcredist/2013/vcredist_x86.exe",BASE+"vcredist/2013/vcredist_x64.exe",
        BASE+"vcredist_2015-2019_x86.exe",BASE+"vcredist_2015-2019_x64.exe",
        BASE+"vcredist/2022/VC_redist.x86.exe",BASE+"vcredist/2022/VC_redist.x64.exe",
        BASE+"oalinst.exe",BASE+"xnafx40_redist.msi",
        BASE+"VulkanRT-1.1.108.0-Installer.exe"
    };
    private static final String[] LABELS={
        "Visual C++ (legacy) x86","Visual C++ (legacy) x64",
        "Visual C++ 2013 x86","Visual C++ 2013 x64",
        "Visual C++ 2015–2019 x86","Visual C++ 2015–2019 x64",
        "Visual C++ 2022 x86","Visual C++ 2022 x64",
        "OpenAL","XNA Framework 4.0","Vulkan Runtime"
    };
    interface Progress { void update(int percent,String label); }

    private static File vault(Context ctx) throws IOException {
        File root=new File(ctx.getFilesDir(),"wine-prerequisites");
        if(Files.isSymbolicLink(root.toPath()) ||
           (!root.isDirectory()&&!root.mkdirs()))
            throw new IOException("Unsafe dependency vault");
        return root;
    }
    static File bundle(Context ctx) throws IOException {return new File(vault(ctx),FILENAME);}
    static boolean isSafePath(String name) {
        if(name==null||name.isEmpty()||name.startsWith("/")||
           name.contains("\\")||name.contains("..")||name.indexOf('\0')>=0)return false;
        return name.startsWith(BASE);
    }
    private static boolean isDirectXFile(String name) {
        if(!isSafePath(name)||!name.startsWith(DX))return false;
        String leaf=name.substring(DX.length());
        if(leaf.isEmpty()||leaf.contains("/"))return false;
        String low=leaf.toLowerCase(Locale.ROOT);
        return low.endsWith(".cab")||low.equals("dxsetup.exe")||
               low.equals("dsetup.dll")||low.equals("dsetup32.dll");
    }
    static boolean isSupportedInstaller(String name) {
        if(!isSafePath(name))return false;
        for(String n:INSTALLERS)if(n.equals(name))return true;
        return false;
    }
    static boolean isAllowedArchiveMember(String name) {
        return isSupportedInstaller(name)||isDirectXFile(name);
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder();
        for(byte b:bytes)out.append(String.format(Locale.US,"%02x",b&255));
        return out.toString();
    }
    private static String hash(File file) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf=new byte[65536];int n;
            while((n=in.read(buf))!=-1)if(n>0)md.update(buf,0,n);
        }
        return hex(md.digest());
    }
    private static void inspectArchive(File source) throws IOException {
        if(source.length()<500||source.length()>MAX_ARCHIVE)
            throw new IOException("Dependencies.zip outside 500 B – 400 MiB limit");
        try(ZipFile zip=new ZipFile(source)) {
            long sum=0;int found=0;boolean dx=false;
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                String name=entry.getName();
                if(!isSafePath(name)) {
                    if(!entry.isDirectory()&&!name.startsWith(BASE))
                        throw new IOException("Unrecognized path in dependency ZIP: "+name);
                    continue;
                }
                if(entry.isDirectory())continue;
                if(isAllowedArchiveMember(name)) {
                    if(entry.getSize()<0)throw new IOException("Unknown ZIP member size");
                    sum+=entry.getSize();
                    if(sum>MAX_TOTAL_EXPANDED)throw new IOException("Expanded ZIP too large");
                    if(isSupportedInstaller(name))found++;
                    if(name.equals(DX+"DXSETUP.exe"))dx=true;
                }
            }
            if(found==0&&!dx)throw new IOException("No supported offline installers found");
        }
    }
    static String importUri(Context ctx,Uri uri,Progress progress) throws Exception {
        File target=bundle(ctx);
        File temp=new File(target.getParentFile(),".dependencies-import.tmp");
        long reported=-1;
        try(android.database.Cursor cursor=ctx.getContentResolver().query(uri,
             new String[]{OpenableColumns.SIZE},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))
                reported=cursor.getLong(0);
        } catch(RuntimeException ignored){}
        if(reported>MAX_ARCHIVE)throw new IOException("Dependencies.zip exceeds 400 MiB");
        progress.update(0,"Kopiranje Dependencies.zip");
        try {
            try(InputStream input=ctx.getContentResolver().openInputStream(uri);
                OutputStream output=new BufferedOutputStream(new FileOutputStream(temp))) {
                if(input==null)throw new IOException("Cannot open selected ZIP");
                byte[] buf=new byte[65536];int n;long copied=0;int last=-1;
                while((n=input.read(buf))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new IOException("Import interrupted");
                    if(n==0)continue;
                    copied+=n;
                    if(copied>MAX_ARCHIVE)throw new IOException("Dependencies.zip exceeds 400 MiB");
                    output.write(buf,0,n);
                    int percent=reported>0 ? Math.min(95,(int)(copied*95/reported))
                                           : Math.min(95,(int)(copied/(3*1024*1024)));
                    if(percent!=last){progress.update(percent,"Kopiranje Dependencies.zip");last=percent;}
                }
            }
            progress.update(96,"Provjera paketa i izuzetaka");
            inspectArchive(temp);
            String fingerprint=hash(temp);
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            try(OutputStream out=new FileOutputStream(new File(vault(ctx),"Dependencies.sha256"))) {
                out.write(fingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            progress.update(100,"Dependencies.zip je spreman za offline instalaciju");
            return "Dependencies.zip verified SHA-256 "+fingerprint.substring(0,12)+
                   "; .NET and websetup excluded; install on next Wine desktop start.";
        } finally {Files.deleteIfExists(temp.toPath());}
    }
    private static boolean isWindowsInstaller(ZipEntry entry) throws IOException {
        if(entry==null||entry.isDirectory()||entry.getSize()<500||
           entry.getSize()>MAX_ARCHIVE)return false;
        return true;
    }
    private static String fingerprint(String zipFingerprint,ZipEntry entry) {
        return zipFingerprint+":"+entry.getSize()+":"+entry.getCrc();
    }
    private static void save(Properties state,File stateFile) throws IOException {
        File temp=new File(stateFile.getParentFile(),".dependencies-state.tmp");
        try(OutputStream out=new FileOutputStream(temp)) {
            state.store(out,"Verified offline Wine installer exit codes, per-prefix and archive content");
        }
        Files.move(temp.toPath(),stateFile.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    private static void copy(ZipFile zip,ZipEntry entry,File output) throws IOException {
        if(entry.getSize()<0||entry.getSize()>MAX_ARCHIVE)
            throw new IOException("Invalid installer entry size");
        try(InputStream in=zip.getInputStream(entry);
            OutputStream out=new BufferedOutputStream(new FileOutputStream(output))) {
            byte[] buf=new byte[65536];long total=0;int n;
            while((n=in.read(buf))!=-1) {
                if(Thread.currentThread().isInterrupted())throw new IOException("Copy interrupted");
                if(n==0)continue;
                total+=n;
                if(total>entry.getSize()||total>MAX_ARCHIVE)
                    throw new IOException("ZIP entry size exceeded");
                out.write(buf,0,n);
            }
            if(total!=entry.getSize())throw new IOException("ZIP entry truncated");
        }
    }
    private static void cleanDir(File dir) throws IOException {
        if(!dir.exists())return;
        Path root=dir.toPath();
        if(Files.isSymbolicLink(root))throw new IOException("Unsafe staging symlink");
        try(java.util.stream.Stream<Path> stream=Files.walk(root)) {
            Path[] paths=stream.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for(Path p:paths)Files.deleteIfExists(p);
        }
    }
    private static void emit(Consumer<String> log,int percent,String label) {
        log.accept("[Dependencies] "+Math.min(100,Math.max(0,percent))+"|"+label);
    }
    static void installPending(Context ctx,File prefix,File linker,File wine,
                               File cwd,Map<String,String> env,RawWineTrace raw,
                               Consumer<String> log) throws Exception {
        File archive=bundle(ctx);
        if(!archive.isFile()) {
            log.accept("No imported Dependencies.zip; use Components > Import Dependencies.zip. No network installers run.");
            return;
        }
        inspectArchive(archive);
        String bundleHash=hash(archive);
        File stateFile=new File(prefix,".drnas-dependencies-state.properties");
        if(Files.isSymbolicLink(stateFile.toPath()))throw new IOException("Unsafe dependencies state");
        Properties state=new Properties();
        if(stateFile.isFile())try(InputStream in=new FileInputStream(stateFile)){state.load(in);}
        File setup=new File(prefix,"drive_c/DRNAS-Setup/OfflineBundle");
        if(Files.isSymbolicLink(setup.toPath())||
           (!setup.isDirectory()&&!setup.mkdirs()))
            throw new IOException("Unsafe dependency staging");
        try(ZipFile zip=new ZipFile(archive)) {
            // One DXSETUP invocation consumes the complete June 2010 CAB collection.
            List<String> paths=new ArrayList<>();
            if(zip.getEntry(DX+"DXSETUP.exe")!=null)paths.add(DX+"DXSETUP.exe");
            Collections.addAll(paths,INSTALLERS);
            int total=paths.size();int finished=0;
            emit(log,0,"Priprema offline Windows dependencies");
            for(String entryName:paths) {
                if(Thread.currentThread().isInterrupted()) {
                    emit(log,(100*finished)/Math.max(1,total),"Instalacija prekinuta");
                    return;
                }
                String label=entryName.equals(DX+"DXSETUP.exe")?"DirectX June 2010":
                   LABELS[Arrays.asList(INSTALLERS).indexOf(entryName)];
                ZipEntry source=zip.getEntry(entryName);
                if(!isWindowsInstaller(source)) {
                    log.accept("[Dependencies] not available: "+label);
                    finished++;
                    emit(log,(100*finished)/total,label+" – nedostaje");
                    continue;
                }
                String key="bundle."+entryName;
                String mark=fingerprint(bundleHash,source);
                if(mark.equals(state.getProperty(key))) {
                    finished++;
                    emit(log,(100*finished)/total,label+" – već instalirano");
                    continue;
                }
                emit(log,(100*finished)/total,"Instalacija: "+label);
                File stageDir=new File(setup,"Current");
                cleanDir(stageDir);
                if(!stageDir.mkdirs())throw new IOException("Cannot create installer staging");
                int exit=-1;
                try {
                    File installer;
                    String guestPath;
                    if(entryName.equals(DX+"DXSETUP.exe")) {
                        // CAB files must remain beside DXSETUP.exe on the guest disk.
                        File dxDir=new File(stageDir,"DX");
                        if(!dxDir.mkdir())throw new IOException("Cannot create DirectX stage");
                        int dxCount=0;
                        Enumeration<? extends ZipEntry> countEntries=zip.entries();
                        while(countEntries.hasMoreElements())
                            if(isDirectXFile(countEntries.nextElement().getName()))dxCount++;
                        int dxDone=0,dxLastPercent=-1;
                        Enumeration<? extends ZipEntry> entries=zip.entries();
                        while(entries.hasMoreElements()) {
                            ZipEntry item=entries.nextElement();
                            if(!isDirectXFile(item.getName()))continue;
                            File dest=new File(dxDir,item.getName().substring(DX.length()));
                            copy(zip,item,dest);
                            dxDone++;
                            // DX cab staging can take a while; show actual milestones
                            // during unpacking rather than leaving the bar frozen at 0%.
                            int percent=(100*finished)/total + (7*dxDone/Math.max(1,dxCount));
                            if(percent!=dxLastPercent) {
                                emit(log,percent,"Raspakiranje DirectX: "+dxDone+"/"+dxCount+" datoteka");
                                dxLastPercent=percent;
                            }
                        }
                        installer=new File(dxDir,"DXSETUP.exe");
                        guestPath="C:\\DRNAS-Setup\\OfflineBundle\\Current\\DX\\DXSETUP.exe";
                    } else {
                        installer=new File(stageDir,"setup"+(entryName.endsWith(".msi")?".msi":".exe"));
                        copy(zip,source,installer);
                        guestPath="C:\\DRNAS-Setup\\OfflineBundle\\Current\\"+installer.getName();
                    }
                    if(!installer.isFile())throw new IOException("Installer failed to stage");
                    List<String> args=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
                    if(entryName.endsWith(".msi")) {
                        args.set(args.size()-1,"msiexec.exe");
                        args.add("/i");args.add(guestPath);args.add("/qn");args.add("/norestart");
                    } else {
                        args.set(args.size()-1,guestPath);
                        if(entryName.equals(DX+"DXSETUP.exe"))args.add("/silent");
                        else if(entryName.equals(BASE+"oalinst.exe") ||
                                entryName.contains("VulkanRT-"))args.add("/S");
                        else if(entryName.equals(BASE+"vcredist_x86.exe")||
                                entryName.equals(BASE+"vcredist_x64.exe"))args.add("/q");
                        else {args.add("/install");args.add("/quiet");args.add("/norestart");}
                    }
                    ProcessBuilder pb=new ProcessBuilder(args).directory(cwd).redirectErrorStream(true);
                    pb.environment().clear();pb.environment().putAll(env);
                    log.accept("Running offline dependency installer: "+label);
                    Process process=pb.start();
                    Thread reader=WineServerDiagnostics.pipe(process,"[offline "+label+"] ",log,14,raw);
                    try {
                        if(process.waitFor(entryName.equals(DX+"DXSETUP.exe")?300:180,TimeUnit.SECONDS))
                            exit=process.exitValue();
                        else {
                            process.destroyForcibly();
                            process.waitFor(3,TimeUnit.SECONDS);
                            log.accept(label+" timed out; not marked installed");
                        }
                    } finally {
                        if(process.isAlive())process.destroyForcibly();
                        reader.join(1600);
                        process.getInputStream().close();
                    }
                    if(exit==0||exit==3010||exit==1641) {
                        state.setProperty(key,mark);
                        save(state,stateFile);
                        log.accept(label+" installer exit="+exit+"; marked installed for this prefix");
                    } else log.accept(label+" exit="+exit+"; NOT marked installed");
                } catch(Exception e) {
                    log.accept(label+" installer failed: "+e.getClass().getSimpleName()+
                               " "+e.getMessage()+"; NOT marked installed");
                } finally {
                    cleanDir(stageDir);
                }
                finished++;
                emit(log,(100*finished)/total,label+(exit==0||exit==3010||exit==1641 ?
                        " – gotovo":" – nije uspjelo (vidi log)"));
            }
            emit(log,100,"Offline dependencies: provjera završena");
        }
    }
    private WineDependencyBundle(){}
}
