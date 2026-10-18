package au.com.darkside.xserver;

/** UI-thread-only rate of actual desktop onDraw calls, not GPU/engine FPS. */
final class X11DisplayRate {
    private long start;
    private int draws;
    private float rate;
    void reset(long now){start=now;draws=0;rate=0;}
    void draw(){draws++;}
    float sample(long now) {
        long elapsed=now-start;
        if(elapsed>=500_000_000L) {
            rate=(float)(draws*1_000_000_000.0/elapsed);
            draws=0;start=now;
        }
        return rate;
    }
    float current(){return rate;}
}
