package dev.performanceclient.renderer.culling;
import dev.performanceclient.renderer.chunk.ChunkSnapshot;
/** Floods open voxels to compute face-to-face connectivity. 6-bit face masks. */
public final class CaveVisibilityGraph {
    private static final int[][] OFF={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private final int[] faces=new int[6];
    public CaveVisibilityGraph(ChunkSnapshot blocks){
        boolean[] seen=new boolean[4096];int[] q=new int[4096];
        for(int seed=0;seed<4096;seed++){
            int sx=seed&15,sz=(seed>>4)&15,sy=seed>>8;
            if(seen[seed]||blocks.solid(sx,sy,sz))continue;
            int head=0,tail=0,mask=0;q[tail++]=seed;seen[seed]=true;
            while(head<tail){
                int cur=q[head++],x=cur&15,z=(cur>>4)&15,y=cur>>8;
                if(x==15)mask|=1;if(x==0)mask|=2;
                if(y==15)mask|=4;if(y==0)mask|=8;
                if(z==15)mask|=16;if(z==0)mask|=32;
                for(int[] d:OFF){int nx=x+d[0],ny=y+d[1],nz=z+d[2];
                    if(nx<0||nx>15||ny<0||ny>15||nz<0||nz>15||blocks.solid(nx,ny,nz))continue;
                    int v=(ny<<8)|(nz<<4)|nx;
                    if(!seen[v]){seen[v]=true;q[tail++]=v;}
                }
            }
            for(int face=0;face<6;face++)if((mask&(1<<face))!=0)faces[face]|=mask;
        }
    }
    public boolean canSee(int fromFace,int toFace){
        if(fromFace<0||fromFace>5||toFace<0||toFace>5)throw new IllegalArgumentException();
        return (faces[fromFace]&(1<<toFace))!=0;
    }
}
