package com.example.yuejie;

import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;import java.util.function.BooleanSupplier;

/** One comment network lane. Foreground work overtakes queued lookahead work. */
final class CommentExecutor extends ThreadPoolExecutor {
 static final int USER=0,NEXT=1,NEARBY=2;
 private final AtomicLong sequence=new AtomicLong();
 final class Job extends FutureTask<Void> implements Comparable<Job>{
  int priority;final long order=sequence.incrementAndGet();final BooleanSupplier active;
  Job(int priority,BooleanSupplier active,Runnable work){super(work,null);this.priority=priority;this.active=active;}
  public int compareTo(Job other){int c=Integer.compare(priority,other.priority);return c==0?Long.compare(order,other.order):c;}
  public void run(){if(isCancelled()||!active.getAsBoolean()){cancel(false);return;}if(BackendClient.readingRequests.get()>0){NovelApi.MAIN.postDelayed(()->{if(!isShutdown()&&active.getAsBoolean())CommentExecutor.super.execute(this);else cancel(false);},100);return;}BackendClient.commentPriority.set(priority);try{super.run();}finally{BackendClient.commentPriority.remove();}}
 }
 CommentExecutor(){super(1,1,0,TimeUnit.MILLISECONDS,new PriorityBlockingQueue<Runnable>());}
 Job enqueue(int priority,BooleanSupplier active,Runnable work){Job job=new Job(priority,active,work);super.execute(job);return job;}
 @Override public void execute(Runnable work){if(work instanceof CommentExecutor.Job)super.execute(work);else enqueue(USER,()->!isShutdown(),work);}
 void promote(Job job,int priority){synchronized(job){if(priority>=job.priority)return;boolean queued=getQueue().remove(job);job.priority=priority;if(queued)super.execute(job);}}
 void prune(){for(Runnable r:getQueue().toArray(new Runnable[0])){Job job=(Job)r;if(!job.active.getAsBoolean()){job.cancel(false);getQueue().remove(job);}}}
}
