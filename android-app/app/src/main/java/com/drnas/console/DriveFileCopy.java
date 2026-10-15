package com.drnas.console;

import java.io.*;
import java.nio.file.*;
import java.util.function.LongSupplier;

/** Commit only complete imports; never replace a file changed by Wine. */
final class DriveFileCopy {
    static long copy(InputStream source,File target,long expected,LongSupplier available) throws IOException {
        Path part=null;
        try(InputStream in=source) {
            if(in==null)throw new IOException("Provider refused "+target.getName());
            if(Files.exists(target.toPath(),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Destination already exists: "+target.getName());
            part=Files.createTempFile(target.getParentFile().toPath(),".drnas-import-",".part");
            long bytes=0,checked=0;
            try(FileOutputStream out=new FileOutputStream(part.toFile())) {
                byte[] buffer=new byte[128*1024];int n;
                while((n=in.read(buffer))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("R: import interrupted");
                    if(n==0)continue;
                    if(checked==0 || checked>=8L*1024*1024) {
                        if(available.getAsLong()<Math.max(n,8L*1024*1024))
                            throw new IOException("R: insufficient free storage; import stopped safely");
                        checked=0;
                    }
                    out.write(buffer,0,n);bytes+=n;checked+=n;
                }
                if(expected>=0 && bytes!=expected)throw new IOException("Incomplete provider read: "+target.getName()+" expected="+expected+" got="+bytes);
                out.getFD().sync();
            }
            Files.move(part,target.toPath());
            return bytes;
        } finally {if(part!=null)Files.deleteIfExists(part);}
    }
    private DriveFileCopy(){}
}
