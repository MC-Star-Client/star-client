package dev.performanceclient.hud;
public final class FrametimeGraph {
    private final float[] milliseconds=new float[240];private int next;private int count;
    public void record(long frameNs){milliseconds[next++%milliseconds.length]=(float)(frameNs/1e6);count=Math.min(count+1,milliseconds.length);}
    public float last(){if(count==0)return 0f;return milliseconds[(next-1+milliseconds.length)%milliseconds.length];}
    /** Durchschnitts-FPS der letzten bis zu 60 Frames. */
    public float averageFps(){int n=Math.min(count,60);if(n==0)return 0f;float sum=0;for(int i=1;i<=n;i++)sum+=milliseconds[(next-i+milliseconds.length*2)%milliseconds.length];return sum<=0?0f:n*1000f/sum;}
    public float worst(){float m=0;for(int i=0;i<count;i++)m=Math.max(m,milliseconds[i]);return m;}
}
