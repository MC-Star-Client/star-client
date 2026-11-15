package dev.performanceclient.renderer.buffer;
import java.nio.*;
/** Portable CPU staging encoding indexed-indirect calls. No GPU draw happens here. */
public final class IndirectDrawPlan {
    private final ByteBuffer commands;
    private int count;
    public IndirectDrawPlan(int maxCommands){
        if(maxCommands<1)throw new IllegalArgumentException();
        commands=ByteBuffer.allocateDirect(maxCommands*20).order(ByteOrder.nativeOrder());
    }
    public boolean add(int indices,int firstIndex,int baseVertex){
        if(indices<0||firstIndex<0)return false;
        if(commands.remaining()<20)return false;
        commands.putInt(indices).putInt(1).putInt(firstIndex).putInt(baseVertex).putInt(0);
        count++;return true;
    }
    public int drawCount(){return count;}
    public ByteBuffer bytes(){ByteBuffer data=commands.asReadOnlyBuffer();data.flip();return data;}
    public void clear(){commands.clear();count=0;}
}
