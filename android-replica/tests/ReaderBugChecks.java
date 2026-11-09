package com.example.yuejie;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import org.json.*;import java.io.*;import java.util.*;
/** Reproduces paragraph identity failures and compares actual mode rasters on a device. */
public class ReaderBugChecks extends TurnChecks {
 Map<String,Long> shelfTimes(LocalLibrary db){Map<String,Long> times=new HashMap<>();try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT id,last_read FROM books",null)){while(c.moveToNext())times.put(c.getString(0),c.getLong(1));}return times;}
 void restoreShelfTimes(LocalLibrary db,Map<String,Long> times){for(Map.Entry<String,Long> e:times.entrySet()){ContentValues value=new ContentValues();value.put("last_read",e.getValue());db.getWritableDatabase().update("books",value,"id=?",new String[]{e.getKey()});}}
 public void onStart(){output=new File(getTargetContext().getExternalFilesDir(null),"reader-bugs");output.mkdirs();Bundle result=new Bundle();Throwable failure=null;Map<String,Long> times=null;
  try{book="7623686597409508376";db=new LocalLibrary(getTargetContext());times=shelfTimes(db);originalPosition=db.progress(book);originalPrefs=new JSONObject(getTargetContext().getSharedPreferences("ReaderActivity",0).getAll());
   if(!"turn".equals(args.getString("phase"))&&!"gestures".equals(args.getString("phase")))paragraphs();
   if("gestures".equals(args.getString("phase"))){launchReader();for(PageTurnMode mode:PageTurnMode.values())oneMode(mode);}else if(!"comments".equals(args.getString("phase")))visuals();
  }catch(Throwable e){failure=e;try{record("failure",android.util.Log.getStackTraceString(e));}catch(Exception ignored){}}
  finally{try{if(reader!=null)ui(()->reader.finish());if(originalPrefs!=null)restorePrefs(originalPrefs);if(originalPosition!=null)db.progress(book,originalPosition.optString("chapter"),originalPosition.optInt("offset"),originalPosition.optInt("index"),originalPosition.optInt("total"),originalPosition.optString("title"));if(times!=null)restoreShelfTimes(db,times);try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(output,"results-"+args.getString("phase","all")+".json")),"UTF-8")){w.write(checks.toString(2));}}catch(Exception e){failure=e;}}
  result.putString("stream",failure==null?"PASS":"FAIL "+failure);finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
 void paragraphs()throws Exception{
  String id="7623686685754130968";ParagraphCommentApi client=new ParagraphCommentApi(getTargetContext(),db);JSONObject meta=client.metadata(book,id);String version=meta.getString("item_version");JSONArray ps=meta.getJSONArray("paragraph_meta");int failures=0;
  for(int i=0;i<Math.min(12,ps.length());i++){JSONObject p=ps.getJSONObject(i);if("probe".equals(args.getString("phase"))&&i!=1&&i!=8)continue;String pid=p.getString("id"),expected=p.getString("text");
   JSONObject row=new JSONObject().put("paragraph",pid).put("expected",expected);
   try{JSONObject page=client.comments(book,id,version,pid,"",expected);row.put("loaded",page.getJSONArray("items").length());}catch(Exception e){failures++;row.put("error",e.toString());}record("paragraph_"+pid,row);
  }assertTrue(failures==0,"first 12 paragraph pages load; failures="+failures);
  JSONObject p=ps.getJSONObject(1);boolean rejected=false;try{client.comments(book,id,version,p.getString("id"),"","wrong paragraph");}catch(IOException e){rejected=true;}assertTrue(rejected,"wrong current paragraph rejected");
  rejected=false;try{client.comments(book,id,"wrong-version",p.getString("id"),"",p.getString("text"));}catch(IOException e){rejected=true;}assertTrue(rejected,"stale mapping rejected");
  // Pure upstream scope guards are checked in BackendChecks.
  launchReader();ui(()->{reader.settings.comments=true;reader.settingChanged();});ready();waitFor(()->reader.comments.metadata!=null&&!reader.comments.counts.isEmpty(),40,"paragraph metadata");ui(()->reader.comments.open(p.optInt("start")));waitFor(()->!reader.comments.loading&&!reader.comments.seen.isEmpty(),30,"reported failing paragraph visible");shot("fixed-paragraph-1");record("reported_paragraph_visible",reader.comments.seen.size());ui(()->reader.comments.dismiss());
 }
 void visuals()throws Exception{if(reader==null)launchReader();for(float yFraction:new float[]{.25f,.5f,.75f}){Bitmap[] shots=new Bitmap[2];int n=0;for(PageTurnMode mode:new PageTurnMode[]{PageTurnMode.SIMULATION,PageTurnMode.COVER}){select(mode);page(2);int w=reader.surface.getWidth(),h=reader.surface.getHeight();hold(w*.85f,w*.42f,h*yFraction);shots[n++]=rendering();shot(mode.key+"-"+yFraction);if(mode==PageTurnMode.SIMULATION&&yFraction==.5f)assertTrue(Math.abs(((PageTurnRenderer.Curl)reader.surface.renderer).geometry.y-((PageTurnRenderer.Curl)reader.surface.renderer).geometry.cornerY)>h*.1f,"middle swipe must retain a visible diagonal curl");cancelHold();waitFor(()->!reader.surface.animating(),3,"cancel");}int differences=0;for(int y=100;y<shots[0].getHeight()-100;y+=4)for(int x=20;x<shots[0].getWidth()-20;x+=4)if(shots[0].getPixel(x,y)!=shots[1].getPixel(x,y))differences++;record("mode_pixel_difference_"+yFraction,differences);for(Bitmap b:shots)b.recycle();assertTrue(differences>1000,"simulation and cover must differ");}}
}




