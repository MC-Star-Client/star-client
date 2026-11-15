package dev.performanceclient.performance;
import java.util.concurrent.ConcurrentLinkedQueue;
/** Processing jobs with a monotonic per-frame deadline. */
public final class FrameBudgetQueue {
    private final ConcurrentLinkedQueue<Runnable> tasks=new ConcurrentLinkedQueue<>();
    private long frameDeadlineNanos;
    /** A soft per-frame budget; never cancels a rebuild already in progress. */
    public void beginFrame(int micros){frameDeadlineNanos=System.nanoTime()+Math.max(0,micros)*1000L;}
    public boolean hasBudget(){return frameDeadlineNanos==0L||System.nanoTime()<frameDeadlineNanos;}
    public void enqueue(Runnable job){tasks.add(job);}
    public int drain(int budgetMicros){
        long deadline=System.nanoTime()+Math.max(0,budgetMicros)*1000L;int completed=0;
        while(System.nanoTime()<deadline){Runnable r=tasks.poll();if(r==null)break;r.run();completed++;}
        return completed;
    }
    public int pending(){return tasks.size();}
}
