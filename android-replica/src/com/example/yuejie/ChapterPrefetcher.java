package com.example.yuejie;

import java.util.*;
import java.util.concurrent.*;

/** Bounded, serial disk warming. The immediate next chapter is handled by the reader's layout preloader. */
final class ChapterPrefetcher implements AutoCloseable {
  static final int AHEAD=5;
  interface Fetcher {void fetch(String bookId,String chapterId)throws Exception;}
  interface Cache {boolean contains(String bookId,String chapterId);}
  private final Fetcher fetcher;private final Cache cache;private final Runnable ready;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private volatile long generation;private volatile boolean closed;private Future<?> pending;private String window="";
  ChapterPrefetcher(Fetcher fetcher,Cache cache){this(fetcher,cache,()->{});}
  ChapterPrefetcher(Fetcher fetcher,Cache cache,Runnable ready){this.fetcher=fetcher;this.cache=cache;this.ready=ready;}
  static List<String> window(List<String> ids,int current){List<String> out=new ArrayList<>();for(int distance=2;distance<=AHEAD;distance++){int at=current+distance;if(at>=0&&at<ids.size())out.add(ids.get(at));}return out;}
  synchronized void update(String bookId,List<String> ids,int current){
    if(closed)return;List<String> targets=window(ids,current);String key=bookId+":"+current+":"+targets;
    if(key.equals(window))return;window=key;final long token=++generation;if(pending!=null)pending.cancel(false);
    pending=worker.submit(()->{for(String id:targets){if(closed||generation!=token||Thread.currentThread().isInterrupted())return;
      try{if(!cache.contains(bookId,id))fetcher.fetch(bookId,id);if(!closed&&generation==token)ready.run();}catch(Exception failure){return;}
    }});
  }
  synchronized void cancel(){generation++;window="";if(pending!=null)pending.cancel(false);}
  public synchronized void close(){closed=true;cancel();worker.shutdownNow();}
}
