package com.drnas.console;

/**
 * Shadow-only, CPU baseline predictor for Android/X11 DISPLAY intervals.
 * This is a heuristic, NOT an ML model and NOT Blender's GPU render time.
 * No driver settings or graphics operations are ever changed here.
 */
final class NeuroFlowPredictor {
    static final class Result {
        final int count;
        final double observedMs, predictedMs, absoluteErrorMs;
        final String regime;
        Result(int count,double observedMs,double predictedMs,double absoluteErrorMs,String regime) {
            this.count=count;this.observedMs=observedMs;this.predictedMs=predictedMs;
            this.absoluteErrorMs=absoluteErrorMs;this.regime=regime;
        }
    }
    private final double[] times=new double[16];
    private int write,count;
    private double priorMean=Double.NaN,lastPrediction=Double.NaN,errorEwma=Double.NaN;

    static boolean valid(float fps,float ms) {
        return Float.isFinite(fps)&&fps>0&&fps<=1000&&Float.isFinite(ms)&&ms>0&&ms<=1000;
    }
    Result observe(float fps,float ms) {
        if(!valid(fps,ms))return null;
        if(Double.isFinite(lastPrediction)) {
            double err=Math.abs(ms-lastPrediction);
            errorEwma=Double.isNaN(errorEwma)?err:0.8*errorEwma+0.2*err;
        }
        times[write]=ms;
        write=(write+1)%times.length;
        if(count<times.length)count++;
        double sum=0,maximum=0;
        for(int i=0;i<count;i++){sum+=times[i];maximum=Math.max(maximum,times[i]);}
        double mean=sum/count;
        double slope=Double.isFinite(priorMean)?mean-priorMean:0;
        // Extrapolate gently. Never claim this is a real GPU timestamp.
        double forecast=Math.max(1,Math.min(250,mean+2*slope));
        String regime=count<6?"WARMUP":forecast>=40?"DISPLAY_PRESSURE":
                forecast>=23?"DISPLAY_WATCH":"DISPLAY_STABLE";
        priorMean=mean;lastPrediction=forecast;
        return new Result(count,mean,forecast,
                Double.isFinite(errorEwma)?errorEwma:Double.NaN,regime);
    }
}
