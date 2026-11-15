package dev.performanceclient.pvp;
/** Independent CPS time window. Nothing is automated: measures clicks supplied by a future input adapter. */
public final class ClickCounter {
    private final long[] times=new long[100];private int next;
    public void click(long nowNanos){times[next++%times.length]=nowNanos;}
    public int cps(long nowNanos){int count=0;for(long t:times)if(t>0&&nowNanos-t<=1_000_000_000L&&nowNanos>=t)count++;return count;}
}
