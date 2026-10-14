package com.drnas.console;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * RAW Wine stdout/stderr for a SINGLE disposable Wineboot test.
 * Bounded disk use. The regular console log remains independent.
 * Entries in this file are exactly the lines received from native child pipes
 * (prefixed by stream origin); nothing is dropped by the UI's 80-line filter.
 */
final class RawWineTrace implements AutoCloseable {
    static final long MAX_BYTES=24L*1024*1024;
    static final String LATEST="latest-wineboot-raw.txt";
    private final BufferedOutputStream out;
    private long bytes;
    private boolean truncated;
    private boolean closed;
    private boolean failed;
    private String issue;

    RawWineTrace(File directory) throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IOException("Could not create private RAW logs directory");
        File f=new File(directory,LATEST);
        if(java.nio.file.Files.isSymbolicLink(f.toPath()))
            throw new IOException("Refuse symlinked RAW log target");
        out=new BufferedOutputStream(new FileOutputStream(f,false),65536);
        write("DRNAS","RAW Wine trace started "+new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()));
    }

    synchronized void write(String source,String rawLine) {
        if(closed || failed || truncated)return;
        String row="["+source+"] "+rawLine+"\n";
        byte[] utf8=row.getBytes(StandardCharsets.UTF_8);
        if(bytes+utf8.length>MAX_BYTES) {
            truncated=true;
            byte[] notice=("[DRNAS] RAW TRACE LIMIT "+MAX_BYTES+
                    " bytes reached: additional native output not saved.\n")
                    .getBytes(StandardCharsets.UTF_8);
            try {out.write(notice);bytes+=notice.length;out.flush();}
            catch(IOException e){failed=true;issue=e.getMessage();}
            return;
        }
        try {
            out.write(utf8);bytes+=utf8.length;
        } catch(IOException e) {
            failed=true;issue=e.getMessage();
        }
    }

    synchronized long size(){return bytes;}
    synchronized boolean wasTruncated(){return truncated;}
    synchronized String issue(){return issue;}

    @Override public synchronized void close() throws IOException {
        if(closed)return;
        closed=true;
        out.flush();
        out.close();
        if(failed) throw new IOException("RAW Wine log write failure: "+issue);
    }
}
