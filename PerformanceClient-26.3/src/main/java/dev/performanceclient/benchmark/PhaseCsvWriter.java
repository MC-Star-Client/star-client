package dev.performanceclient.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Locale;

/** Separate phase CSV aligned to the ordinary benchmark.csv runs. */
public final class PhaseCsvWriter {
    private PhaseCsvWriter() {}

    public static Path append(Path benchmarkCsv, BenchmarkService.Mode mode, int run, PhaseProfiler.Snapshot s) throws IOException {
        Path file = benchmarkCsv.resolveSibling("benchmark-phases.csv");
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            StringBuilder header = new StringBuilder("timestamp,mode,run,frames,visible_sections_avg,chunk_draw_calls_avg,gpu_wait_us,gpu_time_us");
            for (PhaseProfiler.Phase phase : PhaseProfiler.Phase.values()) header.append(',').append(phase.name().toLowerCase(Locale.ROOT)).append("_us");
            header.append(",notes\n");
            Files.writeString(file, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        StringBuilder row = new StringBuilder(Instant.now().toString()).append(',').append(mode).append(',').append(run)
            .append(',').append(s.frames()).append(',').append(format(s.averageVisibleSections())).append(",,,");
        for (PhaseProfiler.Phase phase : PhaseProfiler.Phase.values()) row.append(',').append(format(s.microsecondsPerFrame(phase)));
        row.append(",CPU_WALLTIME_INCLUSIVE_NESTED;DRAW_CALLS_AND_GPU_NOT_VERIFIED\n");
        Files.writeString(file, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return file;
    }

    private static String format(double d) {
        return Double.isFinite(d) ? String.format(Locale.ROOT, "%.3f", d) : "";
    }
}
