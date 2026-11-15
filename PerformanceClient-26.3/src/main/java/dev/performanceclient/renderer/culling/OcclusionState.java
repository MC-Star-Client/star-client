package dev.performanceclient.renderer.culling;
/** Conservative last-frame occlusion hint. New/untrusted chunks stay visible to prevent holes. */
public final class OcclusionState {
    private long lastValidFrame=-1;
    private boolean hidden;
    public void update(long frame,boolean definitelyHidden){lastValidFrame=frame;hidden=definitelyHidden;}
    public boolean shouldRender(long frame,boolean movingFast){
        if(movingFast||lastValidFrame<0||frame-lastValidFrame>1)return true;
        return !hidden;
    }
}
