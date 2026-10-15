package com.drnas.console;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

/** Snapshot the live GUI trace, latest Wineboot trace and bounded DXVK logs. */
final class DiagnosticLogBundle {
    static void write(File files,RawWineTrace raw,OutputStream out) throws IOException {
        title(out,"LIVE WINE GUI / VULKAN");
        raw.copyTo(out);
        GameLogSnapshot.write(files,out);
        copy(new File(files,"logs/"+RawWineTrace.LATEST),out,8L*1024*1024);
        File[] dxvk=new File(files,"containers/default/gui-tmp").listFiles(
                (dir,name)->name.endsWith(".log"));
        if(dxvk!=null) {
            Arrays.sort(dxvk,Comparator.comparing(File::getName));
            for(int i=0;i<Math.min(8,dxvk.length);i++)copy(dxvk[i],out,1024L*1024);
        }
    }
    private static void title(OutputStream out,String name) throws IOException {
        out.write(("\n\n===== "+name+" =====\n").getBytes(StandardCharsets.UTF_8));
    }
    private static void copy(File file,OutputStream out,long limit) throws IOException {
        if(!file.isFile()||Files.isSymbolicLink(file.toPath()))return;
        title(out,file.getName());
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536];long total=0;int n;
            while(total<limit&&(n=in.read(buffer,0,(int)Math.min(buffer.length,limit-total)))!=-1){
                out.write(buffer,0,n);total+=n;
            }
            if(in.read()!=-1)out.write("\n[Additional log bytes omitted by export limit]\n".getBytes(StandardCharsets.UTF_8));
        } catch(IOException e) {
            out.write(("\n[Could not snapshot "+file.getName()+": "+e.getMessage()+"]\n").getBytes(StandardCharsets.UTF_8));
        }
    }
    private DiagnosticLogBundle(){}
}
