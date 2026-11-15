package dev.performanceclient.renderer.chunk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * CPU-only voxel prototype: NOT a Minecraft block-model renderer.
 * Uses pooled, fixed-capacity direct buffers. Each Result MUST be closed after its
 * consumer has uploaded/copied the bytes; using bytes() after close is invalid.
 * No live world objects are accessed here.
 */
public final class PackedMesher {
    private static final int MAX_BYTES = 16 * 16 * 16 * 6 * 4 * 16;
    private static final int POOL_CAPACITY = 8;
    private static final ArrayBlockingQueue<ByteBuffer> POOL =
            new ArrayBlockingQueue<>(POOL_CAPACITY);

    private static final int[][] DIR = {
            {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };
    private static final int[][][] CORNERS = {
            {{1,0,0},{1,1,0},{1,1,1},{1,0,1}},
            {{0,0,1},{0,1,1},{0,1,0},{0,0,0}},
            {{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
            {{0,0,1},{0,0,0},{1,0,0},{1,0,1}},
            {{1,0,1},{1,1,1},{0,1,1},{0,0,1}},
            {{0,0,0},{0,1,0},{1,1,0},{1,0,0}}
    };

    private static ByteBuffer acquire() {
        ByteBuffer out = POOL.poll();
        if (out == null) {
            // One allocation per concurrent lease, not per meshing call after warmup.
            out = ByteBuffer.allocateDirect(MAX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        }
        out.clear();
        return out;
    }

    private static void recycle(ByteBuffer out) {
        out.clear();
        POOL.offer(out); // Discard excess buffers; bounded memory retained by pool.
    }

    public Result mesh(ChunkSnapshot snapshot) {
        ByteBuffer out = acquire();
        try {
            int quads = 0;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        if (!snapshot.solid(x,y,z)) continue;
                        for (int f = 0; f < 6; f++) {
                            int[] d = DIR[f];
                            if (snapshot.solid(x+d[0],y+d[1],z+d[2])) continue;
                            for (int v = 0; v < 4; v++) {
                                int[] c = CORNERS[f][v];
                                out.putShort((short)((x+c[0])*256));
                                out.putShort((short)((y+c[1])*256));
                                out.putShort((short)((z+c[2])*256));
                                out.put((byte)f).put(snapshot.type(x,y,z));
                                out.putInt(0xffffffff); // PRÜFEN: real AO, lighting, tint and block models.
                                out.putInt((v&3) | (f<<8));
                            }
                            quads++;
                        }
                    }
            out.flip();
            return new Result(out, quads);
        } catch (RuntimeException | Error failure) {
            recycle(out);
            throw failure;
        }
    }

    public static final class Result implements AutoCloseable {
        private ByteBuffer buffer;
        private final int quads;

        private Result(ByteBuffer buffer, int quads) {
            this.buffer = buffer;
            this.quads = quads;
        }

        public synchronized ByteBuffer bytes() {
            if (buffer == null) throw new IllegalStateException("Mesh result already closed");
            return buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }

        public int quads() { return quads; }

        @Override public synchronized void close() {
            if (buffer != null) {
                ByteBuffer released = buffer;
                buffer = null;
                recycle(released);
            }
        }
    }
}
