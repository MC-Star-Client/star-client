package dev.performanceclient.performance;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.config.ClientPreferences;

/** Shared A/B gate. A failed hook stays disabled for the process lifetime. */
public final class StageOneControl {
    public enum Feature { SECTION, REBUILD, ENTITY, PARTICLE }
    public enum Mode { VANILLA, CLIENT }
    public static final StageOneControl INSTANCE = new StageOneControl();

    private final boolean[] faulted = new boolean[Feature.values().length];
    private final long[] hookCalls = new long[Feature.values().length];
    private final long[] appliedCalls = new long[Feature.values().length];
    private final boolean[] configured = new boolean[Feature.values().length];
    private volatile Mode mode = Mode.CLIENT;
    private int framesSinceWorldChange;
    private final FrameBudgetQueue rebuildBudget = new FrameBudgetQueue();

    private StageOneControl() {
        java.util.Arrays.fill(configured, true);
    }

    public void configure(ClientPreferences preferences) {
        for (Feature f : Feature.values()) {
            configured[f.ordinal()] = preferences.enabled("stage1." + f.name().toLowerCase(java.util.Locale.ROOT), true);
        }
    }

    public boolean enabled(Feature feature) {
        return mode == Mode.CLIENT && configured[feature.ordinal()] && !faulted[feature.ordinal()]
            && framesSinceWorldChange >= 3;
    }

    public void hookCalled(Feature feature) { hookCalls[feature.ordinal()]++; }
    public long hookCalls(Feature feature) { return hookCalls[feature.ordinal()]; }
    /** Actual intervention, not simply entering a Mixin. */
    public void applied(Feature feature) { appliedCalls[feature.ordinal()]++; }
    public long appliedCalls(Feature feature) { return appliedCalls[feature.ordinal()]; }

    public boolean configured(Feature feature) {
        return configured[feature.ordinal()] && !faulted[feature.ordinal()];
    }

    public void setConfigured(Feature feature, boolean enabled) {
        configured[feature.ordinal()] = enabled;
    }

    public Mode mode() { return mode; }
    public void setMode(Mode next) { mode = next; }

    public void worldChanged() { framesSinceWorldChange = 0; }

    public void beginFrame() {
        if (framesSinceWorldChange < 3) framesSinceWorldChange++;
    }

    public void beginCompileBatch() {
        rebuildBudget.beginFrame(900);
    }

    public boolean allowSynchronousRebuild() { return rebuildBudget.hasBudget(); }

    public void fault(Feature feature, Throwable failure) {
        faulted[feature.ordinal()] = true;
        // Exception logging is deliberately outside the hot path.
        PerformanceClient.LOGGER.warn("Stage-1 feature {} disabled; falling back to Vanilla", feature, failure);
    }
}
