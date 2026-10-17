package com.drnas.console;

import java.io.*;
import java.nio.file.*;

/** Private C:/R: operations, independent of Wine and its clipboard. */
final class AppFiles {
    static File resolve(File root,String relative) throws IOException {
        Path base=root.getCanonicalFile().toPath();
        if(Files.isSymbolicLink(root.toPath()))throw new IOException("Disk je simbolička poveznica");
        if(relative==null||relative.startsWith("/")||relative.indexOf('\\')>=0||
                relative.indexOf(0)>=0)throw new IOException("Neispravna putanja");
        Path cursor=base;
        if(!relative.isEmpty())for(String part:relative.split("/",-1)) {
            if(part.isEmpty()||part.equals(".")||part.equals(".."))
                throw new IOException("Neispravna putanja");
            cursor=cursor.resolve(part);
            if(Files.isSymbolicLink(cursor))throw new IOException("Simboličke poveznice nisu podržane");
        }
        if(!cursor.normalize().startsWith(base))throw new IOException("Putanja izvan diska");
        return cursor.toFile();
    }
    static String child(String parent,String name) throws IOException {
        if(name==null||name.trim().isEmpty()||name.equals(".")||name.equals("..")||
                name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf(0)>=0||
                name.indexOf(':')>=0||name.length()>240)throw new IOException("Neispravan naziv");
        return parent.isEmpty()?name:parent+"/"+name;
    }
    static long copy(File sourceRoot,String sourcePath,File targetRoot,String targetPath) throws IOException {
        File source=resolve(sourceRoot,sourcePath),target=resolve(targetRoot,targetPath);
        if(!source.exists())throw new IOException("Izvor više ne postoji");
        if(sourcePath.isEmpty())throw new IOException("Odaberi datoteku ili podmapu, ne cijeli disk");
        if(target.toPath().startsWith(source.toPath()))
            throw new IOException("Nije moguće kopirati mapu u samu sebe");
        if(target.exists())throw new IOException("Odredište već postoji. Odaberi drugu mapu ili naziv.");
        return copyTree(sourceRoot,source,targetRoot,target,0);
    }
    private static long copyTree(File sr,File source,File tr,File target,int depth) throws IOException {
        if(depth>64)throw new IOException("Previše ugniježđenih mapa");
        source=resolve(sr,sr.getCanonicalFile().toPath().relativize(source.toPath()).toString().replace(File.separatorChar,'/'));
        target=resolve(tr,tr.getCanonicalFile().toPath().relativize(target.toPath()).toString().replace(File.separatorChar,'/'));
        if(source.isDirectory()) {
            File[] children=source.listFiles();
            if(children==null)throw new IOException("Mapa se ne može pročitati: "+source.getName());
            if(!target.mkdir())throw new IOException("Ne mogu stvoriti mapu: "+target.getName());
            long bytes=0;
            for(File f:children)bytes+=copyTree(sr,f,tr,new File(target,f.getName()),depth+1);
            return bytes;
        }
        if(!source.isFile())throw new IOException("Datoteka nije podržana: "+source.getName());
        final File destinationRoot=tr;
        return DriveFileCopy.copy(new FileInputStream(source),target,source.length(),
                ()->Math.max(0,destinationRoot.getUsableSpace()-256L*1024*1024));
    }
    private AppFiles(){}
}
