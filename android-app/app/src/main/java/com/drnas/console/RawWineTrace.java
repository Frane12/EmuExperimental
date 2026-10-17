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
    private final File file;
    private long bytes;
    private boolean truncated;
    private boolean closed;
    private boolean failed;
    private String issue;
    private long lastFlushNanos;
    // OFF by default: game frames must not wait for disk logging.
    private volatile boolean verbose;

    void setVerbose(boolean enabled) { verbose = enabled; }
    boolean isVerbose() { return verbose; }

    RawWineTrace(File directory) throws IOException {
        this(directory,LATEST);
    }
    RawWineTrace(File directory,String name) throws IOException {
        if(!name.matches("[a-z0-9-]+\\.txt"))throw new IOException("Invalid trace name");
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IOException("Could not create private RAW logs directory");
        File f=new File(directory,name);
        file=f;
        if(java.nio.file.Files.isSymbolicLink(f.toPath()))
            throw new IOException("Refuse symlinked RAW log target");
        out=new BufferedOutputStream(new FileOutputStream(f,false),65536);
        writeEssential("DRNAS","RAW Wine trace started "+new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()));
    }

    void write(String source,String rawLine) {
        if(!verbose)return;
        writeEssential(source,rawLine);
    }

    // Small fatal/error-only evidence remains available when verbose is OFF.
    synchronized void writeEssential(String source,String rawLine) {
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
            long now=System.nanoTime();
            if(now-lastFlushNanos>=1000000000L){out.flush();lastFlushNanos=now;}
        } catch(IOException e) {
            failed=true;issue=e.getMessage();
        }
    }

    synchronized long size(){return bytes;}
    synchronized void checkpoint() throws IOException {if(!closed)out.flush();}
    synchronized boolean wasTruncated(){return truncated;}
    synchronized String issue(){return issue;}
    synchronized void copyTo(OutputStream target) throws IOException {
        if(!closed)out.flush();
        try(java.io.FileInputStream input=new java.io.FileInputStream(file)) {
            byte[] buffer=new byte[65536];int n;
            while((n=input.read(buffer))!=-1)target.write(buffer,0,n);
        }
    }

    @Override public synchronized void close() throws IOException {
        if(closed)return;
        closed=true;
        out.flush();
        out.close();
        if(failed) throw new IOException("RAW Wine log write failure: "+issue);
    }
}

