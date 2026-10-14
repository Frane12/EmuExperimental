package com.drnas.console;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Complete per-process app log, separate from the bounded TextView UI history.
 * The user exports it via ACTION_CREATE_DOCUMENT to their chosen location.
 * No broad storage permissions or external-storage write paths are needed.
 */
final class SessionLogStore implements AutoCloseable {
    private final File sessionFile;
    private final FileOutputStream stream;
    private boolean closed;

    SessionLogStore(File privateLogDirectory) throws IOException {
        if (!privateLogDirectory.isDirectory() && !privateLogDirectory.mkdirs())
            throw new IOException("Cannot create private log directory");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US)
                .format(new Date());
        sessionFile=File.createTempFile("DRNAS-console-"+stamp+"-", ".txt", privateLogDirectory);
        stream=new FileOutputStream(sessionFile,true);
    }

    synchronized void appendLine(String line) throws IOException {
        if (closed)throw new IOException("Session logger closed");
        stream.write(line.getBytes(StandardCharsets.UTF_8));
        // Persist each line so exporting after a crash still has the earlier log.
        stream.flush();
    }

    synchronized long copyTo(OutputStream destination) throws IOException {
        if (closed) throw new IOException("Session logger closed");
        stream.flush();
        long copied=0;
        byte[] buffer=new byte[32768];
        try(BufferedInputStream source=new BufferedInputStream(new FileInputStream(sessionFile))) {
            int count;
            while((count=source.read(buffer))!=-1) {
                destination.write(buffer,0,count);
                copied+=count;
            }
        }
        destination.flush();
        return copied;
    }

    synchronized long size() {
        return sessionFile.length();
    }

    String filename() {return sessionFile.getName();}
    File file() {return sessionFile;}

    @Override public synchronized void close() throws IOException {
        if(!closed) {
            closed=true;
            stream.close();
        }
    }
}
