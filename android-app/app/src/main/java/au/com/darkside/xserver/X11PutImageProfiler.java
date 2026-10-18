package au.com.darkside.xserver;

import java.util.Locale;

/** Optional X11 PutImage profiling with fixed histogram buckets.
 * No threads, allocations, logging or clock reads when disabled.
 * Read is framed payload access (transport received before the server lock);
 * upload timing excludes later Android HWUI.
 */
final class X11PutImageProfiler {
    private static final int[] LIMITS_US={
        50,100,150,250,400,600,900,1300,2000,3000,4500,6500,10000,16000,26000,50000
    };
    private final int[] read=new int[LIMITS_US.length+1];
    private final int[] decode=new int[LIMITS_US.length+1];
    private final int[] upload=new int[LIMITS_US.length+1];
    private int frames;
    private long firstTime,lastTime,totalBytes;

    static int bucket(long nanos) {
        long micros=Math.max(0L,nanos/1000L);
        for(int i=0;i<LIMITS_US.length;i++)if(micros<=LIMITS_US[i])return i;
        return LIMITS_US.length;
    }
    static String approximatePercentile(int[] counts,int size,int percentile) {
        int wanted=(size*percentile+99)/100;
        int cumulative=0;
        for(int i=0;i<counts.length;i++) {
            cumulative+=counts[i];
            if(cumulative>=wanted) {
                return i>=LIMITS_US.length?">50":String.format(Locale.ROOT,"%.2f",LIMITS_US[i]/1000.0);
            }
        }
        return "--";
    }
    /** Emits one aggregate line every 256 full-frame or tiled uploads. */
    String record(long readNanos,long decodeNanos,long uploadNanos,int bytes,long timeNowNanos) {
        if(frames==0)firstTime=timeNowNanos;
        lastTime=timeNowNanos;
        totalBytes+=Math.max(0,bytes);
        read[bucket(readNanos)]++;
        decode[bucket(decodeNanos)]++;
        upload[bucket(uploadNanos)]++;
        ++frames;
        if(frames<256)return null;
        double seconds=Math.max(0.001d,(lastTime-firstTime)/1e9);
        String report=String.format(Locale.ROOT,
                "X11_PUTIMAGE_PROFILE count=%d totalMiB=%.1f rateMiBps=%.1f"+
                " read_ms(p50/p95)=%s/%s decode_ms(p50/p95)=%s/%s upload_ms(p50/p95)=%s/%s"+
                " (read is buffered payload access; upload excludes Android HWUI draw)",
                frames,totalBytes/1048576.0,totalBytes/1048576.0/seconds,
                approximatePercentile(read,frames,50),approximatePercentile(read,frames,95),
                approximatePercentile(decode,frames,50),approximatePercentile(decode,frames,95),
                approximatePercentile(upload,frames,50),approximatePercentile(upload,frames,95));
        frames=0;totalBytes=0;firstTime=lastTime=0;
        java.util.Arrays.fill(read,0);
        java.util.Arrays.fill(decode,0);
        java.util.Arrays.fill(upload,0);
        return report;
    }
}

