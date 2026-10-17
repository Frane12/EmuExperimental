package com.drnas.console;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Date;

/** Preserve host failures separately from Wine logs and across app restarts. */
public final class AndroidCrashDiagnostics {
    private static final String FATAL="last-android-crash.txt";
    private static final String RENDER="last-renderer-error.txt";
    private static final int LIMIT=256*1024;

    static void install(Context context) {
        File logs=new File(context.getFilesDir(),"logs");
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try {record(logs,FATAL,thread,error);} catch(Throwable ignored){}
            try {AutomaticCrashLog.fatal(context);}catch(Throwable ignored){}
            // Keep Android's own crash handling and process-exit accounting.
            if(previous!=null)previous.uncaughtException(thread,error);
            else {android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
    }

    public static void recordRenderer(Context context,Throwable error) {
        try {record(new File(context.getFilesDir(),"logs"),RENDER,Thread.currentThread(),error);}
        catch(Exception ignored){}
        AutomaticCrashLog.request(context,"Android renderer exception");
    }

    public static void recordPointer(Context context,Throwable error) {
        try {record(new File(context.getFilesDir(),"logs"),"last-pointer-error.txt",Thread.currentThread(),error);}
        catch(Exception ignored){}
        AutomaticCrashLog.request(context,"X11 pointer exception");
    }

    static synchronized void record(File logs,String name,Thread thread,Throwable error) throws IOException {
        if(!logs.isDirectory()&&!logs.mkdirs())throw new IOException("Crash directory unavailable");
        StringWriter trace=new StringWriter();
        PrintWriter writer=new PrintWriter(trace);
        writer.println("DRNAS Android host failure: "+new Date());
        writer.println("Thread: "+thread.getName());error.printStackTrace(writer);writer.flush();
        byte[] bytes=trace.toString().getBytes(StandardCharsets.UTF_8);
        Path temp=Files.createTempFile(logs.toPath(),".crash-",".tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(temp.toFile())) {
                out.write(bytes,0,Math.min(bytes.length,LIMIT));out.getFD().sync();
            }
            Files.move(temp,new File(logs,name).toPath(),StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp);}
    }

    static void appendSaved(File files,OutputStream out) throws IOException {
        copy(new File(files,"logs/"+FATAL),out,LIMIT);
        copy(new File(files,"logs/"+RENDER),out,LIMIT);
        copy(new File(files,"logs/last-pointer-error.txt"),out,LIMIT);
        copy(new File(files,"logs/last-process-exits.txt"),out,LIMIT);
    }

    static void appendExitHistory(Context context,OutputStream out) throws IOException {
        StringBuilder text=new StringBuilder("\n===== ANDROID PROCESS EXIT HISTORY =====\n");
        if(Build.VERSION.SDK_INT>=30) {
            try {
                ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(context.getPackageName(),0,8)) {
                    text.append(new Date(exit.getTimestamp())).append(" pid=").append(exit.getPid())
                            .append(" process=").append(exit.getProcessName()).append(" reason=").append(exit.getReason())
                            .append(" status=").append(exit.getStatus()).append(" pssKiB=").append(exit.getPss())
                            .append(" rssKiB=").append(exit.getRss()).append(" description=").append(exit.getDescription()).append('\n');
                }
                text.append("Reason codes: 3=low memory, 4=Java crash, 5=native crash, 6=ANR.\n");
            } catch(Exception error){text.append("Exit history unavailable: ").append(error).append('\n');}
        } else text.append("Requires Android 11+.\n");
        byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);
        out.write(bytes);
        // Cache the last successful query so ordinary GUI log exports include it.
        File logs=new File(context.getFilesDir(),"logs");
        try {logs.mkdirs();Files.write(new File(logs,"last-process-exits.txt").toPath(),bytes);}
        catch(IOException ignored){}
    }

    static void appendInitializationLogs(File files,OutputStream out) throws IOException {
        File logs=new File(files,"logs");
        File[] console=logs.listFiles((dir,name)->name.startsWith("DRNAS-console-")&&name.endsWith(".txt"));
        if(console!=null){
            java.util.Arrays.sort(console,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
            for(int i=0;i<Math.min(2,console.length);i++)copy(console[i],out,2*1024*1024);
        }
        copy(new File(logs,RawWineTrace.LATEST),out,24*1024*1024);
    }

    static void export(Context context,OutputStream out) throws IOException {
        out.write(("DRNAS host crash diagnostics\nPackage: "+context.getPackageName()+
                "\nAndroid API: "+Build.VERSION.SDK_INT+"\n").getBytes(StandardCharsets.UTF_8));
        appendExitHistory(context,out);
        appendSaved(context.getFilesDir(),out);
        appendInitializationLogs(context.getFilesDir(),out);
        copy(new File(context.getFilesDir(),"containers/default/wineboot-probe/template/.wine/drive_c/DRNAS/Tools/DesktopStyle.log"),out,256*1024);
        copy(new File(context.getFilesDir(),"logs/latest-wine-gui-raw.txt"),out,8*1024*1024);
    }

    private static void copy(File file,OutputStream out,int limit) throws IOException {
        if(!file.isFile()||Files.isSymbolicLink(file.toPath()))return;
        out.write(("\n===== "+file.getName()+" =====\n").getBytes(StandardCharsets.UTF_8));
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[8192];int remaining=limit,n;
            while(remaining>0 && (n=in.read(buffer,0,Math.min(remaining,buffer.length)))!=-1) {
                out.write(buffer,0,n);remaining-=n;
            }
        }
    }
    private AndroidCrashDiagnostics(){}
}

