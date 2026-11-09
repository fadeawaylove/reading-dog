package com.example.yuejie;

import org.json.*;import java.util.*;import java.util.function.BooleanSupplier;import android.os.SystemClock;

/** Cache-first, cursor-keyed single-flight requests. Prefetch never advances a panel cursor. */
final class CommentPages {
 static final class Scope {
  final String book,chapter,version,paragraph,text;
  Scope(String b,String c,String v,String p,String t){book=b;chapter=c;version=v;paragraph=p;text=t;}
  String key(String cursor){return ParagraphCommentApi.key("page",book,chapter,version,paragraph,cursor);}
 }
 interface Result{void done(JSONObject value,Exception error);}
 interface Clock{long now();}
 final ParagraphCommentApi api;final CommentExecutor executor;final Map<String,Flight> flights=new HashMap<>();final Map<String,Failure> failures=new HashMap<>();Clock clock=SystemClock::elapsedRealtime;
 static final class Failure{int attempts;long next;}
 static final class Waiter {final int priority;final BooleanSupplier active;final Result result;Waiter(int p,BooleanSupplier a,Result r){priority=p;active=a;result=r;}}
 final class Flight {final Scope scope;final String cursor,key;final List<Waiter> waiters=new ArrayList<>();CommentExecutor.Job job;Flight(Scope s,String c){scope=s;cursor=c;key=s.key(c);}boolean active(){synchronized(CommentPages.this){for(Waiter w:waiters)if(w.active.getAsBoolean())return true;return false;}}}
 CommentPages(ParagraphCommentApi api,CommentExecutor executor){this.api=api;this.executor=executor;}
 CommentStore.Entry cached(Scope scope,String cursor){CommentStore.Entry entry=api.cache.get(scope.key(cursor));return entry!=null&&entry.value.optInt("schema")>=3?entry:null;}
 void request(Scope scope,String cursor,int priority,boolean explicit,BooleanSupplier active,Result result){
  if(!active.getAsBoolean())return;
  String key=scope.key(cursor);Flight flight;
  synchronized(this){
   if(explicit)failures.remove(key);
   Flight old=flights.get(key);if(old!=null&&old.job!=null&&old.job.isCancelled()){flights.remove(key);old=null;}if(old!=null&&!old.active()&&(old.job==null||!old.job.isDone())){if(old.job!=null&&executor.getQueue().remove(old.job)){old.job.cancel(false);flights.remove(key);}else{old.waiters.add(new Waiter(priority,active,result));if(old.job!=null)executor.promote(old.job,priority);return;}}
   old=flights.get(key);if(old!=null){old.waiters.add(new Waiter(priority,active,result));if(old.job!=null)executor.promote(old.job,priority);return;}
   Failure failure=failures.get(key);if(failure!=null&&(failure.attempts>=3||clock.now()<failure.next)){if(priority==CommentExecutor.USER)result.done(null,new java.io.IOException("段评暂未准备好，点击重试"));return;}
   flight=new Flight(scope,cursor);flight.waiters.add(new Waiter(priority,active,result));flights.put(key,flight);
   flight.job=executor.enqueue(priority,flight::active,()->fetch(flight));
  }
 }
 void fetch(Flight flight){JSONObject value=null;Exception error=null;try{if(!flight.active())return;CommentStore.Entry cached=cached(flight.scope,flight.cursor);if(cached!=null&&!cached.stale)value=cached.value;else value=api.comments(flight.scope.book,flight.scope.chapter,flight.scope.version,flight.scope.paragraph,flight.cursor,flight.scope.text);}catch(Exception e){error=e;}finally{
   final JSONObject result=value;final Exception problem=error;final List<Waiter> waiting;long delay=0;boolean retry=false;
   synchronized(this){if(flights.get(flight.key)==flight)flights.remove(flight.key);waiting=new ArrayList<>(flight.waiters);if(problem==null){failures.remove(flight.key);}else{Failure f=failures.get(flight.key);if(f==null){f=new Failure();failures.put(flight.key,f);}f.attempts++;delay=Math.min(15000,2000L<<(f.attempts-1));f.next=clock.now()+delay;retry=f.attempts<3;}}
   final boolean again=retry;final long retryDelay=delay;
   NovelApi.MAIN.post(()->{for(Waiter w:waiting)if(w.active.getAsBoolean()){w.result.done(result,problem);if(problem!=null&&again&&w.priority>CommentExecutor.USER)NovelApi.MAIN.postDelayed(()->request(flight.scope,flight.cursor,w.priority,false,w.active,w.result),retryDelay);}});
  }
 }
 synchronized void prune(){executor.prune();Iterator<Flight> it=flights.values().iterator();while(it.hasNext()){Flight f=it.next();f.waiters.removeIf(w->!w.active.getAsBoolean());if(f.waiters.isEmpty()&&f.job!=null&&f.job.isCancelled())it.remove();}}
 synchronized void resetFailures(){failures.clear();}
}
