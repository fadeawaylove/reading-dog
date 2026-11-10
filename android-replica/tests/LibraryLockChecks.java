package com.example.yuejie;
import android.app.*;import android.os.*;import org.json.*;import java.util.concurrent.*;

/** Reproduce page-save versus comment-prefetch lock inversion on an isolated real SQLite DB. */
public final class LibraryLockChecks extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();ExecutorService jobs=Executors.newFixedThreadPool(2);LocalLibrary store=new LocalLibrary(getTargetContext());boolean success=false;
  try{
   String book="991234",chapter="991235";store.chapter(book,chapter,new JSONObject().put("id",chapter).put("text","并发测试正文"));store.directory(book,new JSONArray().put(new JSONObject().put("id",chapter).put("title","并发")));
   CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
   Future<?> writes=jobs.submit(()->{ready.countDown();try{go.await();for(int i=0;i<300;i++)store.progress(book,chapter,i,0,1,"并发");}catch(InterruptedException e){throw new RuntimeException(e);}});
   Future<?> reads=jobs.submit(()->{ready.countDown();try{go.await();for(int i=0;i<600;i++)if(store.chapter(book,chapter)==null)throw new AssertionError("lost body");}catch(InterruptedException e){throw new RuntimeException(e);}});
   if(!ready.await(2,TimeUnit.SECONDS))throw new AssertionError("workers did not start");go.countDown();writes.get(8,TimeUnit.SECONDS);reads.get(8,TimeUnit.SECONDS);
   if(store.progress(book).getInt("offset")!=299)throw new AssertionError("lost final position");boolean queued=false;JSONArray pending=LibrarySync.pending(store.getReadableDatabase());for(int i=0;i<pending.length();i++){JSONObject op=pending.getJSONObject(i);if(book.equals(op.optString("book"))&&op.getJSONObject("value").optInt("offset")==299)queued=true;}if(!queued)throw new AssertionError("lost synchronized position");
   success=true;result.putString("stream","PASS 300 progress saves concurrent with 600 chapter-cache reads; final offset and sync outbox intact\n");
  }catch(Throwable e){result.putString("stream","FAIL page-save/comment-prefetch lock regression: "+e+"\n");}
  finally{jobs.shutdownNow();if(success)store.close();finish(success?0:1,result);}
 }
}
