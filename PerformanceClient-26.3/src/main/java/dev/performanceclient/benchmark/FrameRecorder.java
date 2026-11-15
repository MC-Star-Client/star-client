package dev.performanceclient.benchmark;
import java.util.Arrays;
/** Collects actual wall-clock frame durations, not synthetic FPS. A frame hook calls record(). */
public final class FrameRecorder {
    private final long[] nano = new long[240_000];
    private final long[] cpuNano = new long[240_000];
    private int count;
    private long started;
    private int warmupFrames = 300;
    public void start(int warmup) {count = 0; warmupFrames = Math.max(0,warmup); started = System.nanoTime();}
    public void record(long wallNanos,long cpuNanos) {
        if(count<nano.length && wallNanos>0){nano[count]=wallNanos;cpuNano[count]=cpuNanos;count++;}
    }
    public boolean full() {return count == nano.length;}
    public int frames() {return count;}
    public long elapsedNanos() {return System.nanoTime()-started;}
    public Result summarize() {
        int n = Math.max(0,count-warmupFrames);
        if (n<20) throw new IllegalStateException("Need >=20 measured frames after warmup");
        long[] times = Arrays.copyOfRange(nano,warmupFrames,count);
        Arrays.sort(times);
        long total=0; for(long x:times) total+=x;
        int tail1 = Math.max(1,(int)Math.ceil(n*0.01));
        int tail01 = Math.max(1,(int)Math.ceil(n*0.001));
        long slow1=0,slow01=0;
        for(int i=n-tail1;i<n;i++)slow1+=times[i];
        for(int i=n-tail01;i<n;i++)slow01+=times[i];
        double fps = 1_000_000_000.0*n/total;
        double low1 = 1_000_000_000.0*tail1/slow1;
        double low01 = 1_000_000_000.0*tail01/slow01;
        double p99 = times[(int)Math.ceil(n*0.99)-1]/1e6;
        long cpu=0;int cpuCount=0;
        for(int i=warmupFrames;i<count;i++)if(cpuNano[i]>=0){cpu+=cpuNano[i];cpuCount++;}
        double cpuMs=cpuCount>0?(cpu/(1e6*cpuCount)):Double.NaN;
        return new Result(n,fps,low1,low01,p99,total/(1e6*n),cpuMs);
    }
    public record Result(int frames,double avgFps,double onePercentLow,double pointOnePercentLow,double p99Ms,double avgFrameMs,double avgCpuMs) {}
}
