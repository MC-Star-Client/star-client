package dev.performanceclient.renderer.chunk;

import java.util.Arrays;

/** Dependency-free correctness/lifetime smoke test; run via scripts/macos/test-core.sh. */
public final class PackedMesherPoolSelfTest {
    private static void verify(boolean expression, String message) {
        if (!expression) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        PackedMesher mesher = new PackedMesher();
        byte[] one = new byte[4096];
        one[0] = 1;
        ChunkSnapshot oneBlock = new ChunkSnapshot(one);
        for (int i = 0; i < 32; i++) {
            try (PackedMesher.Result r = mesher.mesh(oneBlock)) {
                verify(r.quads() == 6, "Single voxel needs 6 faces");
                verify(r.bytes().remaining() == 6 * 4 * 16, "Packed bytes mismatch");
            }
        }
        byte[] all = new byte[4096];
        Arrays.fill(all, (byte)1);
        try (PackedMesher.Result r = mesher.mesh(new ChunkSnapshot(all))) {
            verify(r.quads() == 6 * 16 * 16, "Solid section exterior faces mismatch");
        }
        PackedMesher.Result closed = mesher.mesh(oneBlock);
        closed.close();
        closed.close(); // idempotent
        boolean failed = false;
        try {closed.bytes();} catch (IllegalStateException expected) { failed = true; }
        verify(failed, "Use-after-close must be rejected");
        System.out.println("PackedMesher CPU-only self-test PASSED (not a Minecraft integration test)");
    }
}
