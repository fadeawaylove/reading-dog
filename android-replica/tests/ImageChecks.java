package com.example.yuejie;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;

/** Deterministic tests use real Android layout, disk/bitmap code and gestures; no upstream network. */
public final class ImageChecks extends Instrumentation {
 long gestureTime;int checks;Activity activity;Context context;ReaderSettings settings;byte[] png;String prefix="https://p3.byteimg.com/fixture-"+System.nanoTime()+"/";
 void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);checks++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 void ui(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
 interface Condition{boolean ready();}
 void await(Condition condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!condition.ready()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);if(!condition.ready())throw new AssertionError("Timed out");}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  context=getTargetContext();ui(()->settings=new ReaderSettings(context));Bitmap b=Bitmap.createBitmap(24,32,Bitmap.Config.ARGB_8888);b.eraseColor(0xff249966);b.setPixel(0,0,Color.TRANSPARENT);ByteArrayOutputStream bytes=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,bytes);png=bytes.toByteArray();b.recycle();
  metadata();pagination();pureTextParity();cache();async();gestures();result.putString("stream","PASS "+checks+" body image checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}finally{if(activity!=null)ui(()->activity.finish());}}
 JSONObject image(int offset,int width,int height)throws Exception{return new JSONObject().put("offset",offset).put("url",prefix+offset).put("width",width).put("height",height);}
 JSONObject body(String text)throws Exception{return new JSONObject().put("id","456").put("text",text).put("paragraph_mapping",true).put("paragraph_meta",new JSONArray().put(new JSONObject().put("id","1").put("start",0).put("end",text.length()).put("text",text)));}
 void metadata()throws Exception{
  String text="中😀A\ufffcB\ufffc";JSONArray images=new JSONArray().put(image(4,800,1200)).put(image(6,0,0)).put(image(1,2,2));Map<Integer,BodyImages.Image> parsed=BodyImages.parse(text,images);
  ok(parsed.size()==2&&parsed.get(4).width==800,"UTF-16 image positions validate against canonical text");
  LocalLibrary library=new LocalLibrary(context);try{JSONObject old=body(text),fresh=new JSONObject(old.toString()).put("image_version",1).put("images",images);library.chapter("123","456",old);if(!library.bookmark("123","456",4))library.toggleBookmark("123","456",4,"image");library.progress("123","456",4,0,1,"image");NovelApi api=new NovelApi(context,library);AtomicInteger calls=new AtomicInteger();api.chapterFetcher=(book,id)->{calls.incrementAndGet();return fresh;};JSONObject result=api.chapter("123","456");ok(!BodyImages.needsUpgrade(result)&&calls.get()==1&&BodyImages.sameBody(old,result),"old cache upgrades atomically without shifting body or paragraph identities");ok(library.progress("123").getInt("offset")==4&&library.bookmark("123","456",4),"image metadata upgrade preserves saved progress and bookmark");ok(LibrarySync.pending(library.getReadableDatabase()).getJSONObject(0).getJSONObject("value").getInt("offset")==4,"queued multi-device progress retains original UTF-16 image offset");api.chapterFetcher=(book,id)->{throw new IOException("offline");};ok(!BodyImages.needsUpgrade(api.chapter("123","456")),"upgraded body available offline");
   library.chapter("123","456",old);result=api.chapter("123","456");ok(result.has("image_notice")&&BodyImages.needsUpgrade(library.chapter("123","456")),"offline legacy body retained for later retry");
   JSONObject changed=body("changed\ufffc").put("image_version",1).put("images",new JSONArray());api.chapterFetcher=(book,id)->changed;result=api.chapter("123","456");ok(result.optBoolean("image_refresh")&&library.chapter("123","456").getString("text").equals(text),"changed upstream body requires explicit refresh");result=api.chapter("123","456",true);ok(result.getString("text").equals("changed\ufffc"),"confirmed refresh can replace changed body");
   library.chapter("123","456",old);JSONObject moved=new JSONObject(fresh.toString());moved.getJSONArray("paragraph_meta").getJSONObject(0).put("id","2");api.chapterFetcher=(book,id)->moved;ok(api.chapter("123","456").optBoolean("image_refresh"),"changed paragraph mapping cannot silently replace cached body");
  }finally{library.close();}LocalLibrary reopened=new LocalLibrary(context);try{ok(reopened.progress("123").getInt("offset")==4&&reopened.bookmark("123","456",4),"progress and image bookmark survive database reopen");}finally{reopened.close();}
 }
 ReaderPaginator layout(String text,JSONArray images,int height,Map<Integer,Long> comments){return new ReaderPaginator(text,"图片测试",360,height,1,1,settings,comments,images);}
 void pagination()throws Exception{
  String text="前😀文\ufffc图片后文字。\ufffc\ufffc\n\n末段。";JSONArray images=new JSONArray();for(int i=0;i<text.length();i++)if(text.charAt(i)=='\ufffc')images.put(image(i,i==4?100:0,i==4?10000:0));Map<Integer,Long> counts=Collections.singletonMap(0,3L);
  ReaderPaginator p=layout(text,images,420,counts);int last=0,n=0,bubbles=0;
  for(int i=0;i<p.pages.size();i++){ReaderPaginator.Page page=p.pages.get(i);ok(page.start==last&&page.end>=page.start,"contiguous image page "+i);last=page.end;for(ReaderPaginator.Fragment f:page.fragments){if(f.image!=null){n++;RectF r=p.imageRect(f);ok(r.top>=p.top&&r.bottom<=p.bottom+.01f,"whole image fits body bounds");ok(p.locate(f.image.offset)==i,"image offset locates its page");}else ok(f.layout.text.indexOf('\ufffc')<0,"object marker never enters text drawing");if(p.commentBubble(f)!=null)bubbles++;}}
  ok(last==text.length()&&n==3&&bubbles==1,"all images and one paragraph comment survive mixed pagination");
  int[] starts=new int[p.pages.size()];for(int i=0;i<starts.length;i++)starts[i]=p.anchorForPage(i);ReaderImages.MEMORY.put(prefix+"loaded",Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888));ReaderPaginator again=layout(text,images,420,counts);ok(again.pages.size()==p.pages.size(),"image arrival cannot change page count");for(int i=0;i<starts.length;i++)ok(starts[i]==again.anchorForPage(i),"image arrival preserves page anchor "+i);
  ReaderPaginator legacy=layout("A\ufffcB",null,420,Collections.emptyMap());ok(legacy.pages.stream().flatMap(page->page.fragments.stream()).anyMatch(f->f.image!=null&&f.image.url.isEmpty()),"missing image metadata becomes an explicit placeholder");
  settings.font=60;ReaderPaginator large=layout(text,images,420,counts);ok(large.pages.get(large.pages.size()-1).end==text.length(),"large fonts retain complete body");settings.font=20;
 }
 int pageField(Object page,String name)throws Exception{java.lang.reflect.Field f=page.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(page);}
 void pureTextParity()throws Exception{
  Class<?> baseline=Class.forName("com.example.yuejie.LegacyReaderPaginator");java.lang.reflect.Constructor<?> constructor=baseline.getDeclaredConstructor(String.class,String.class,int.class,int.class,float.class,float.class,ReaderSettings.class,Map.class);constructor.setAccessible(true);java.lang.reflect.Field pagesField=baseline.getDeclaredField("pages");pagesField.setAccessible(true);java.lang.reflect.Method anchor=baseline.getDeclaredMethod("anchorForPage",int.class);anchor.setAccessible(true);
  String text="中文标点，“引号”与（括号）；😀 emoji、English words and extraordinarilylongword.\n\n第二段内容重复用于跨页，保持原有书签与段落身份。";text=text+text+text;
  for(int font:new int[]{16,20,30,60})for(int width:new int[]{240,360,720})for(int height:new int[]{320,800})for(boolean commented:new boolean[]{false,true}){
   settings.font=font;Map<Integer,Long> counts=commented?Collections.singletonMap(0,10L):Collections.emptyMap();ReaderPaginator now=new ReaderPaginator(text,"测试标题",width,height,1,1,settings,counts);Object before=constructor.newInstance(text,"测试标题",width,height,1f,1f,settings,counts);List<?> beforePages=(List<?>)pagesField.get(before);
   if(now.pages.size()!=beforePages.size())throw new AssertionError("Pure page count "+font+"/"+width+"/"+height);
   for(int i=0;i<now.pages.size();i++)if(now.pages.get(i).start!=pageField(beforePages.get(i),"start")||now.pages.get(i).end!=pageField(beforePages.get(i),"end")||now.anchorForPage(i)!=(Integer)anchor.invoke(before,i))throw new AssertionError("Pure anchor drift");
  }
  settings.font=20;ok(true,"96 pure-text font/viewport/comment combinations match pre-change pagination");
 }
 void write(File file)throws Exception{try(OutputStream out=new FileOutputStream(file)){out.write(png);}}
 void cache()throws Exception{
  AtomicInteger calls=new AtomicInteger();ReaderImages.Transfer transfer=(c,u,file)->{calls.incrementAndGet();write(file);};String url=prefix+"raw";ReaderImages.load(context,url,transfer);File file=new File(new File(context.getCacheDir(),"body-images"),ReaderImages.key(url));byte[] original;try(InputStream in=new FileInputStream(file)){original=ProtocolCrypto.read(in);}ok(Arrays.equals(original,png),"disk preserves original PNG bytes and transparency");
  Bitmap cached=ReaderImages.load(context,url,(c,u,f)->{throw new IOException("offline");});ok(calls.get()==1&&Color.alpha(cached.getPixel(0,0))==0,"disk cache decodes offline without losing alpha");
  try(OutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2,3});}ReaderImages.load(context,url,transfer);ok(calls.get()==2,"corrupt disk image is downloaded again");
  boolean rejected=false;try{ReaderImages.load(context,prefix+"bad",(c,u,f)->{try(OutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3});}});}catch(IOException e){rejected=true;}ok(rejected&&!new File(file.getParentFile(),ReaderImages.key(prefix+"bad")).exists(),"invalid image never commits corrupt cache");
 }
 void async()throws Exception{
  AtomicInteger calls=new AtomicInteger(),callbacks=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ReaderImages[] repos=new ReaderImages[2];String url=prefix+"dedup";
  ReaderImages.Transfer transfer=(c,u,file)->{calls.incrementAndGet();entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IOException();write(file);};
  ui(()->{repos[0]=new ReaderImages(context,callbacks::incrementAndGet,transfer);repos[1]=new ReaderImages(context,callbacks::incrementAndGet,transfer);repos[0].request(url,false);repos[1].request(url,false);});ok(entered.await(5,TimeUnit.SECONDS),"image request starts asynchronously");ui(()->repos[0].close());release.countDown();await(()->callbacks.get()==1);ok(calls.get()==1,"duplicate requests share one transfer and closed readers receive no callback");ui(()->repos[1].close());AtomicInteger hits=new AtomicInteger();ReaderImages[] hit=new ReaderImages[1];ui(()->{hit[0]=new ReaderImages(context,hits::incrementAndGet,transfer);hit[0].request(url,false);});await(()->hits.get()==1);ok(calls.get()==1,"memory-cache hits notify a newly attached viewer without another download");ui(()->hit[0].close());
  ReaderImages[] retry=new ReaderImages[1];AtomicInteger attempts=new AtomicInteger(),done=new AtomicInteger();ui(()->{retry[0]=new ReaderImages(context,done::incrementAndGet,(c,u,f)->{if(attempts.incrementAndGet()==1)throw new IOException("first attempt");write(f);});retry[0].request(prefix+"retry",false);});await(()->done.get()==1);ui(()->retry[0].request(prefix+"retry",false));SystemClock.sleep(150);ok(attempts.get()==1,"failed images do not spin in an automatic retry loop");ui(()->retry[0].request(prefix+"retry",true));await(()->done.get()==2);ok(attempts.get()==2&&retry[0].state(prefix+"retry").bitmap!=null,"explicit retry recovers image");ui(()->retry[0].close());
 }
 void gestures()throws Exception{
  activity=startActivitySync(new Intent().setClassName(context.getPackageName(),ImageFixtureActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  ReaderPaginator p=layout("\ufffc\n\n后文。",new JSONArray().put(image(0,400,300)),700,Collections.emptyMap());AtomicInteger opened=new AtomicInteger(),turns=new AtomicInteger();ReaderSurface[] surfaces=new ReaderSurface[1];CountDownLatch release=new CountDownLatch(1),entered=new CountDownLatch(1);
  ReaderSurface.Host host=new ReaderSurface.Host(){public ReaderSurface.Frame adjacent(int dir){return null;}public void commit(ReaderSurface.Frame f){turns.incrementAndGet();}public void needChapter(int dir){turns.incrementAndGet();}public void center(){}public void sized(int w,int h){}public int battery(){return 100;}public Map<Integer,Long> commentCounts(String id){return Collections.emptyMap();}public void paragraph(int start){}public void image(BodyImages.Image image){opened.incrementAndGet();}};
  ui(()->{ReaderSurface s=new ReaderSurface(activity,settings,host,(c,u,f)->{entered.countDown();release.await(5,TimeUnit.SECONDS);write(f);});surfaces[0]=s;activity.setContentView(s);s.setFrame(new ReaderSurface.Frame("456","fixture",0,1,p,0));});ok(entered.await(5,TimeUnit.SECONDS),"visible page starts image loading");
  ReaderPaginator.Fragment fragment=p.pages.get(0).fragments.stream().filter(f->f.image!=null).findFirst().get();RectF rect=p.imageRect(fragment);ui(()->{long now=SystemClock.uptimeMillis();surfaces[0].onTouchEvent(MotionEvent.obtain(now,now,0,rect.left+1,rect.centerY(),0));surfaces[0].onTouchEvent(MotionEvent.obtain(now,now+30,1,rect.left+1,rect.centerY(),0));});ok(opened.get()==1&&turns.get()==0,"image tap at page edge opens preview instead of turning page");
  ui(()->surfaces[0].dragging=true);release.countDown();await(()->surfaces[0].pendingImageVisuals);ok(surfaces[0].pages[1].imageStates.get(0).bitmap==null,"image completion freezes visual snapshot during drag");ui(()->{surfaces[0].dragging=false;surfaces[0].flushVisuals();});ok(surfaces[0].pages[1].imageStates.get(0).bitmap!=null,"pending image appears after gesture ends");
  Bitmap[] raster={null};ui(()->{ReaderSurface s=surfaces[0];raster[0]=Bitmap.createBitmap(s.getWidth(),s.getHeight(),Bitmap.Config.ARGB_8888);s.paintPage(new Canvas(raster[0]),s.pages[1]);});ok((raster[0].getPixel((int)rect.centerX(),(int)rect.centerY())&0x00ffffff)==0x249966,"actual page canvas paints the downloaded bitmap");try(OutputStream out=new FileOutputStream(new File(context.getFilesDir(),"body-image-page.png"))){raster[0].compress(Bitmap.CompressFormat.PNG,100,out);}raster[0].recycle();
  ReaderImagePreview[] viewer={null};ui(()->{viewer[0]=new ReaderImagePreview(activity,prefix+"0");viewer[0].show();});
  await(()->viewer[0].picture.getWidth()>0&&viewer[0].picture.getHeight()>0&&viewer[0].state.bitmap!=null);
  try{ui(()->{
   ReaderImagePreview preview=viewer[0];ReaderImagePreview.ZoomView view=preview.picture;
   float minimum=minimumSpan(view),slop=ViewConfiguration.get(activity).getScaledTouchSlop()*2f;
   float available=Math.max(view.getWidth(),view.getHeight())*.85f;
   float crossing=minimum+slop*2+1;
   ok(available>crossing*1.2f,"laid-out viewer has room for a density-aware pinch");
   pinch(view,new float[]{minimum*.5f,minimum*.8f},false,false);
   ok(view.zoom==1&&!view.scaler.isInProgress(),"sub-threshold two-finger movement does not zoom");
   // The move crossing minSpan begins recognition; only later moves scale.
   pinch(view,new float[]{minimum*.5f,crossing,crossing*1.2f},true,false);
   ok(view.zoom>1,"movement after crossing the system threshold zooms the preview");
   float enlarged=view.zoom;
   pinch(view,new float[]{available,available*.8f,available*.6f},false,true);
   ok(view.zoom<enlarged&&view.zoom>=1,"inward pinch shrinks the preview without going below fit");
   pinch(view,new float[]{crossing,(crossing+available)/2,available},false,false);
   ok(view.zoom>1&&view.zoom<=8,"complete two-pointer gesture zooms within supported bounds");
   view.dx=10000;view.clamp();ok(view.dx<10000,"preview supports zoom with bounded pan");
   preview.onBackPressed();ok(!preview.isShowing()&&preview.images.closed,"back closes viewer and removes callbacks");
  });}finally{ui(()->viewer[0].dismiss());}
  ui(()->{ReaderSurface s=surfaces[0];settings.pageTurnMode=PageTurnMode.NONE;s.modeChanged();long now=SystemClock.uptimeMillis();float y=rect.centerY();s.onTouchEvent(MotionEvent.obtain(now,now,0,s.getWidth()*.85f,y,0));s.onTouchEvent(MotionEvent.obtain(now,now+300,2,s.getWidth()*.1f,y,0));s.onTouchEvent(MotionEvent.obtain(now,now+600,1,s.getWidth()*.1f,y,0));});ok(turns.get()==1&&opened.get()==1,"swiping across an image keeps the page-turn gesture");
  visual(surfaces[0]);ui(()->surfaces[0].images.close());
 }
 float minimumSpan(View view){
  if(Build.VERSION.SDK_INT>=29)return ViewConfiguration.get(view.getContext()).getScaledMinimumScalingSpan();
  int id=view.getResources().getIdentifier("config_minScalingSpan","dimen","android");
  if(id==0)throw new AssertionError("System scaling threshold unavailable");
  return view.getResources().getDimensionPixelSize(id);
 }
 void touch(ReaderImagePreview.ZoomView view,long down,long time,int action,int count,float span){
  MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
  boolean vertical=view.getHeight()>view.getWidth();
  for(int i=0;i<count;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();float delta=(i==0?-1:1)*span/2;
   coords[i].x=view.getWidth()/2f+(vertical?0:delta);coords[i].y=view.getHeight()/2f+(vertical?delta:0);coords[i].pressure=1;coords[i].size=1;}
  MotionEvent event=MotionEvent.obtain(down,time,action,count,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
  try{view.dispatchTouchEvent(event);}finally{event.recycle();}
 }
 void pinch(ReaderImagePreview.ZoomView view,float[] spans,boolean thresholdCheck,boolean liftFirst){
  long down=Math.max(SystemClock.uptimeMillis(),gestureTime+16),time=down;float span=spans[0];
  touch(view,down,time,MotionEvent.ACTION_DOWN,1,span);
  touch(view,down,time+=16,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,span);
  float before=view.zoom;
  for(int i=1;i<spans.length;i++){span=spans[i];touch(view,down,time+=16,MotionEvent.ACTION_MOVE,2,span);
   if(thresholdCheck&&i==1)ok(view.scaler.isInProgress()&&view.zoom==before,"threshold-crossing move begins recognition before applying scale");}
  float after=view.zoom;
  // Exercise both pointer-lift orders and finish the stream with monotonic times.
  touch(view,down,time+=16,MotionEvent.ACTION_POINTER_UP|((liftFirst?0:1)<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,span);
  float x=view.lastX,y=view.lastY;
  MotionEvent.PointerProperties finger=new MotionEvent.PointerProperties();finger.id=liftFirst?1:0;finger.toolType=MotionEvent.TOOL_TYPE_FINGER;
  MotionEvent.PointerCoords position=new MotionEvent.PointerCoords();position.x=x;position.y=y;position.pressure=1;position.size=1;
  gestureTime=time+16;
  MotionEvent up=MotionEvent.obtain(down,gestureTime,MotionEvent.ACTION_UP,1,new MotionEvent.PointerProperties[]{finger},new MotionEvent.PointerCoords[]{position},0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
  try{view.dispatchTouchEvent(up);}finally{up.recycle();}
  ok(!view.scaler.isInProgress()&&view.zoom==after,"pointer lift ends scaling without resetting zoom");
 }
 void visual(ReaderSurface surface)throws Exception{
  Bitmap source=Bitmap.createBitmap(1200,700,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(source);c.drawColor(0xffdce9de);Paint pen=new Paint(3);pen.setColor(0xff27573d);pen.setTextSize(70);c.drawText("正文图片",80,150,pen);pen.setTextSize(36);c.drawText("原始 PNG · 等比显示 · 点击放大",80,220,pen);pen.setColor(0xff73a88c);c.drawRoundRect(new RectF(80,290,1120,620),28,28,pen);pen.setColor(Color.WHITE);pen.setTextSize(56);c.drawText("图片中的文字也保留下来",140,475,pen);String url=prefix+"visual";ReaderImages.MEMORY.put(url,source);
  String text="这是一段包含配图的正文。\n\n\ufffc\n\n图片加载后，后面的文字和阅读位置保持稳定。";JSONArray values=new JSONArray().put(new JSONObject().put("offset",text.indexOf('\ufffc')).put("url",url).put("width",1200).put("height",700));
  ui(()->{float d=context.getResources().getDisplayMetrics().density,scaled=context.getResources().getDisplayMetrics().scaledDensity;ReaderPaginator layout=new ReaderPaginator(text,"正文图片测试",surface.getWidth(),surface.getHeight(),d,scaled,settings,Collections.emptyMap(),values);surface.setFrame(new ReaderSurface.Frame("visual","正文图片测试",0,1,layout,0));});
  for(boolean night:new boolean[]{false,true}){Bitmap[] raster={null};ui(()->{settings.night=night;surface.refreshVisuals();raster[0]=Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),Bitmap.Config.ARGB_8888);surface.paintPage(new Canvas(raster[0]),surface.pages[1]);});try(OutputStream out=new FileOutputStream(new File(context.getFilesDir(),night?"body-image-night.png":"body-image-day.png"))){raster[0].compress(Bitmap.CompressFormat.PNG,100,out);}raster[0].recycle();}
  ok(true,"day and night page rasters captured at actual device density");
 }

}
