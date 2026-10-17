package com.drnas.console;

import android.os.Process;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/** Low-frequency HUD metrics; no sampling on the render/upload thread.
 * System CPU is from /proc/stat when readable. Android 16 may deny access.
 * Only show true GPU busy from KGSL, otherwise "--" (never fake load).
 */
final class HudMetrics {
    private long prevCpuTotal=-1,prevCpuIdle;
    private long prevGpuBusy=-1,prevGpuTotal;
    private long prevAppMs=-1,prevWallMs=-1;

    static long[] parseCpuStat(String text) {
        String[] fields=text.trim().split("\\s+");
        if(fields.length<5||!"cpu".equals(fields[0]))return null;
        long total=0,idle=0;
        try {
            for(int i=1;i<fields.length;i++) {
                long value=Long.parseLong(fields[i]);
                if(value<0)return null;
                total+=value;
                if(i==4||i==5)idle+=value;
            }
        }catch(NumberFormatException e){return null;}
        return new long[]{total,idle};
    }
    static String number(long current,long prior,long totalCurrent,long totalPrior) {
        long denominator=totalCurrent-totalPrior;
        long numerator=current-prior;
        if(denominator<=0||numerator<0||numerator>denominator)return "--";
        return Long.toString(Math.max(0,Math.min(100,Math.round(100.0*numerator/denominator))))+"%";
    }
    private static String firstLine(String path) {
        try(BufferedReader reader=new BufferedReader(new FileReader(path))){
            return reader.readLine();
        }catch(IOException|SecurityException ignored){return null;}
    }
    private String readCpu(){
        String line=firstLine("/proc/stat");
        if(line!=null) {
            long[] now=parseCpuStat(line);
            if(now!=null) {
                String result=prevCpuTotal<0?"--":number(
                        now[0]-now[1],prevCpuTotal-prevCpuIdle,now[0],prevCpuTotal);
                prevCpuTotal=now[0];prevCpuIdle=now[1];
                return result;
            }
        }
        // Fallback is explicitly labeled APP (not system CPU) so user
        // isn't tricked into thinking it includes FEX/Wine child processes.
        long wall=SystemClock.elapsedRealtime(),process=Process.getElapsedCpuTime();
        String percent="--";
        if(prevWallMs>=0 && wall>prevWallMs){
            double used=100.0*(process-prevAppMs)/(wall-prevWallMs);
            percent=String.format(Locale.ROOT,"%.0f%%",Math.max(0,
                    Math.min(100,used/Math.max(1,Runtime.getRuntime().availableProcessors()))));
        }
        prevAppMs=process;prevWallMs=wall;
        return "APP "+percent;
    }
    private String readGpu(){
        String direct=firstLine("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
        if(direct!=null){
            try{
                String first=direct.trim().split("\\s+")[0];
                float percent=Float.parseFloat(first);
                if(Float.isFinite(percent)&&percent>=0&&percent<=100)
                    return String.format(Locale.ROOT,"%.0f%%",percent);
            }catch(NumberFormatException ignored){}
        }
        String counters=firstLine("/sys/class/kgsl/kgsl-3d0/gpubusy");
        if(counters==null)return "--";
        String[] parts=counters.trim().split("\\s+");
        if(parts.length<2)return "--";
        try {
            long busy=Long.parseLong(parts[0]),total=Long.parseLong(parts[1]);
            String out="--";
            if(prevGpuTotal>=0 && total>prevGpuTotal){
                out=number(busy,prevGpuBusy,total,prevGpuTotal);
            }
            prevGpuBusy=busy;prevGpuTotal=total;
            return out;
        }catch(NumberFormatException error){return "--";}
    }
    String cpu(){return readCpu();}
    String gpu(){return readGpu();}
}
