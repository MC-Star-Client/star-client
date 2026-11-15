package dev.performanceclient.performance;
/** Frame pacing guidance for host integration; no unsafe sleeping on render thread. */
public final class BackgroundPolicy {
    public static int suggestedFpsLimit(boolean focused,int foregroundLimit,int backgroundLimit){
        return focused?foregroundLimit:Math.clamp(backgroundLimit,5,60);
    }
    private BackgroundPolicy(){}
}
