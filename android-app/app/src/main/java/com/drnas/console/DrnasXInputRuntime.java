package com.drnas.console;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Bundles *source-built* Windows PE xinput DLLs for Wine's guest architecture.
 * Uses native Wine DLL override for XInput ABI, not keyboard emulation or uinput.
 * Never writes outside this app-private verified Wine prefix.
 */
final class DrnasXInputRuntime {
    static File stateFile(File prefix) {
        return new File(prefix,"drive_c/DRNAS/Input/xinput-state.bin");
    }
    static void install(Context context,File prefix,Consumer<String> log) throws Exception {
        Path root=prefix.getCanonicalFile().toPath();
        Path windows=root.resolve("drive_c/windows");
        if(!Files.isDirectory(windows,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("XInput Wine prefix not prepared");
        safeDir(root,"drive_c/DRNAS/Input");
        safeDir(root,"drive_c/DRNAS/Graphics");
        safeDir(root,"drive_c/windows/system32");
        safeDir(root,"drive_c/windows/syswow64");
        int installed=0,existing=0;
        for(String arch:new String[]{"x64","x86"}) {
            boolean x86=arch.equals("x86");
            Path folder=root.resolve("drive_c/windows/").resolve(x86?"syswow64":"system32");
            for(String dll:new String[]{"xinput1_3.dll","xinput1_4.dll","xinput9_1_0.dll"}) {
                String asset="xinput/"+arch+"/"+dll;
                byte[] data=read(context,asset,2*1024*1024);
                DxvkRuntime.validatePe(data,x86);
                byte[] expected=hexDigest(read(context,asset+".sha256",128));
                byte[] actual=MessageDigest.getInstance("SHA-256").digest(data);
                if(!Arrays.equals(expected,actual))throw new IOException("XInput asset hash mismatch: "+asset);
                Path destination=folder.resolve(dll);
                if(Files.isSymbolicLink(destination)) {
                    // Wine can have symlinked built-ins. Replace the link itself,
                    // never follow it into the staged Proton package.
                    Files.delete(destination);
                }
                if(Files.isRegularFile(destination,LinkOption.NOFOLLOW_LINKS) &&
                        Arrays.equals(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(destination)),actual)) {
                    existing++;continue;
                }
                Path temp=Files.createTempFile(folder,".drnas-xinput-",".tmp");
                try {
                    Files.write(temp,data);
                    Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);
                }finally{Files.deleteIfExists(temp);}
                installed++;
            }
        }
        // This exe only calls LoadLibrary/GetProcAddress and prints a state.
        byte[] probe=read(context,"xinput/XInputProbe.exe",2*1024*1024);
        DxvkRuntime.validatePe(probe,true);
        Path probePath=root.resolve("drive_c/DRNAS/Graphics/XInputProbe.exe");
        if(Files.isSymbolicLink(probePath))throw new IOException("Unsafe XInput probe target");
        if(!Files.isRegularFile(probePath,LinkOption.NOFOLLOW_LINKS) ||
           !Arrays.equals(Files.readAllBytes(probePath),probe))
            Files.write(probePath,probe);
        log.accept("DRNAS XInput DLL installed="+installed+" verified="+existing+
                " | x86/x64 xinput1_3, xinput1_4, xinput9_1_0; controller probe C:\\DRNAS\\Graphics\\XInputProbe.exe");
    }
    private static byte[] hexDigest(byte[] bytes) throws IOException {
        String s=new String(bytes,StandardCharsets.US_ASCII).trim();
        if(!s.matches("[0-9a-f]{64}"))throw new IOException("Invalid XInput SHA256 manifest");
        byte[] b=new byte[32];
        for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return b;
    }
    private static byte[] read(Context context,String asset,int limit) throws IOException {
        try(InputStream in=context.getAssets().open(asset);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1) {
                if(out.size()+n>limit)throw new IOException("XInput asset exceeds limit");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }
    private static Path safeDir(Path root,String path) throws IOException {
        Path current=root;
        for(Path part:Paths.get(path)) {
            current=current.resolve(part);
            if(Files.isSymbolicLink(current))throw new IOException("Unsafe XInput directory");
            if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(current);
            if(!Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS))throw new IOException("XInput folder not a directory");
        }
        return current;
    }
    private DrnasXInputRuntime(){}
}
