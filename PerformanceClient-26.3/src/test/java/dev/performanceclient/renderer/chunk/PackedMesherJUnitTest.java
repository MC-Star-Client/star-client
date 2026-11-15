package dev.performanceclient.renderer.chunk;

import org.junit.jupiter.api.Test;

/** Runs the existing CPU-only smoke test through Gradle's JUnit Platform. */
final class PackedMesherJUnitTest {
    @Test
    void packedMesherPoolSelfTest() {
        PackedMesherPoolSelfTest.main(new String[0]);
    }
}
