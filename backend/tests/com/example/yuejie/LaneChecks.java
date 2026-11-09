package com.example.yuejie;
import java.util.*;import java.util.concurrent.*;
public final class LaneChecks {
 public static void main(String[] args)throws Exception{RequestLanes lanes=new RequestLanes();ExecutorService threads=Executors.newFixedThreadPool(4);List<String> order=Collections.synchronizedList(new ArrayList<>());
  try(RequestLanes.Lease slow=lanes.enter(true,2)){
   Future<?> body=threads.submit(()->{try(RequestLanes.Lease ignored=lanes.enter(false,0)){order.add("body");}catch(Exception e){throw new RuntimeException(e);}});body.get(1,TimeUnit.SECONDS);
   CountDownLatch queued=new CountDownLatch(2);
   Future<?> nearby=threads.submit(()->{queued.countDown();try(RequestLanes.Lease ignored=lanes.enter(true,2)){order.add("nearby");}catch(Exception e){throw new RuntimeException(e);}});
   Future<?> user=threads.submit(()->{queued.countDown();try(RequestLanes.Lease ignored=lanes.enter(true,0)){order.add("user");}catch(Exception e){throw new RuntimeException(e);}});
   if(!queued.await(1,TimeUnit.SECONDS))throw new AssertionError("workers not queued");
   long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);for(;;){lanes.comments.lock.lock();int size;try{size=lanes.comments.waiting.size();}finally{lanes.comments.lock.unlock();}if(size==2)break;if(System.nanoTime()>until)throw new AssertionError("lane queue did not fill");Thread.yield();}
   slow.close();user.get(1,TimeUnit.SECONDS);nearby.get(1,TimeUnit.SECONDS);
   if(!order.equals(Arrays.asList("body","user","nearby")))throw new AssertionError(order);
  }finally{threads.shutdownNow();}
  System.out.println("PASS body proceeds during slow comment; user overtakes queued lookahead");
  java.nio.file.Path folder=java.nio.file.Files.createTempDirectory("comment-lane-profiles-");ExecutorService bootstrap=Executors.newFixedThreadPool(2);CountDownLatch registering=new CountDownLatch(1),release=new CountDownLatch(1);
  try{
   NovelApi commentApi=new NovelApi(new BackendContext(folder.resolve("comments"),java.nio.file.Path.of("backend/assets")),new LocalLibrary(),(url,headers,body)->{registering.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("registration timeout");return "{\"install_id_str\":\"10001\",\"device_id_str\":\"10002\"}";});
   NovelApi bodyApi=new NovelApi(new BackendContext(folder.resolve("reading"),java.nio.file.Path.of("backend/assets")),new LocalLibrary(),(url,headers,body)->"{\"install_id_str\":\"20001\",\"device_id_str\":\"20002\"}");
   Future<?> slowProfile=bootstrap.submit(()->{try{commentApi.profile("content");}catch(Exception e){throw new RuntimeException(e);}});if(!registering.await(1,TimeUnit.SECONDS))throw new AssertionError("comment bootstrap did not start");
   Future<?> bodyProfile=bootstrap.submit(()->{try{bodyApi.profile("content");}catch(Exception e){throw new RuntimeException(e);}});bodyProfile.get(1,TimeUnit.SECONDS);release.countDown();slowProfile.get(1,TimeUnit.SECONDS);
   System.out.println("PASS slow comment identity registration does not block reading identity");
  }finally{release.countDown();bootstrap.shutdownNow();}
 }
}
