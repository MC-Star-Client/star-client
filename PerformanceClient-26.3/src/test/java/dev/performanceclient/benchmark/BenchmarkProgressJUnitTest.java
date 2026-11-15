package dev.performanceclient.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class BenchmarkProgressJUnitTest {
    @Test void warmupProgressAndCsv() throws IOException {
        Path temporary = Files.createTempDirectory("performanceclient-benchmark-test-");
        try {
            BenchmarkService benchmark = new BenchmarkService(temporary);
            benchmark.begin(BenchmarkService.Mode.VANILLA, 300);
            assertEquals(300, benchmark.warmupRemaining());
            assertEquals(0, benchmark.measuredFrames());
            for (int i = 0; i < 350; i++) benchmark.onFrame(16_500_000L, -1L);
            assertEquals(0, benchmark.warmupRemaining());
            assertEquals(50, benchmark.measuredFrames());
            assertEquals(50, benchmark.end().frames());
            assertFalse(benchmark.running());
            assertTrue(Files.readString(benchmark.file()).contains("MANUAL_UNVERIFIED"));
        } finally {
            Path csv = temporary.resolve("performanceclient/benchmark.csv");
            Files.deleteIfExists(csv);
            Files.deleteIfExists(csv.getParent());
            Files.deleteIfExists(temporary);
        }
    }
}
