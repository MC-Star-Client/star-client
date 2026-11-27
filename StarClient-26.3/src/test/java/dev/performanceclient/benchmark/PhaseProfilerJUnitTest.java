package dev.performanceclient.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseProfilerJUnitTest {
    @Test void unobservedPhasesAreMissingNotZero() {
        PhaseProfiler p = PhaseProfiler.INSTANCE;
        p.startRun(); p.beginFrame();
        p.begin(PhaseProfiler.Phase.LEVEL_EXTRACT);
        for (int i=0; i<1000; i++) Math.sqrt(i);
        p.end(PhaseProfiler.Phase.LEVEL_EXTRACT);
        p.visibleSections(42);
        p.endFrame(true);
        PhaseProfiler.Snapshot r = p.finishRun();
        assertEquals(1L, r.frames());
        assertEquals(42.0, r.averageVisibleSections());
        assertTrue(r.microsecondsPerFrame(PhaseProfiler.Phase.LEVEL_EXTRACT)>0);
        assertTrue(Double.isNaN(r.microsecondsPerFrame(PhaseProfiler.Phase.GRAPH_UPDATE)));
    }
}
