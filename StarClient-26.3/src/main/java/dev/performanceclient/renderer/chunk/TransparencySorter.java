package dev.performanceclient.renderer.chunk;
import java.nio.*;
/** Far-to-near CPU quad index stream. Re-sorts only for significant camera movement.
 * PRÜFEN: still requires translucent Material-specific RenderPearl pipeline integration.
 */
public final class TransparencySorter {
    private float lastX=Float.NaN,lastY,lastZ,lastYaw,lastPitch;
    public boolean needsSort(float x,float y,float z,float yaw,float pitch){
        if(Float.isNaN(lastX))return true;
        float dx=x-lastX,dy=y-lastY,dz=z-lastZ;
        return dx*dx+dy*dy+dz*dz>2.25f || Math.abs(yaw-lastYaw)>8f || Math.abs(pitch-lastPitch)>8f;
    }
    public int[] order(float[] centers,float x,float y,float z,float yaw,float pitch){
        if(centers.length%3!=0)throw new IllegalArgumentException("one XYZ per quad");
        lastX=x;lastY=y;lastZ=z;lastYaw=yaw;lastPitch=pitch;
        int count=centers.length/3;int[] indexes=new int[count];float[] dist=new float[count];
        for(int i=0;i<count;i++){
            indexes[i]=i;float dx=centers[3*i]-x,dy=centers[3*i+1]-y,dz=centers[3*i+2]-z;
            dist[i]=dx*dx+dy*dy+dz*dz;
        }
        quickSort(indexes,dist,0,count-1);
        return indexes;
    }
    private static void quickSort(int[] ids,float[] depth,int lo,int hi){
        int i=lo,j=hi;float pivot=depth[ids[(lo+hi)>>>1]];
        while(i<=j){
            while(depth[ids[i]]>pivot)i++;
            while(depth[ids[j]]<pivot)j--;
            if(i<=j){int t=ids[i];ids[i++]=ids[j];ids[j--]=t;}
        }
        if(lo<j)quickSort(ids,depth,lo,j);
        if(i<hi)quickSort(ids,depth,i,hi);
    }
    public static ByteBuffer indexBuffer(int[] sortedQuads){
        ByteBuffer out=ByteBuffer.allocateDirect(sortedQuads.length*6*4).order(ByteOrder.LITTLE_ENDIAN);
        for(int q:sortedQuads){int v=q*4;
            out.putInt(v).putInt(v+1).putInt(v+2).putInt(v+2).putInt(v+3).putInt(v);
        }
        out.flip();return out;
    }
}
