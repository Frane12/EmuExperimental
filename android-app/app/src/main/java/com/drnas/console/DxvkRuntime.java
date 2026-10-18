package com.drnas.console;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.Consumer;

/** Apply the already WCP-verified DXVK payload to the prefix actually launched. */
final class DxvkRuntime {
    static File windows(File prefix) throws IOException {
        for(String path:new String[]{"drive_c/windows","prefix/drive_c/windows","wineprefix/drive_c/windows",".wine/drive_c/windows"}) {
            File dir=new File(prefix,path);
            if(dir.isDirectory())return dir;
        }
        throw new IOException("Prepared DXVK prefix has no Windows directory");
    }
    static void install(File preparedPrefix,File activePrefix,Consumer<String> log) throws Exception {
        File source=windows(preparedPrefix).getCanonicalFile();
        if(!source.toPath().startsWith(preparedPrefix.getCanonicalFile().toPath()))
            throw new IOException("DXVK source escaped prepared prefix");
        File target=new File(activePrefix,"drive_c/windows").getCanonicalFile();
        if(!target.toPath().startsWith(activePrefix.getCanonicalFile().toPath()))
            throw new IOException("DXVK destination escaped active prefix");
        int copied=0,verified=0;
        String[] dlls={"d3d9.dll","dxgi.dll","d3d11.dll","d3d10core.dll"};
        // Validate the entire payload before modifying the active prefix.
        for(String arch:new String[]{"system32","syswow64"})for(String dll:dlls) {
            File from=new File(source,arch+"/"+dll);
            if(!Files.isRegularFile(from.toPath(),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Prepared DXVK DLL missing: "+arch+"/"+dll);
            validatePe(Files.readAllBytes(from.toPath()),arch.equals("syswow64"));
        }
        for(String arch:new String[]{"system32","syswow64"}) {
            File folder=new File(target,arch);Files.createDirectories(folder.toPath());
            if(!folder.getCanonicalFile().toPath().startsWith(activePrefix.getCanonicalFile().toPath()))
                throw new IOException("DXVK system folder escaped prefix");
            for(String dll:dlls) {
                File from=new File(source,arch+"/"+dll),to=new File(folder,dll);
                byte[] data=Files.readAllBytes(from.toPath());
                if(Files.isRegularFile(to.toPath(),LinkOption.NOFOLLOW_LINKS)&&to.length()==data.length&&
                        Arrays.equals(hash(data),hashFile(to.toPath()))){verified++;continue;}
                // Move the symlink itself, never write through a Wine builtin link.
                Path backup=new File(folder,dll+".drnas-before-dxvk").toPath();
                if(Files.exists(to.toPath(),LinkOption.NOFOLLOW_LINKS)&&!Files.exists(backup,LinkOption.NOFOLLOW_LINKS))
                    Files.move(to.toPath(),backup);
                Path part=Files.createTempFile(folder.toPath(),".drnas-dxvk-",".tmp");
                try {
                    Files.write(part,data);
                    Files.move(part,to.toPath(),StandardCopyOption.REPLACE_EXISTING);
                } finally {Files.deleteIfExists(part);}
                copied++;
            }
        }
        log.accept("DXVK active Wine prefix: copied="+copied+", verified unchanged="+verified+" (system32 + syswow64).");
    }
    private static byte[] hashFile(Path path) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(Files.newInputStream(path),65536)) {
            byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
        }
        return digest.digest();
    }
    private static byte[] hash(byte[] data) throws Exception {return MessageDigest.getInstance("SHA-256").digest(data);}
    static void validatePe(byte[] data,boolean x86) throws IOException {
        if(data.length<256||data[0]!='M'||data[1]!='Z')throw new IOException("DXVK DLL is not PE");
        long offset=Integer.toUnsignedLong((data[60]&255)|((data[61]&255)<<8)|((data[62]&255)<<16)|((data[63]&255)<<24));
        if(offset>data.length-24)throw new IOException("Invalid DXVK PE header");
        int p=(int)offset;
        if(data[p]!='P'||data[p+1]!='E'||data[p+2]!=0||data[p+3]!=0)throw new IOException("Invalid DXVK PE signature");
        int machine=(data[p+4]&255)|((data[p+5]&255)<<8);
        if(x86?machine!=0x014c:(machine!=0x8664&&machine!=0xa641&&machine!=0xaa64))
            throw new IOException("DXVK DLL architecture mismatch");
    }
    private DxvkRuntime(){}
}

