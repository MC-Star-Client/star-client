package dev.performanceclient.renderer.chunk;
import java.util.Arrays;
/** Self-contained immutable voxel classification snapshot. Never access Minecraft world from worker. */
public final class ChunkSnapshot {
    private final byte[] blocks;
    public ChunkSnapshot(byte[] data) {
        if(data.length!=4096)throw new IllegalArgumentException("16^3 required");
        blocks=Arrays.copyOf(data,4096);
    }
    public boolean solid(int x,int y,int z){return x>=0&&x<16&&y>=0&&y<16&&z>=0&&z<16&&blocks[(y*16+z)*16+x]!=0;}
    public byte type(int x,int y,int z){return blocks[(y*16+z)*16+x];}
}
