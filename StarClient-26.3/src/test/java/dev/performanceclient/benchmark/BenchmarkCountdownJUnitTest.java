package dev.performanceclient.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic one-minute timer test: no waiting 60 seconds in CI. */
public class BenchmarkCountdownJUnitTest {
    @Test
    void stopsSamplingAtDeadlineAndMarksAutoStop() throws IOException {
        Path root = Files.createTempDirectory("performanceclient-timer-");
        try {
            AtomicLong now = new AtomicLong(3_000_000_000L);
            BenchmarkService bench = new BenchmarkService(root, now::get);
            bench.begin(BenchmarkService.Mode.VANILLA, 2);
            assertEquals(60, bench.remainingSeconds());
            for (int i = 0; i < 40; ++i) {
                now.addAndGet(1_000_000_000L);
                bench.onFrame(16_000_000L, -1L);
            }
            assertEquals(20, bench.remainingSeconds());
            assertFalse(bench.expired());
            now.addAndGet(20_000_000_000L);
            assertTrue(bench.expired());
            assertEquals(0, bench.remainingSeconds());
            bench.onFrame(16_000_000L, -1L);
            assertEquals(38, bench.measuredFrames()); // two of the first forty are warmup
            assertEquals(38, bench.end(true).frames());
            assertFalse(bench.running());
            String csv = Files.readString(bench.file());
            assertTrue(csv.contains("AUTO_60S_WALLCLOCK"));
        } finally {
            Files.deleteIfExists(root.resolve("performanceclient/benchmark.csv"));
            Files.deleteIfExists(root.resolve("performanceclient"));
            Files.deleteIfExists(root);
        }
    }
}
