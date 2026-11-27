package dev.performanceclient.benchmark;

import java.util.Arrays;

/**
 * Render-thread-only, measurement-only instrumentation. No allocation during frame hooks.
 * Timings are CPU wall times, not GPU execution time. Parent categories are inclusive.
 * Unobserved phases are NOT written as zero in CSV (see PhaseCsvWriter).
 */
public final class PhaseProfiler {
    public enum Phase {
        GAME_EXTRACT, LEVEL_EXTRACT, GRAPH_FRUSTUM, GRAPH_UPDATE,
        CHUNK_PREPARE, CHUNK_REBUILD, ENTITY_EXTRACT, BLOCK_ENTITY_EXTRACT,
        PARTICLE_EXTRACT, ENTITY_SUBMIT, BLOCK_ENTITY_SUBMIT,
        GUI_EXTRACT, GUI_DRAW, WORLD_RENDER, GAME_RENDER
    }

    private static final int COUNT = Phase.values().length;
    public static final PhaseProfiler INSTANCE = new PhaseProfiler();
    private final long[] started = new long[COUNT];
    private final long[] frameNanos = new long[COUNT];
    private final int[] frameCalls = new int[COUNT];
    private final long[] totalNanos = new long[COUNT];
    private final long[] totalCalls = new long[COUNT];
    private volatile boolean active;
    private Thread owner;
    private boolean frameOpen;
    private long measuredFrames;
    private long visibleTotal;
    private long visibleSamples;
    private long visibleLast;

    private PhaseProfiler() {}

    public void startRun() {
        active = false;
        owner = Thread.currentThread();
        Arrays.fill(started, 0L);
        Arrays.fill(frameNanos, 0L);
        Arrays.fill(frameCalls, 0);
        Arrays.fill(totalNanos, 0L);
        Arrays.fill(totalCalls, 0L);
        measuredFrames = visibleTotal = visibleSamples = visibleLast = 0L;
        frameOpen = false;
        active = true;
    }

    public void abortRun() { active = false; frameOpen = false; }

    public void beginFrame() {
        if (!active || Thread.currentThread() != owner) return;
        frameOpen = true;
        Arrays.fill(started, 0L);
        Arrays.fill(frameNanos, 0L);
        Arrays.fill(frameCalls, 0);
        visibleLast = -1;
    }

    public void begin(Phase phase) {
        if (!active || !frameOpen || Thread.currentThread() != owner) return;
        int i = phase.ordinal();
        if (started[i] == 0) started[i] = System.nanoTime();
    }

    public void end(Phase phase) {
        if (!active || !frameOpen || Thread.currentThread() != owner) return;
        int i = phase.ordinal();
        long start = started[i];
        if (start == 0) return;
        long elapsed = System.nanoTime() - start;
        started[i] = 0;
        if (elapsed > 0) {
            frameNanos[i] += elapsed;
            frameCalls[i]++;
        }
    }

    /** Sample from LevelRenderer.render before chunk render preparation. */
    public void visibleSections(int count) {
        if (active && frameOpen && Thread.currentThread() == owner && count >= 0) visibleLast = count;
    }

    /** Called at Minecraft.renderFrame TAIL, before BenchmarkService.onFrame. */
    public void endFrame(boolean accept) {
        if (!active || !frameOpen || Thread.currentThread() != owner) return;
        frameOpen = false;
        if (!accept) return;
        measuredFrames++;
        for (int i = 0; i < COUNT; i++) {
            totalNanos[i] += frameNanos[i];
            totalCalls[i] += frameCalls[i];
        }
        if (visibleLast >= 0) { visibleTotal += visibleLast; visibleSamples++; }
    }

    public Snapshot finishRun() {
        active = false;
        return new Snapshot(measuredFrames, totalNanos.clone(), totalCalls.clone(), visibleTotal, visibleSamples);
    }

    public record Snapshot(long frames, long[] nanos, long[] calls, long visibleTotal, long visibleSamples) {
        public double microsecondsPerFrame(Phase p) {
            int i = p.ordinal();
            return frames == 0 || calls[i] == 0 ? Double.NaN : nanos[i] / (1000.0 * frames);
        }
        public double averageVisibleSections() {
            return visibleSamples == 0 ? Double.NaN : (double) visibleTotal / visibleSamples;
        }
        public boolean has(Phase p) { return calls[p.ordinal()] > 0; }
    }
}
