package dev.performanceclient.benchmark;
/** Fixed camera coordinates useful for future automatic single-player replay adapter. NOT attached to game camera yet. */
public final class RouteFixture {
    public static final long WORLD_SEED = 2639950L;
    public static final int DURATION_TICKS = 20*90;
    private RouteFixture() {}
    public static Sample at(int tick) {
        double t=Math.max(0,Math.min(DURATION_TICKS,tick))/(double)DURATION_TICKS;
        double angle=t*2*Math.PI;
        return new Sample(64+Math.sin(angle)*80, 96+6*Math.sin(angle*2),64+Math.cos(angle)*80, (float)Math.toDegrees(angle)+90, -15f);
    }
    public record Sample(double x,double y,double z,float yaw,float pitch) {}
}
