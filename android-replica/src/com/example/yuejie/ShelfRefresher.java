package com.example.yuejie;
import java.util.*;import java.util.concurrent.*;
/** Serial metadata work uses a separate executor and never writes reading positions. */
final class ShelfRefresher implements AutoCloseable {
 interface Source {void refresh(Book book)throws Exception;}
 interface Listener {void changed();void complete(int updated,int failed);}
 static final long MAX_AGE=5*60*1000L;
 final LocalLibrary library;final Source source;final ExecutorService worker=Executors.newSingleThreadExecutor();volatile boolean closed;boolean running;
 ShelfRefresher(LocalLibrary library,Source source){this.library=library;this.source=source;}
 synchronized boolean refresh(boolean force,Listener listener){if(closed||running)return false;List<Book> work=new ArrayList<>();long now=System.currentTimeMillis();for(Book b:library.books(true))if(force||now-library.checkedAt(b.id)>=MAX_AGE)work.add(b);if(work.isEmpty())return false;running=true;worker.execute(()->{int updated=0,failed=0;try{for(Book book:work){if(closed)break;if(!library.contains(book.id))continue;try{source.refresh(book);updated++;}catch(Exception e){failed++;}if(!closed)NovelApi.MAIN.post(()->{if(!closed)listener.changed();});}}finally{final int ok=updated,bad=failed;synchronized(this){running=false;}if(!closed)NovelApi.MAIN.post(()->{if(!closed)listener.complete(ok,bad);});}});return true;}
 public synchronized void close(){closed=true;worker.shutdownNow();}
}
