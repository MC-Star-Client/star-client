package dev.performanceclient.benchmark;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Locale;
import java.util.function.LongSupplier;
/** Manual A/B series: same world, same journey, same settings; record real frames via mixin. */
public final class BenchmarkService {
    public enum Mode { VANILLA, CLIENT }
    /** Strict wall-clock duration from F9 press. Warmup is included in these 60 seconds. */
    public static final long CAPTURE_NANOS = 60_000_000_000L;
    private final LongSupplier clock;
    private long startedAtNanos;
    private final FrameRecorder recorder = new FrameRecorder();
    private boolean running;
    private Mode mode=Mode.VANILLA;
    private final Path csv;
    private int run;
    private int warmupFrames;
    private FrameRecorder.Result previous;
    public BenchmarkService(Path base) { this(base, System::nanoTime); }
    // Injectable monotonic clock for deterministic timer tests; no Minecraft API needed.
    BenchmarkService(Path base, LongSupplier clock) {
        this.csv=base.resolve("performanceclient/benchmark.csv");
        this.clock=java.util.Objects.requireNonNull(clock);
    }
    public void begin(Mode next,int warmupFrames) {
        if(running)throw new IllegalStateException("Already running");
        mode=next;
        this.warmupFrames=Math.max(0,warmupFrames);
        recorder.start(this.warmupFrames);
        startedAtNanos=clock.getAsLong();
        running=true;
    }
    public void onFrame(long nanos,long cpuNanos) {
        // Do not include frames completed beyond the one-minute deadline.
        if (running && !expired()) recorder.record(nanos,cpuNanos);
    }
    /** Monotonic elapsed time, independent of the wall clock and the FPS. */
    public long elapsedNanos(){ return running ? Math.max(0L,clock.getAsLong()-startedAtNanos) : 0L; }
    public long remainingNanos(){ return running ? Math.max(0L,CAPTURE_NANOS-elapsedNanos()) : 0L; }
    /** Uses ceiling: 59 seconds remain until at least one full second has passed. */
    public int remainingSeconds(){return (int)((remainingNanos()+999_999_999L)/1_000_000_000L);}
    public boolean expired(){return running && elapsedNanos()>=CAPTURE_NANOS;}
    public void cancel(){running=false;}
    public boolean running(){return running;}
    public Mode mode(){return mode;}
    public int warmupRemaining(){return Math.max(0,warmupFrames-recorder.frames());}
    public int measuredFrames(){return Math.max(0,recorder.frames()-warmupFrames);}
    public synchronized FrameRecorder.Result end() throws IOException {return end(false);}
    public synchronized FrameRecorder.Result end(boolean automatic) throws IOException {
        if(!running)throw new IllegalStateException("Not running");running=false;
        FrameRecorder.Result r=recorder.summarize();
        Files.createDirectories(csv.getParent());
        if(!Files.exists(csv))Files.writeString(csv,"timestamp,mode,run,frames,avg_fps,one_percent_low,point_one_percent_low,p99_ms,avg_frame_ms,cpu_ms,gpu_ms,world_seed,route_id,notes\n",StandardCharsets.UTF_8);
        // CPU thread time is reported only if supported/enabled. GPU timestamp is intentionally blank.
        String cpu=Double.isFinite(r.avgCpuMs())?String.format(Locale.ROOT,"%.5f",r.avgCpuMs()):"";
        boolean identical=previous!=null && suspiciouslyIdentical(previous,r);
        String note = identical ? "SUSPICIOUS_IDENTICAL_RESULTS" : "MANUAL_ROUTE_NOT_REPRODUCIBLE";
        if (automatic) note += ";AUTO_60S_WALLCLOCK";
        String row=String.format(Locale.ROOT,"%s,%s,%d,%d,%.4f,%.4f,%.4f,%.5f,%.5f,%s,,UNKNOWN,MANUAL_UNVERIFIED,%s%n",
          Instant.now(),mode,++run,r.frames(),r.avgFps(),r.onePercentLow(),r.pointOnePercentLow(),r.p99Ms(),r.avgFrameMs(),cpu,note);
        previous=r;
        Files.writeString(csv,row,StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        return r;
    }
    public static boolean suspiciouslyIdentical(FrameRecorder.Result a,FrameRecorder.Result b) {
        return a.frames()==b.frames() && Double.doubleToLongBits(a.avgFps())==Double.doubleToLongBits(b.avgFps()) &&
               Double.doubleToLongBits(a.p99Ms())==Double.doubleToLongBits(b.p99Ms());
    }
    public int lastRunNumber(){return run;}
    public Path file(){return csv;}
}
