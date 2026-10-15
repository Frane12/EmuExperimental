package com.drnas.console;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Bounded, read-only snapshots of known game diagnostic files. */
final class GameLogSnapshot {
    static void write(File files,OutputStream out) throws IOException {
        List<File> logs=new ArrayList<>();int[] budget={20000};
        scan(new File(files,"windows-drives/shared"),0,budget,logs);
        scan(new File(files,"containers/default/wineboot-probe/template/.wine/drive_c/users/xuser"),0,budget,logs);
        logs.sort(Comparator.comparingLong(File::lastModified).reversed());
        if(budget[0]<=0)out.write("\n[Game log scan reached 20000 entries]\n".getBytes(StandardCharsets.UTF_8));
        for(int i=0;i<Math.min(logs.size(),8);i++) {
            File log=logs.get(i);
            String relative=files.toPath().relativize(log.toPath()).toString();
            out.write(("\n\n===== GAME LOG: "+relative+" (modified="+log.lastModified()+") =====\n").getBytes(StandardCharsets.UTF_8));
            try(RandomAccessFile in=new RandomAccessFile(log,"r")) {
                long length=in.length(),start=Math.max(0,length-2L*1024*1024);
                if(start>0)out.write("[Earlier bytes omitted; retaining crash tail]\n".getBytes(StandardCharsets.UTF_8));
                in.seek(start);byte[] buffer=new byte[65536];long left=length-start;
                while(left>0) {int n=in.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)break;out.write(buffer,0,n);left-=n;}
            } catch(IOException e) {
                out.write(("[Snapshot unavailable: "+e.getMessage()+"]\n").getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    private static void scan(File file,int depth,int[] budget,List<File> logs) {
        if(depth>24 || budget[0]--<=0 || Files.isSymbolicLink(file.toPath()))return;
        if(file.isDirectory()) {
            File[] children=file.listFiles();
            if(children!=null)for(File child:children) {if(budget[0]<=0)break;scan(child,depth+1,budget,logs);}
        } else if(file.isFile()) {
            String name=file.getName().toLowerCase(Locale.ROOT);
            if(name.equals("game.log") || name.equals("gamecrash.log") || name.equals("error.log") || name.equals("crash.log"))logs.add(file);
        }
    }
    private GameLogSnapshot(){}
}
