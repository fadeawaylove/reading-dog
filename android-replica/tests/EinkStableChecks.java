package com.example.yuejie;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import java.util.*;import org.json.*;

/** Real Android views and gestures, isolated package, no backend/network access. */
public final class EinkStableChecks extends Instrumentation {
 Activity activity;ReaderSettings settings;ReaderSurface surface;Fixture host;int checks;
 void ui(Runnable work){Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
 void ok(boolean value,String label){if(!value)throw new AssertionError(label);checks++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 void settle(){waitForIdleSync();SystemClock.sleep(180);waitForIdleSync();}
 final class Fixture implements ReaderSurface.Host {
  ReaderPaginator layout;int battery=80;String progress="1/100";Map<Integer,Long> counts=new HashMap<>();
  public ReaderSurface.Frame adjacent(int dir){int p=surface.frame.pageIndex+dir,ch=surface.frame.chapterIndex;if(p<0){ch--;p=layout.pages.size()-1;}else if(p>=layout.pages.size()){ch++;p=0;}return ch<0||ch>1?null:new ReaderSurface.Frame("fixture-"+ch,"测试",ch,2,layout,p);}
  ReaderSurface.Frame frame(int p){return new ReaderSurface.Frame("fixture-0","测试",0,2,layout,p);}
  public void commit(ReaderSurface.Frame f){surface.setFrame(f);}
  public void needChapter(int dir){}public void center(){}public void sized(int w,int h){}public int battery(){return battery;}
  public Map<Integer,Long> commentCounts(String chapter){return counts;}public void paragraph(int start){}public String bookProgress(ReaderSurface.Frame f){return progress;}
 }
 void create(boolean stable,boolean picture)throws Exception{
  if(surface!=null)ui(()->surface.images.close());
  ui(()->{settings=new ReaderSettings(getTargetContext());settings.einkStable=stable;settings.pageTurnMode=stable?PageTurnMode.COVER:PageTurnMode.NONE;settings.comments=true;host=new Fixture();host.counts.put(0,5L);
   StringBuilder text=new StringBuilder();if(picture)text.append('\ufffc');for(int i=0;i<180;i++)text.append("这是连续阅读测试文字，确保翻页没有丢失内容。\n\n");
   try{JSONArray images=picture?new JSONArray().put(new JSONObject().put("offset",0).put("url","").put("width",100).put("height",50)):null;host.layout=new ReaderPaginator(text.toString(),"测试",360,640,1,1,settings,host.counts,images);}catch(Exception e){throw new RuntimeException(e);}
   surface=new ReaderSurface(activity,settings,host,(c,u,f)->{throw new AssertionError("No network allowed");});activity.setContentView(surface);surface.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));surface.layout(0,0,360,640);surface.setFrame(host.frame(0));
  });settle();
 }
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  activity=startActivitySync(new Intent(getTargetContext(),ImageFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  create(true,false);ok(surface.activeMode==PageTurnMode.NONE,"automatic eink policy overrides animation without rewriting saved mode");
  ReaderPageView old=surface.pages[1];long gen=old.generation;int draws=surface.sceneDraws;
  ui(()->{host.battery=42;host.progress="2/200";host.counts.put(0,99L);surface.invalidate();surface.invalidate();surface.warmNow();});settle();
  ok(old.generation==gen&&old.battery==80&&old.counts.get(0)==5L,"idle metadata and comment arrivals leave current snapshot untouched");
  ok(surface.sceneDraws==draws,"idle metadata and neighbor preparation produce no scene draw");
  ui(()->{long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,surface.getWidth()*.9f,surface.getHeight()*.55f,0);surface.onTouchEvent(down);down.recycle();});settle();
  ok(old.generation==gen,"touch down does not redraw old page for pending metadata");
  ui(()->{long now=SystemClock.uptimeMillis();MotionEvent up=MotionEvent.obtain(now,now,MotionEvent.ACTION_UP,surface.getWidth()*.9f,surface.getHeight()*.55f,0);surface.onTouchEvent(up);up.recycle();});settle();
  ok(surface.frame.pageIndex==1&&surface.pages[1].battery==42&&surface.pages[1].progress.equals("2/200"),"page commit adopts latest metadata on prewarmed page");
  ok(surface.pages[1].counts.get(0)==99L&&surface.frame.layout==host.layout,"count refresh preserves chapter layout and anchors");
  ui(()->{for(int i=0;i<20;i++)surface.turn(1);});settle();
  ok(surface.frame.pageIndex==21&&surface.commits==21&&surface.animationFrames==0,"21 forward turns are immediate and preserve page order");
  ui(()->{for(int i=0;i<21;i++)surface.turn(-1);});settle();ok(surface.frame.pageIndex==0,"reverse turns preserve starting anchor");
  ui(()->{surface.setFrame(host.frame(host.layout.pages.size()-1));surface.turn(1);});settle();ok(surface.frame.chapterIndex==1&&surface.frame.pageIndex==0,"prepared next chapter commits without intermediate loading frame");
  ui(()->surface.turn(-1));settle();ok(surface.frame.chapterIndex==0&&surface.frame.pageIndex==host.layout.pages.size()-1,"reverse chapter crossing preserves last-page anchor");
  gen=surface.pages[1].generation;draws=surface.sceneDraws;ui(()->{surface.imagesChanged();surface.imagesChanged();});settle();
  ok(surface.pages[1].generation==gen&&surface.sceneDraws==draws,"unrelated image completion never redraws text-only current page");
  ui(()->{settings.night=true;surface.invalidatePageImages();});settle();ok(surface.pages[1].night,"explicit theme change stays immediate");
  ui(()->{surface.suspendRendering();host.battery=20;surface.invalidate();surface.resumeRendering();});settle();ok(surface.pages[1].battery==20,"resume refreshes snapshot");
  create(false,false);gen=surface.pages[1].generation;ui(()->{host.battery=33;surface.invalidate();});settle();ok(surface.pages[1].generation>gen&&surface.pages[1].battery==33,"ordinary phone retains live metadata behavior");
  create(true,true);gen=surface.pages[1].generation;ui(()->{host.battery=11;surface.pages[1].imageStates.put(0,new ReaderImages.State(null,"加载图片…"));surface.imagesChanged();surface.imagesChanged();});settle();
  ok(surface.pages[1].generation==gen+1&&surface.pages[1].imageStates.get(0).label.equals("图片地址不可用"),"necessary image error updates coalesce once");
  ok(surface.pages[1].battery==80,"image completion does not unfreeze unrelated metadata");
  gen=surface.pages[1].generation;ui(()->{ReaderImages.MEMORY.put("",Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888));surface.imagesChanged();});settle();
  ok(surface.pages[1].generation==gen+1&&surface.pages[1].imageStates.get(0).bitmap!=null,"completed image replaces error without requiring a page turn");
  ui(()->ReaderImages.MEMORY.remove(""));
  result.putString("stream","PASS "+checks+" eink rendering checks (not physical panel acceptance)\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}finally{if(activity!=null)ui(()->{if(surface!=null)surface.images.close();activity.finish();});}}
}
