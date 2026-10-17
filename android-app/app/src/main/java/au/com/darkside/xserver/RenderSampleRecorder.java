package au.com.darkside.xserver;

import java.util.Arrays;
import java.util.Locale;

/**
 * Bounded five-second render snapshot. Records elapsed times only in RAM;
 * writes NO per-frame logs and allocates no objects on the input hot path.
 * Actual guest engine FPS and direct Vulkan Present timing are not observable
 * through this X11/HWUI layer and must not be misreported as measurements.
 */
public final class RenderSampleRecorder {
    private static final int CAP=8192;
    private final long[] read=new long[CAP],decode=new long[CAP],upload=new long[CAP];
    private final long[] displayIntervals=new long[CAP];
    private int uploads,displays;
    private long lastDrawAt,startAt,finishAt,totalBytes;
    private int srcW,srcH,targetW,targetH,drawableId,geometryChanges;
    private boolean recording;

    public synchronized void start(long now) {
        recording=true;
        uploads=0;displays=0;lastDrawAt=0;totalBytes=0;
        startAt=now;finishAt=0;
        srcW=srcH=targetW=targetH=drawableId=geometryChanges=0;
    }
    public synchronized boolean isRecording(){return recording;}
    public synchronized void put(long readNs,long decodeNs,long uploadNs,int payload,
                                 int id,int w,int h,int destW,int destH) {
        if(!recording)return;
        totalBytes+=Math.max(0,payload);
        if(drawableId!=id||srcW!=w||srcH!=h||targetW!=destW||targetH!=destH)
            geometryChanges++;
        drawableId=id;srcW=w;srcH=h;targetW=destW;targetH=destH;
        if(uploads<CAP) {
            read[uploads]=Math.max(0,readNs);
            decode[uploads]=Math.max(0,decodeNs);
            upload[uploads]=Math.max(0,uploadNs);
            uploads++;
        }
    }
    public synchronized void display(long now) {
        if(!recording)return;
        if(lastDrawAt>0 && displays<CAP)displayIntervals[displays++]=Math.max(0,now-lastDrawAt);
        lastDrawAt=now;
    }
    public synchronized String finish(long now) {
        if(!recording)return "Nema aktivnog uzorkovanja.";
        recording=false;
        finishAt=now;
        double duration=Math.max(.001,(finishAt-startAt)/1.0e9);
        StringBuilder r=new StringBuilder(800);
        r.append("EmuDrnas  /  Render sample\n");
        r.append("Duration: ").append(String.format(Locale.ROOT,"%.2f",duration)).append(" s\n");
        r.append("Source: X11 PutImage -> Android Bitmap -> HWUI\n");
        r.append("Engine FPS / DXVK Present timestamps: NOT OBSERVED by this sampler\n");
        r.append("PutImage updates: ").append(uploads).append(" (max retained ").append(CAP).append(")\n");
        r.append("Displayed frame intervals sampled: ").append(displays).append("\n");
        r.append("Android display FPS: ").append(String.format(Locale.ROOT,"%.1f",displays/duration)).append("\n");
        r.append("PutImage transport volume: ").append(String.format(Locale.ROOT,"%.1f MiB",totalBytes/1048576.0)).append("\n");
        r.append("PutImage throughput: ").append(String.format(Locale.ROOT,"%.1f MiB/s",totalBytes/1048576.0/duration)).append("\n");
        r.append("Last drawable: ").append(drawableId).append(" source ").append(srcW).append('x').append(srcH);
        r.append(" target ").append(targetW).append('x').append(targetH).append("\n");
        r.append("Distinct geometry transitions: ").append(geometryChanges).append("\n");
        appendStats(r,"X11 bytes read + wait",read,uploads,true);
        appendStats(r,"BGRX decode",decode,uploads,true);
        appendStats(r,"Bitmap/Canvas upload",upload,uploads,true);
        appendStats(r,"Android display interval",displayIntervals,displays,true);
        r.append("All statistics exclude GPU Present timing and guest frame-generation latency.\n");
        return r.toString();
    }
    private static void appendStats(StringBuilder b,String title,long[] values,int n,boolean milliseconds) {
        b.append(title).append(" (ms) ");
        if(n==0){b.append("no samples\n");return;}
        long[] sorted=Arrays.copyOf(values,n);
        Arrays.sort(sorted);
        long total=0L;
        for(int i=0;i<n;i++)total+=values[i];
        b.append("avg=").append(ms(total/(double)n));
        b.append(" min=").append(ms(sorted[0]));
        b.append(" p50=").append(ms(at(sorted,50)));
        b.append(" p95=").append(ms(at(sorted,95)));
        b.append(" p99=").append(ms(at(sorted,99)));
        b.append(" max=").append(ms(sorted[n-1])).append('\n');
    }
    private static long at(long[] sorted,int percentile){
        return sorted[Math.max(0,Math.min(sorted.length-1,
                (int)Math.ceil(percentile*.01*sorted.length)-1))];
    }
    private static String ms(double nanos){return String.format(Locale.ROOT,"%.3f",nanos/1.0e6);}
}
