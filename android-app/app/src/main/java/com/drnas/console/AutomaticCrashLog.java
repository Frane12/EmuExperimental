package com.drnas.console;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/** Persist first, then publish to the same Downloads/DRNAS folder as manual exports. */
final class AutomaticCrashLog {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"drnas-automatic-crash-log");t.setDaemon(true);return t;
    });
    private static final AtomicLong LAST=new AtomicLong();
    private static File logs(Context c){return new File(c.getFilesDir(),"logs");}
    private static File marker(Context c){return new File(logs(c),"desktop-session-active.txt");}

    static void install(Context context) {
        Context c=context.getApplicationContext();
        // Snapshot before an Activity can truncate the previous session's RAW trace.
        if(marker(c).isFile()) {
            try {
                capture(c,"Previous desktop ended without clean shutdown; see Android exit history.");
                Files.deleteIfExists(marker(c).toPath());
            } catch(Exception e){android.util.Log.e("DRNAS-AutoLog","Recovery snapshot failed",e);}
        }
        WORKER.execute(()->publishPending(c));
    }

    static void begin(Context c) {
        try {
            logs(c).mkdirs();
            try(FileOutputStream out=new FileOutputStream(marker(c))) {
                out.write(new java.util.Date().toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
        } catch(Exception e){android.util.Log.e("DRNAS-AutoLog","Session marker failed",e);}
    }

    static void end(Context c) {
        try {Files.deleteIfExists(marker(c).toPath());}
        catch(Exception e){android.util.Log.e("DRNAS-AutoLog","Session marker cleanup failed",e);}
    }

    static void request(Context context,String reason) {
        long now=android.os.SystemClock.elapsedRealtime(),old=LAST.get();
        if(old!=0 && now-old<10000)return;
        if(!LAST.compareAndSet(old,now))return;
        Context c=context.getApplicationContext();
        WORKER.execute(()->{
            try {capture(c,reason);}catch(Exception e){android.util.Log.e("DRNAS-AutoLog","Snapshot failed",e);}
            publishPending(c);
        });
    }

    static void fatal(Context c) {
        // Do not acquire a live RawWineTrace lock on a crashing thread.
        // A private fsynced report survives even if MediaStore cannot finish.
        try {capture(c,"Unhandled Android host exception");end(c);publishPending(c);}
        catch(Throwable e){android.util.Log.e("DRNAS-AutoLog","Fatal snapshot deferred to restart",e);}
    }

    private static void capture(Context c,String reason) throws IOException {
        File pending=new File(logs(c),"automatic-pending");
        if(!pending.isDirectory()&&!pending.mkdirs())throw new IOException("Pending log directory unavailable");
        File[] retained=pending.listFiles((d,n)->n.endsWith(".txt"));
        // Keep failed exports rather than silently deleting the only evidence.
        if(retained!=null && retained.length>=8)throw new IOException("Eight pending reports retained; export storage unavailable");
        Path temp=Files.createTempFile(pending.toPath(),"report-",".tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(temp.toFile())) {
                out.write(("DRNAS automatic failure diagnostics\nTime: "+new java.util.Date()+
                        "\nPackage: "+c.getPackageName()+"\nReason: "+reason+"\n").getBytes(StandardCharsets.UTF_8));
                AndroidCrashDiagnostics.appendExitHistory(c,out);
                DiagnosticLogBundle.write(c.getFilesDir(),null,out);
                out.getFD().sync();
            }
            Files.move(temp,temp.resolveSibling(temp.getFileName().toString().replace(".tmp",".txt")));
        } finally {Files.deleteIfExists(temp);}
    }

    private static synchronized void publishPending(Context c) {
        File[] reports=new File(logs(c),"automatic-pending").listFiles((d,n)->n.endsWith(".txt"));
        if(reports==null)return;
        java.util.Arrays.sort(reports);
        for(File report:reports) {
            try {
                String name=GuiLogExport.save(c,out->{
                    try(InputStream in=new FileInputStream(report)) {
                        byte[] buffer=new byte[65536];int n;
                        while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    }
                });
                Files.delete(report.toPath());
                android.util.Log.i("DRNAS-AutoLog","Saved Downloads/DRNAS/"+name);
            } catch(Exception e){android.util.Log.e("DRNAS-AutoLog","Export retained for next startup",e);}
        }
    }
    private AutomaticCrashLog(){}
}
