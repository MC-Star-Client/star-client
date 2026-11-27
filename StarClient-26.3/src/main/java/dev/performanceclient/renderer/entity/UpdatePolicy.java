package dev.performanceclient.renderer.entity;
/** Opt-in distant updates. Never change player position / hitboxes / collision or game simulation. */
public final class UpdatePolicy {
    public static boolean renderAnimationThisFrame(double distanceSquared,long frame) {
        if(distanceSquared<32*32)return true;
        if(distanceSquared<96*96)return (frame&1)==0;
        return (frame&3)==0;
    }
    public static int particleBudget(int preset){return switch (Math.clamp(preset,0,3)){
        case 0 -> 500;case 1 -> 1200;case 2 -> 3000;default -> 6000;
    };}
    private UpdatePolicy(){}
}
