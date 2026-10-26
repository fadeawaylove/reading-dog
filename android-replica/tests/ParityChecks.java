package com.example.yuejie;
import android.app.*;import android.os.*;import android.view.*;import org.json.*;import java.io.*;
/** Actual view traversal regression: unchanged NONE drags must not schedule drawing. */
public final class ParityChecks extends TurnChecks {
 public void onStart(){output=new File(getTargetContext().getFilesDir(),"turn-parity-evidence");output.mkdirs();Bundle result=new Bundle();Throwable failure=null;try{originalPrefs=new JSONObject(getTargetContext().getSharedPreferences("ReaderActivity",0).getAll());db=new LocalLibrary(getTargetContext());originalPosition=db.progress(book);launchReader();noneDraws();pool();thresholds();freeze();geometry();metrics();result.putString("stream","PASS\n");}catch(Throwable e){failure=e;result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));}finally{try{if(reader!=null)ui(()->reader.finish());if(originalPrefs!=null)restorePrefs(originalPrefs);if(originalPosition!=null)db.progress(book,originalPosition.optString("chapter"),originalPosition.optInt("offset"),originalPosition.optInt("index"),originalPosition.optInt("total"),originalPosition.optString("title"));try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(output,"results.json")),"UTF-8")){w.write(new JSONObject().put("success",failure==null).put("checks",checks).put("failure",failure==null?JSONObject.NULL:android.util.Log.getStackTraceString(failure)).toString(2));}}catch(Exception e){result.putString("stream",e.toString());}}finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
 void noneDraws()throws Exception{select(PageTurnMode.NONE);page(2);SystemClock.sleep(500);final int[] draws={0};ViewTreeObserver.OnDrawListener listener=()->draws[0]++;ui(()->reader.surface.getViewTreeObserver().addOnDrawListener(listener));int before=reader.anchor;float w=reader.surface.getWidth(),h=reader.surface.getHeight();hold(w*.85f,w*.7f,h*.72f);SystemClock.sleep(100);for(int i=0;i<6;i++){final float x=w*(.69f-i*.01f);ui(()->{long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_MOVE,x,h*.72f,0);reader.surface.dispatchTouchEvent(e);e.recycle();});SystemClock.sleep(45);}int result=draws[0];ui(()->reader.surface.getViewTreeObserver().removeOnDrawListener(listener));cancelHold();record("none_cancel_state",new JSONObject().put("before",before).put("after",reader.anchor).put("page",reader.surface.frame.pageIndex).put("mode",reader.surface.activeMode.key).put("commits",reader.surface.commits).put("cancels",reader.surface.cancels));assertTrue(reader.anchor==before,"NONE drag does not commit");record("none_drag_actual_view_traversals",result);assertTrue(result==0,"NONE unchanged drag must not request draw, actual="+result);}

 void pool()throws Exception{
  select(PageTurnMode.NONE);page(2);waitFor(()->!reader.surface.whitePage,3,"initial page revealed");
  ReaderPageView previous=reader.surface.pages[0],current=reader.surface.pages[1],next=reader.surface.pages[2];int offset=reader.anchor;
  noneWhiteTurn(1);
  assertTrue(reader.surface.getChildCount()==3&&reader.surface.pages[1]==next&&reader.surface.pages[0]==current,"next rotates native page objects");
  noneWhiteTurn(-1);
  assertTrue(reader.surface.getChildCount()==3&&reader.surface.pages[1]==current&&reader.surface.pages[0]==previous&&reader.surface.pages[2]==next&&reader.anchor==offset,"previous reuses same three objects and original offset");
  record("three_native_pages_rotate_without_recreation",true);record("none_white_then_target_page",true);
 }
 void noneWhiteTurn(int direction)throws Exception{
  final ReaderSurface surface=reader.surface;final int[] requests=new int[2],draws=new int[2],page=new int[2];final boolean[] blank={false,false};final ReaderSurface.Frame[] target={null};final android.graphics.Bitmap[] images=new android.graphics.Bitmap[3];
  try{
   ui(()->{
    requests[0]=surface.sceneRequests;page[0]=surface.frame.pageIndex;surface.turn(direction);
    target[0]=surface.frame;page[1]=surface.frame.pageIndex;blank[0]=surface.whitePage;
    draws[0]=surface.pages[1].contentDraws;
    images[0]=android.graphics.Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
    surface.draw(new android.graphics.Canvas(images[0]));
    draws[1]=surface.pages[1].contentDraws;requests[1]=surface.sceneRequests;blank[1]=surface.whitePageScheduled;
   });
   assertTrue(page[1]==page[0]+direction,"NONE commits exactly one target page");
   assertTrue(blank[0]&&blank[1]&&requests[1]>requests[0],"NONE schedules white phase and delayed reveal");
   assertTrue(draws[1]==draws[0],"white phase does not draw target text");
   int[] pixels=new int[images[0].getWidth()*images[0].getHeight()];images[0].getPixels(pixels,0,images[0].getWidth(),0,0,images[0].getWidth(),images[0].getHeight());for(int pixel:pixels)assertTrue(pixel==android.graphics.Color.WHITE,"white phase clears entire page");
   waitFor(()->!surface.whitePage&&!surface.whitePageScheduled,3,"NONE target automatically revealed");
   ui(()->{
    images[1]=android.graphics.Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
    images[2]=android.graphics.Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
    surface.draw(new android.graphics.Canvas(images[1]));surface.drawFrame(new android.graphics.Canvas(images[2]),target[0]);
   });
   assertTrue(surface.sceneRequests>requests[1],"reveal requests a new scene");
   assertTrue(surface.frame==target[0]&&images[1].sameAs(images[2]),"revealed raster is the committed target page");
   assertTrue(surface.pages[1].contentDraws>draws[1],"reveal draws target content");
   record("none_white_reveal_"+(direction>0?"forward":"backward"),new JSONObject().put("white_phase_requests",requests[1]-requests[0]).put("total_requests",surface.sceneRequests-requests[0]).put("white_and_target_rasters",true));
  }finally{for(android.graphics.Bitmap image:images)if(image!=null)image.recycle();}
 }

 void slow(float fraction)throws Exception{float w=reader.surface.getWidth(),h=reader.surface.getHeight();ui(()->{long t=SystemClock.uptimeMillis();for(int i=0;i<4;i++){int action=i==0?0:i==3?1:2;float x=i==0?w*.85f:w*(.85f-fraction);MotionEvent e=MotionEvent.obtain(t,t+(i==0?0:i==1?600:i==2?900:1000),action,x,h*.72f,0);reader.surface.dispatchTouchEvent(e);e.recycle();}});waitFor(()->!reader.surface.animating(),3,"slow drag settles");}
 void thresholds()throws Exception{for(PageTurnMode mode:PageTurnMode.values()){select(mode);page(2);int offset=reader.anchor;slow(mode==PageTurnMode.NONE||mode==PageTurnMode.SIMULATION?.30f:.40f);assertTrue(reader.anchor==offset,"below threshold rolls back "+mode);slow(mode==PageTurnMode.NONE?.38f:.68f);assertTrue(reader.surface.frame.pageIndex==3,"above threshold commits once "+mode);page(2);}record("all_modes_slow_commit_and_rollback",true);}
 void freeze()throws Exception{select(PageTurnMode.COVER);page(2);ReaderPageView page=reader.surface.pages[1];String before=page.clock;int battery=reader.battery;hold(reader.width*.85f,reader.width*.42f,reader.height*.72f);ui(()->{reader.battery=battery==100?99:battery+1;reader.surface.invalidate();});assertTrue(before.equals(page.clock)&&reader.surface.pendingVisuals,"asynchronous footer stays frozen during drag");cancelHold();waitFor(()->!reader.surface.animating(),3,"cancel settles");assertTrue(!before.equals(page.clock)&&!reader.surface.pendingVisuals,"deferred footer applies after settlement");ui(()->{reader.battery=battery;reader.surface.invalidate();});record("visual_updates_deferred_during_gesture",true);}
 void geometry()throws Exception{int count=0;for(boolean top:new boolean[]{false,true})for(float fraction:new float[]{-.9f,-.1f,.05f,.2f,.5f,.8f,.99f})for(float y:new float[]{1,400,960,1450,1919}){CurlGeometry g=new CurlGeometry();g.update(1080,1920,1080*fraction,y,top);for(android.graphics.PointF p:new android.graphics.PointF[]{g.start1,g.start2,g.control1,g.control2,g.end1,g.end2,g.vertex1,g.vertex2})assertTrue(Float.isFinite(p.x)&&Float.isFinite(p.y),"finite fold geometry");assertTrue(!g.fold.isEmpty()&&!g.back.isEmpty(),"real quadratic fold paths");count++;}record("curl_geometry_positions",count);}

 void metrics()throws Exception{android.os.HandlerThread worker=new android.os.HandlerThread("window-frame-metrics");worker.start();java.util.concurrent.atomic.AtomicInteger frames=new java.util.concurrent.atomic.AtomicInteger();java.util.concurrent.atomic.AtomicLong longest=new java.util.concurrent.atomic.AtomicLong();android.view.Window.OnFrameMetricsAvailableListener listener=(window,value,dropped)->{frames.incrementAndGet();long duration=value.getMetric(android.view.FrameMetrics.TOTAL_DURATION);longest.accumulateAndGet(duration,Math::max);};ui(()->reader.getWindow().addOnFrameMetricsAvailableListener(listener,new android.os.Handler(worker.getLooper())));try{for(PageTurnMode mode:PageTurnMode.values()){select(mode);page(2);frames.set(0);longest.set(0);int requests=reader.surface.sceneRequests,draws=reader.surface.sceneDraws;ui(()->reader.surface.turn(1));waitFor(()->!reader.surface.animating(),3,"hardware turn complete");SystemClock.sleep(150);record("rendered_frames_"+mode.key,new JSONObject().put("window_completed_frames",frames.get()).put("scene_draws",reader.surface.sceneDraws-draws).put("explicit_scene_requests",reader.surface.sceneRequests-requests).put("longest_frame_ms",longest.get()/1e6));}}finally{ui(()->reader.getWindow().removeOnFrameMetricsAvailableListener(listener));worker.quitSafely();}}
}
