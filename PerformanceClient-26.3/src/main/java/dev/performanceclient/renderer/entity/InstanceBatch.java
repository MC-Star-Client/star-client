package dev.performanceclient.renderer.entity;
import java.nio.*;
/** Groups equivalent instances into a packed staging buffer; does not replace Vanilla entity renderer. */
public final class InstanceBatch {
    private final ByteBuffer buffer;
    private int size;
    public InstanceBatch(int maxInstances){buffer=ByteBuffer.allocateDirect(maxInstances*32).order(ByteOrder.LITTLE_ENDIAN);}
    public boolean add(float x,float y,float z,float yaw,float scale,int rgba,int modelId,int flags){
        if(buffer.remaining()<32)return false;
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(yaw).putFloat(scale)
              .putInt(rgba).putInt(modelId).putInt(flags);size++;return true;
    }
    public ByteBuffer view(){ByteBuffer view=buffer.asReadOnlyBuffer();view.flip();return view;}
    public int instances(){return size;}
    public void reset(){buffer.clear();size=0;}
}
