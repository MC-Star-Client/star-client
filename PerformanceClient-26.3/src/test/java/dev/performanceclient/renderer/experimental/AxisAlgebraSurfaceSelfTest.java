package dev.performanceclient.renderer.experimental;

import java.util.Random;

/** Independent, deterministic proof that every exposed cube face is encoded exactly once. */
public final class AxisAlgebraSurfaceSelfTest {
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    public static void main(String[] args) {
        Random rnd = new Random(0xA11CE55);
        AxisAlgebraSurface encoder = new AxisAlgebraSurface();
        long checks = 0;
        for (int iter = 0; iter < 650; iter++) {
            final boolean[][][] voxel = new boolean[18][18][18];
            int density = switch (iter % 7) {
                case 0 -> 0; case 1 -> 100; case 2 -> 10; case 3 -> 50;
                case 4 -> 90; case 5 -> 25; default -> 75;
            };
            for (int x = 0; x < 18; x++) for (int y = 0; y < 18; y++) for (int z = 0; z < 18; z++) {
                voxel[x][y][z] = rnd.nextInt(100) < density;
            }
            AxisAlgebraSurface.SolidSource blocks = (x, y, z) -> voxel[x + 1][y + 1][z + 1];
            encoder.encode(blocks);
            int count = 0;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                for (int f = 0; f < 6; f++) {
                    boolean visible = blocks.solid(x, y, z) && !blocks.solid(x + DX[f], y + DY[f], z + DZ[f]);
                    int plane = switch (f) { case 0,1 -> x; case 2,3 -> y; default -> z; };
                    int row = switch (f) { case 0,1,4,5 -> y; default -> z; };
                    int bit = switch (f) { case 0,1 -> z; default -> x; };
                    boolean encoded = (encoder.faceMask(f, plane, row) & (1 << bit)) != 0;
                    if (visible != encoded) {
                        throw new AssertionError("mismatch at test " + iter + ", xyz " + x + "," + y + "," + z + ", dir " + f);
                    }
                    if (visible) count++;
                    checks++;
                }
            }
            if (encoder.faceCount() != count) throw new AssertionError("face count mismatch: " + encoder.faceCount() + " vs " + count);
        }
        System.out.println("PASS: 650 distinct deterministic voxel cases, " + checks + " face checks, border neighbors included");
    }
}
