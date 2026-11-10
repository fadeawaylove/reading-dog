package com.example.yuejie;
import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Original-byte disk cache; callbacks never change geometry or hold an Activity. */
final class ReaderImages implements AutoCloseable {
 static final long DISK_LIMIT=64L*1024*1024;
 static final int BYTE_LIMIT=8*1024*1024;
 static final ExecutorService IO=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(48));
 static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(24*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
 // The future is shared across reader surfaces and the preview; repeat URLs perform one download.
 static final Map<String,FutureTask<Bitmap>> REQUESTS=new HashMap<>();
 static final class State {
  final Bitmap bitmap;final String label;
  State(Bitmap bitmap,String label){this.bitmap=bitmap;this.label=label;}
 }
 interface Transfer{void download(Context context,String url,File target)throws Exception;}
 final Context context;final Transfer transfer;final Set<String> pending=new HashSet<>();final LinkedHashMap<String,String> failures=new LinkedHashMap<>();Runnable changed;boolean closed;
 ReaderImages(Context context,Runnable changed){this(context,changed,ReaderImages::download);}
 ReaderImages(Context context,Runnable changed,Transfer transfer){this.context=context.getApplicationContext();this.changed=changed;this.transfer=transfer;}
 State state(String url){Bitmap b=MEMORY.get(url);return new State(b,b!=null?"":failures.containsKey(url)?failures.get(url):url.startsWith("https://")?"加载图片…":"图片地址不可用");}
 State snapshot(String url,RectF rect){State state=state(url);if(state.bitmap==null)return state;Bitmap b=state.bitmap;float scale=Math.min(1,Math.min(rect.width()/b.getWidth(),rect.height()/b.getHeight()));if(scale<1)return new State(Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*scale)),Math.max(1,Math.round(b.getHeight()*scale)),true),"");return state;}
 void request(String url,boolean retry){
  if(closed||!url.startsWith("https://")||pending.contains(url))return;if(retry)failures.remove(url);else if(failures.containsKey(url))return;
  if(MEMORY.get(url)!=null){NovelApi.MAIN.post(()->{if(!closed&&changed!=null)changed.run();});return;}pending.add(url);
  final FutureTask<Bitmap> task;boolean submit=false;
  synchronized(REQUESTS){FutureTask<Bitmap> existing=REQUESTS.get(url);if(existing==null){existing=new FutureTask<>(()->{Bitmap b=load(context,url,transfer);MEMORY.put(url,b);return b;});REQUESTS.put(url,existing);submit=true;}task=existing;}
  // One worker downloads; every observer is notified on the main thread by a short poll.
  if(submit)try{IO.execute(()->{try{task.run();}finally{synchronized(REQUESTS){if(REQUESTS.get(url)==task)REQUESTS.remove(url);}}});}catch(RejectedExecutionException e){synchronized(REQUESTS){REQUESTS.remove(url);}task.cancel(false);}
  observe(url,task);
 }
 void observe(String url,FutureTask<Bitmap> task){
  if(closed)return;
  if(!task.isDone()){NovelApi.MAIN.postDelayed(()->observe(url,task),100);return;}
  pending.remove(url);try{Bitmap bitmap=task.get();if(bitmap==null)throw new IOException();MEMORY.put(url,bitmap);failures.remove(url);}catch(Exception e){failures.put(url,"图片加载失败，点击重试");while(failures.size()>128)failures.remove(failures.keySet().iterator().next());}
  if(changed!=null)changed.run();
 }
 static String key(String url)throws Exception{return ProtocolCrypto.hex(MessageDigest.getInstance("SHA-256").digest(ProtocolCrypto.utf(url)));}
 static Bitmap decode(File file)throws IOException {
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);
  if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("图片格式暂不支持");
  BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
  while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>4096||(long)(bounds.outWidth/options.inSampleSize)*(bounds.outHeight/options.inSampleSize)>4L*1024*1024)options.inSampleSize*=2;
  Bitmap b=BitmapFactory.decodeFile(file.getPath(),options);if(b==null)throw new IOException("图片解码失败");return b;
 }
 static Bitmap load(Context context,String url)throws Exception {
  return load(context,url,ReaderImages::download);
 }
 static Bitmap load(Context context,String url,Transfer transfer)throws Exception {
  if(!url.startsWith("https://"))throw new IOException("图片地址无效");
  File folder=new File(context.getCacheDir(),"body-images");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建图片缓存");File[] abandoned=folder.listFiles(f->f.getName().endsWith(".part")&&f.lastModified()<System.currentTimeMillis()-300000);if(abandoned!=null)for(File f:abandoned)f.delete();File file=new File(folder,key(url));
  if(file.isFile()){try{Bitmap b=decode(file);file.setLastModified(System.currentTimeMillis());return b;}catch(IOException e){file.delete();}}
  File temp=File.createTempFile("image-",".part",folder);
  try{
   transfer.download(context,url,temp);if(temp.length()>BYTE_LIMIT)throw new IOException("图片过大");
   Bitmap b=decode(temp);
   synchronized(ReaderImages.class){if(!temp.renameTo(file))throw new IOException("图片缓存写入失败");trim(folder);}
   return b;
  }finally{temp.delete();}
 }
 static void download(Context context,String url,File temp)throws Exception {
  byte[] bytes=UpstreamImages.read(url);try(OutputStream out=new FileOutputStream(temp)){out.write(bytes);}
 }
 static void trim(File folder){File[] files=folder.listFiles(f->!f.getName().endsWith(".part"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=0;for(File f:files)size+=f.length();for(File f:files){if(size<=DISK_LIMIT)break;long n=f.length();if(f.delete())size-=n;}}
 static void draw(Canvas canvas,RectF rect,State state,int ink){
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  if(state!=null&&state.bitmap!=null){Bitmap b=state.bitmap;float scale=Math.min(rect.width()/b.getWidth(),rect.height()/b.getHeight());float w=b.getWidth()*scale,h=b.getHeight()*scale;canvas.drawBitmap(b,null,new RectF(rect.centerX()-w/2,rect.centerY()-h/2,rect.centerX()+w/2,rect.centerY()+h/2),p);return;}
  p.setColor(ink);p.setAlpha(18);canvas.drawRoundRect(rect,6,6,p);p.setAlpha(255);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.min(14*Math.max(1,canvas.getDensity()/160f),rect.width()/16));String label=state==null?"加载图片…":state.label;canvas.drawText(label,rect.centerX(),rect.centerY()-(p.ascent()+p.descent())/2,p);
 }
 public void close(){closed=true;changed=null;pending.clear();}
}
