package com.example.yuejie;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;import java.net.*;import java.io.*;
final class PaperUi {
 static final int PAPER=0xfff8f7f2,INK=0xff252d29,MUTED=0xff71786f,GREEN=0xff3e624f,SOFT=0xffedf0e7,RULE=0xffe4e6dd;
 static final Typeface SERIF=serif();static Typeface serif(){try{return Typeface.createFromFile("/system/fonts/NotoSerifCJK-Regular.ttc");}catch(Exception e){return Typeface.create("serif",0);}}
 static int dp(Context c,float v){return (int)(v*c.getResources().getDisplayMetrics().density+.5f);}
 static GradientDrawable bg(Context c,int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 static LinearLayout col(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 static TextView text(Context c,String s,float size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
 static TextView heading(Context c,String s,float size){TextView t=text(c,s,size,INK);t.setTypeface(SERIF);t.setLetterSpacing(.035f);return t;}
 static void gap(LinearLayout l,int h){l.addView(new View(l.getContext()),new LinearLayout.LayoutParams(1,dp(l.getContext(),h)));}
 static void rule(LinearLayout l){View v=new View(l.getContext());v.setBackgroundColor(RULE);l.addView(v,new LinearLayout.LayoutParams(-1,dp(l.getContext(),1)));}
 static TextView button(Context c,String s,Runnable action){TextView t=text(c,s,13,GREEN);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(c,46));t.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));t.setBackground(bg(c,SOFT,10));t.setOnClickListener(v->action.run());return t;}
 static TextView eyebrow(Context c,String s){TextView t=text(c,s,10,MUTED);t.setLetterSpacing(.16f);return t;}
 static void bars(Activity a,boolean night){int paper=night?0xff202722:PAPER;a.getWindow().setStatusBarColor(paper);a.getWindow().setNavigationBarColor(paper);a.getWindow().getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(android.os.Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
 static ImageView icon(Context c,String name,int color,int box,Runnable action){ImageView v=new ImageView(c);v.setImageDrawable(new LineIcon(name,color));int p=dp(c,(box-20)/2f);v.setPadding(p,p,p,p);v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,box),dp(c,box)));if(action!=null)v.setOnClickListener(w->action.run());v.setContentDescription(name);return v;}
 static class LineIcon extends Drawable {
 final String name;final Paint p=new Paint(3);LineIcon(String n,int color){name=n;p.setColor(color);p.setStrokeWidth(1.5f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
 void line(Canvas c,float... pts){Path x=new Path();x.moveTo(pts[0],pts[1]);for(int i=2;i<pts.length;i+=2)x.lineTo(pts[i],pts[i+1]);c.drawPath(x,p);}
 public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);switch(name){
 case "搜索":c.drawCircle(10,10,6,p);line(c,15,15,21,21);break;case "返回":line(c,14,5,7,12,14,19);break;case "下一页":line(c,9,5,16,12,9,19);break;
 case "发现":c.drawCircle(12,12,9,p);line(c,15,8,13,14,8,16,10,10,15,8);break;case "书架":line(c,4,4,4,21,21,21);line(c,8,5,8,17);line(c,12,3,12,17);line(c,17,5,20,17);break;
 case "目录":for(int y:new int[]{6,12,18}){c.drawCircle(4,y,.5f,p);line(c,8,y,21,y);}break;
 case "夜间":Path moon=new Path();moon.moveTo(20,15);moon.cubicTo(10,19,4,9,11,3);moon.cubicTo(-1,4,1,22,13,22);moon.cubicTo(17,22,20,19,20,15);c.drawPath(moon,p);break;
 case "日间":c.drawCircle(12,12,5,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10);}break;
 case "书签":case "已标记":line(c,6,3,18,3,18,22,12,17,6,22,6,3);if(name.equals("已标记")){Paint f=new Paint(p);f.setStyle(Paint.Style.FILL);c.drawRect(7,4,17,16,f);}break;
 case "排序":line(c,3,6,21,6);line(c,3,12,21,12);line(c,3,18,21,18);c.drawCircle(8,6,2,p);c.drawCircle(16,12,2,p);c.drawCircle(10,18,2,p);break;
 case "关闭":line(c,6,6,18,18);line(c,18,6,6,18);break;
 case "关于阅读狗":c.drawCircle(12,12,9,p);line(c,12,10,12,17);c.drawCircle(12,7,.5f,p);break;
 default:line(c,5,10,3,4,9,6,15,6,21,4,19,12,19,18,14,22,9,22,5,18,5,10);c.drawCircle(9,12,.7f,p);c.drawCircle(15,12,.7f,p);line(c,10,16,14,16,12,18,10,16);}
 c.restore();}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}


 static final android.util.LruCache<String,Bitmap> COVERS=new android.util.LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
 static final java.util.concurrent.LinkedBlockingDeque<Runnable> COVER_QUEUE=new java.util.concurrent.LinkedBlockingDeque<>();
 static final java.util.concurrent.ThreadPoolExecutor COVER_IO=new java.util.concurrent.ThreadPoolExecutor(3,3,0,java.util.concurrent.TimeUnit.SECONDS,COVER_QUEUE);
 static final java.util.Map<String,java.util.List<java.lang.ref.WeakReference<ImageView>>> WAITING=new java.util.HashMap<>();
 static final java.util.Map<String,Runnable> COVER_TASKS=new java.util.HashMap<>();
 static {COVER_IO.prestartAllCoreThreads();}
 static String coverKey(String url){int q=url.indexOf('?');return q<0?url:url.substring(0,q);}
 static Bitmap loadCover(Context context,String url,String key)throws Exception{
  File folder=new File(context.getCacheDir(),"paper-covers");folder.mkdirs();File file=new File(folder,ProtocolCrypto.hex(java.security.MessageDigest.getInstance("SHA-256").digest(ProtocolCrypto.utf(key)))+".jpg");
  if(file.isFile()){Bitmap cached=BitmapFactory.decodeFile(file.getPath());if(cached!=null){file.setLastModified(System.currentTimeMillis());return cached;}file.delete();}
  HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();con.setConnectTimeout(7000);con.setReadTimeout(9000);Bitmap result;
  try{con.setRequestProperty("User-Agent","Mozilla/5.0");byte[] bytes;try(InputStream in=con.getInputStream()){bytes=ProtocolCrypto.read(in);}BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;result=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}finally{con.disconnect();}
  if(result==null)throw new IOException("Unsupported cover image");
  synchronized(COVER_TASKS){try(OutputStream out=new FileOutputStream(file)){result.compress(Bitmap.CompressFormat.JPEG,90,out);}File[] files=folder.listFiles();if(files!=null){java.util.Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));long total=0;for(File f:files)total+=f.length();for(File f:files){if(total<=16L*1024*1024)break;long size=f.length();if(f.delete())total-=size;}}}
  return result;
 }
 static void requestCover(Context context,String url,ImageView image,boolean priority){String key=coverKey(url);Bitmap cached=COVERS.get(key);if(cached!=null){image.setImageBitmap(cached);return;}
  synchronized(WAITING){java.util.List<java.lang.ref.WeakReference<ImageView>> views=WAITING.get(key);if(views!=null){views.add(new java.lang.ref.WeakReference<>(image));Runnable queued=COVER_TASKS.get(key);if(queued!=null&&COVER_QUEUE.remove(queued))COVER_QUEUE.offerFirst(queued);return;}views=new java.util.ArrayList<>();views.add(new java.lang.ref.WeakReference<>(image));WAITING.put(key,views);
   Runnable task=()->{Bitmap loaded=null;try{loaded=loadCover(context.getApplicationContext(),url,key);COVERS.put(key,loaded);}catch(Exception e){android.util.Log.w("ReadingDogCover",e.getClass().getSimpleName()+": "+e.getMessage());}final Bitmap bitmap=loaded;final java.util.List<java.lang.ref.WeakReference<ImageView>> listeners;synchronized(WAITING){listeners=WAITING.remove(key);COVER_TASKS.remove(key);}if(bitmap!=null&&listeners!=null)NovelApi.MAIN.post(()->{for(java.lang.ref.WeakReference<ImageView> ref:listeners){ImageView v=ref.get();if(v!=null)v.setImageBitmap(bitmap);}});};COVER_TASKS.put(key,task);if(priority)COVER_QUEUE.offerFirst(task);else COVER_QUEUE.offerLast(task);
  }
 }
 static View cover(Context c,Book b,int width){FrameLayout frame=new FrameLayout(c);frame.setLayoutParams(new LinearLayout.LayoutParams(dp(c,width),dp(c,width/.7f)));frame.setBackground(bg(c,0xffdce3d7,4));frame.setClipToOutline(true);TextView fallback=heading(c,b.title,Math.max(13,width/5f));fallback.setTextColor(GREEN);fallback.setPadding(dp(c,9),dp(c,12),dp(c,7),dp(c,9));fallback.setMaxLines(4);frame.addView(fallback,new FrameLayout.LayoutParams(-1,-1));ImageView im=new ImageView(c);im.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(im,new FrameLayout.LayoutParams(-1,-1));frame.setContentDescription(b.title+"封面");if(b.cover.startsWith("https://"))requestCover(c,b.cover,im,width>=76);return frame;}
}
