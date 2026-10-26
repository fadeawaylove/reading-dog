package com.example.yuejie;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import java.util.*;
/** Offline real-device checks: isolated preferences and generated paragraphs, no library edits. */
public class DisplayChecks extends Instrumentation {
 Activity activity;ReaderSurface surface;ReaderPaginator layout;ReaderSettings settings;Context isolated;int checks;
 void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 void ui(Runnable action){final Throwable[] failure={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
 public void onCreate(Bundle args){start();}
 ReaderPaginator paginate(){StringBuilder text=new StringBuilder();for(int i=0;i<100;i++)text.append("文字粗细与白屏翻页检查，保持原文阅读位置。\n\n");return new ReaderPaginator(text.toString(),"显示检查",1080,1920,3,3,settings);}
 Bitmap draw(){Bitmap b=Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),Bitmap.Config.ARGB_8888);surface.draw(new Canvas(b));return b;}
 void white(Bitmap b){int[] pixels=new int[b.getWidth()*b.getHeight()];b.getPixels(pixels,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());for(int pixel:pixels)if(pixel!=Color.WHITE)throw new AssertionError("white interval contains text/background");checks++;b.recycle();}
 void visible(){Bitmap b=draw();int dark=0;for(int y=50;y<b.getHeight()-50;y+=3)for(int x=0;x<b.getWidth();x+=3)if((b.getPixel(x,y)&255)<100)dark++;check(dark>50,"target text visible");b.recycle();}
 long ink(ReaderPaginator p){Bitmap b=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);p.draw(c,1,Color.BLACK);long sum=0;int[] pixels=new int[1080*1920];b.getPixels(pixels,0,1080,0,0,1080,1920);for(int pixel:pixels)sum+=255-(pixel&255);b.recycle();return sum;}
 void settingsChecks(){isolated=new ContextWrapper(getTargetContext()){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("display_checks_"+name,mode);}};SharedPreferences prefs=isolated.getSharedPreferences("ReaderActivity",0);
  for(int old=0;old<3;old++){prefs.edit().clear().putBoolean("reader_v2",true).putInt("font_weight",old).commit();ReaderSettings s=new ReaderSettings(isolated);check(s.fontWeight==(old==0?0:old==1?30:100),"legacy weight migrated");}
  prefs.edit().clear().putBoolean("reader_v2",true).commit();settings=new ReaderSettings(isolated);settings.comments=false;settings.pageTurnMode=PageTurnMode.NONE;settings.font=20;
  check(settings.ink()==Color.BLACK,"day ink pure black");settings.night=true;check(settings.ink()==0xffc4c6bf,"night ink preserved");settings.night=false;
  String previous="";long previousInk=-1;
  for(int weight=0;weight<=ReaderSettings.FONT_WEIGHT_MAX;weight+=ReaderSettings.FONT_WEIGHT_STEP){settings.fontWeight=weight;settings.save();check(new ReaderSettings(isolated).fontWeight==weight,"fine weight saved "+weight);ReaderPaginator p=paginate();int anchor=p.text.length()/2;ReaderPaginator.Page page=p.pages.get(p.locate(anchor));check(page.start<=anchor&&anchor<page.end,"anchor inside reflowed page");String key=BookPageIndex.profile(1080,1920,3,3,settings);check(!key.equals(previous),"weight cache separated");previous=key;long darkness=ink(p);check(darkness>previousInk,"slider raster grows darker at "+weight);previousInk=darkness;}
  for(int[] pair:new int[][]{{-1,0},{1,0},{33,30},{45,50},{99,100},{101,100}}){prefs.edit().putInt("font_weight_amount",pair[0]).commit();check(new ReaderSettings(isolated).fontWeight==pair[1],"previous amount rounds and clamps "+pair[0]);}
  settings.fontWeight=30;layout=paginate();
 }
 void attach(){ReaderSurface.Host host=new ReaderSurface.Host(){public ReaderSurface.Frame adjacent(int dir){int next=surface.frame.pageIndex+dir;return next<0||next>=layout.pages.size()?null:new ReaderSurface.Frame(surface.frame.id,"显示检查",0,1,layout,next);}public void commit(ReaderSurface.Frame f){surface.setFrame(f);}public void needChapter(int dir){}public void center(){}public void sized(int w,int h){}public int battery(){return 80;}public Map<Integer,Long> commentCounts(String id){return Collections.emptyMap();}public void paragraph(int start){}};
  surface=new ReaderSurface(getTargetContext(),settings,host);activity.setContentView(surface);surface.setFrame(new ReaderSurface.Frame("fixture","显示检查",0,1,layout,2));
 }
 public void onStart(){Bundle result=new Bundle();boolean success=false;try{
  ui(this::settingsChecks);
  ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("am start -n com.example.yuejie/.MainActivity");java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){while(in.read()!=-1){}}activity=monitor.waitForActivityWithTimeout(10000);removeMonitor(monitor);check(activity!=null,"test activity launched");ui(this::attach);SystemClock.sleep(200);
  ui(()->{check(!surface.whitePage,"initial page has no flash");surface.turn(1);check(surface.frame.pageIndex==3,"turn commits once");check(surface.whitePage,"none turn starts blank");white(draw());});
  SystemClock.sleep(25);ui(()->check(surface.whitePage,"blank remains during interval"));SystemClock.sleep(180);ui(()->{check(!surface.whitePage,"blank ends automatically");visible();surface.turn(-1);white(draw());});SystemClock.sleep(180);
  ui(()->{check(surface.frame.pageIndex==2&&!surface.whitePage,"reverse turn completes");surface.target=surface.neighbor(1);surface.animateTo(0,false);check(!surface.whitePage,"cancelled turn does not blank");surface.turn(1);white(draw());surface.turn(1);white(draw());check(surface.frame.pageIndex==4,"rapid turns each commit once");});SystemClock.sleep(180);
  ui(()->{check(!surface.whitePage,"rapid turns reveal final page");surface.setFrame(new ReaderSurface.Frame("next-chapter","显示检查",1,2,layout,0));white(draw());surface.suspendRendering();check(!surface.whitePage&&!surface.whitePageScheduled,"pause clears blank timer");surface.resumeRendering();visible();ReaderPaginator replacement=paginate();surface.setFrame(new ReaderSurface.Frame("next-chapter","显示检查",1,2,replacement,0));check(!surface.whitePage,"settings reflow does not blank");settings.pageTurnMode=PageTurnMode.COVER;surface.modeChanged();surface.turn(1);check(!surface.whitePage,"animated mode has no white flash");surface.cancelAnimation();});
  success=true;result.putString("stream","PASS "+checks+" display checks\n");
 }catch(Throwable t){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));}finally{if(surface!=null)ui(()->surface.suspendRendering());if(activity!=null)ui(()->activity.finish());if(isolated!=null)isolated.getSharedPreferences("ReaderActivity",0).edit().clear().commit();}finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
