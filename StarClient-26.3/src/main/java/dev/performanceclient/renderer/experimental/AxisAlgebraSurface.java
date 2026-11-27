package dev.performanceclient.renderer.experimental;

import java.util.Arrays;

/**
 * Experimental axis-algebra surface encoder for 16^3 opaque FULL CUBES only.
 *
 * Encodes exact exposed unit-faces as six banks of 16 planes * 16 rows * 16 bits,
 * using 1D bitwise neighborhood differences rather than constructing vertices.
 * A GPU shader/draw implementation is NOT provided by this class.
 *
 * Axis conventions:
 *  face 0 +X: plane=x, row=y, bit=z
 *  face 1 -X: plane=x, row=y, bit=z
 *  face 2 +Y: plane=y, row=z, bit=x
 *  face 3 -Y: plane=y, row=z, bit=x
 *  face 4 +Z: plane=z, row=y, bit=x
 *  face 5 -Z: plane=z, row=y, bit=x
 *
 * This prototype assumes unsupported block-model types are handled by Vanilla.
 */
public final class AxisAlgebraSurface {
    public interface SolidSource {
        boolean solid(int x, int y, int z);
    }

    private static final int SIZE = 16;
    private static final int LAST = 1 << 15;
    private final short[] planes = new short[6 * SIZE * SIZE];
    private int faces;

    public int faceMask(int face, int plane, int row) {
        if (face < 0 || face >= 6 || plane < 0 || plane >= SIZE || row < 0 || row >= SIZE)
            throw new IndexOutOfBoundsException();
        return Short.toUnsignedInt(planes[(face * SIZE + plane) * SIZE + row]);
    }

    public int faceCount() {
        return faces;
    }

    public void encode(SolidSource blocks) {
        Arrays.fill(planes, (short) 0);
        faces = 0;
        // X-axis: for each Y,Z, determine all exposed X-faces with bitwise differences.
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                int occupancy = 0;
                for (int x = 0; x < SIZE; x++) {
                    if (blocks.solid(x, y, z)) occupancy |= 1 << x;
                }
                int plus = occupancy & ~(occupancy >>> 1) & 0xffff;
                int minus = occupancy & ~(occupancy << 1) & 0xffff;
                if (blocks.solid(SIZE, y, z)) plus &= ~LAST;
                if (blocks.solid(-1, y, z)) minus &= ~1;
                while (plus != 0) {
                    int x = Integer.numberOfTrailingZeros(plus);
                    planes[(x * SIZE) + y] |= (short) (1 << z);
                    faces++;
                    plus &= plus - 1;
                }
                while (minus != 0) {
                    int x = Integer.numberOfTrailingZeros(minus);
                    planes[((SIZE + x) * SIZE) + y] |= (short) (1 << z);
                    faces++;
                    minus &= minus - 1;
                }
            }
        }
        // Y-axis: for each Z,X, extract exact +/-Y faces.
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int occupancy = 0;
                for (int y = 0; y < SIZE; y++) {
                    if (blocks.solid(x, y, z)) occupancy |= 1 << y;
                }
                int plus = occupancy & ~(occupancy >>> 1) & 0xffff;
                int minus = occupancy & ~(occupancy << 1) & 0xffff;
                if (blocks.solid(x, SIZE, z)) plus &= ~LAST;
                if (blocks.solid(x, -1, z)) minus &= ~1;
                while (plus != 0) {
                    int y = Integer.numberOfTrailingZeros(plus);
                    planes[((2 * SIZE + y) * SIZE) + z] |= (short) (1 << x);
                    faces++;
                    plus &= plus - 1;
                }
                while (minus != 0) {
                    int y = Integer.numberOfTrailingZeros(minus);
                    planes[((3 * SIZE + y) * SIZE) + z] |= (short) (1 << x);
                    faces++;
                    minus &= minus - 1;
                }
            }
        }
        // Z-axis: for each Y,X, extract exact +/-Z faces.
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int occupancy = 0;
                for (int z = 0; z < SIZE; z++) {
                    if (blocks.solid(x, y, z)) occupancy |= 1 << z;
                }
                int plus = occupancy & ~(occupancy >>> 1) & 0xffff;
                int minus = occupancy & ~(occupancy << 1) & 0xffff;
                if (blocks.solid(x, y, SIZE)) plus &= ~LAST;
                if (blocks.solid(x, y, -1)) minus &= ~1;
                while (plus != 0) {
                    int z = Integer.numberOfTrailingZeros(plus);
                    planes[((4 * SIZE + z) * SIZE) + y] |= (short) (1 << x);
                    faces++;
                    plus &= plus - 1;
                }
                while (minus != 0) {
                    int z = Integer.numberOfTrailingZeros(minus);
                    planes[((5 * SIZE + z) * SIZE) + y] |= (short) (1 << x);
                    faces++;
                    minus &= minus - 1;
                }
            }
        }
    }
}
