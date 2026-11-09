package com.example.yuejie;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Async HTTP comment dispatch reserves the normal HTTP workers for reading and directory traffic. */
final class CommentDispatch implements AutoCloseable {
 final AtomicInteger pending=new AtomicInteger();final AtomicLong sequence=new AtomicLong();
 final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new PriorityBlockingQueue<>());
 final class Job implements Runnable,Comparable<Job>{final int priority;final long order=sequence.incrementAndGet();final Runnable work;Job(int p,Runnable r){priority=p;work=r;}public int compareTo(Job other){int c=Integer.compare(priority,other.priority);return c==0?Long.compare(order,other.order):c;}public void run(){try{work.run();}finally{pending.decrementAndGet();}}}
 boolean submit(int priority,Runnable work){if(pending.incrementAndGet()>32){pending.decrementAndGet();return false;}try{worker.execute(new Job(Math.max(0,Math.min(2,priority)),work));return true;}catch(RejectedExecutionException e){pending.decrementAndGet();return false;}}
 public void close(){worker.shutdownNow();}
}
