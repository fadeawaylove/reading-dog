package com.example.yuejie;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import org.json.*;import java.io.*;import java.util.*;

/** OS-injected gestures against the real paragraph overlay and its real comments. */
public final class CommentSwipeChecks extends DeviceChecks {
 float[] point(View v,float y){int[] p=new int[2];ui(()->v.getLocationOnScreen(p));return new float[]{p[0]+v.getWidth()*.5f,p[1]+y};}
 void pull(View v,int distance)throws Exception{float[] p=point(v,reader.d(24));swipe(p[0],p[1],p[0],p[1]+reader.d(distance),420);}
 void top()throws Exception{ui(()->reader.comments.commentList.setSelection(0));waitFor(()->!reader.comments.commentList.canScrollVertically(-1),3,"list at top");}
 void runChecks()throws Exception{
  LocalLibrary db=new LocalLibrary(getTargetContext());String book="6982529841564224526";JSONObject before=db.progress(book);Map<String,Long> times=new HashMap<>();
  try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT id,last_read FROM books",null)){while(c.moveToNext())times.put(c.getString(0),c.getLong(1));}
  try{
   launchMain();reader=(ReaderActivity)startActivitySync(new Intent(getTargetContext(),ReaderActivity.class).putExtra("book_id",book).putExtra("book_name","我在精神病院学斩神").putExtra("chapter_index",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ready();
   ui(()->{reader.settings.comments=true;reader.comments.retry();});waitFor(()->reader.comments.metadata!=null,40,"real paragraph metadata");
   int start=reader.comments.metadata.getJSONArray("paragraph_meta").getJSONObject(2).getInt("start"),anchor=reader.anchor;
   ui(()->reader.comments.open(start));waitFor(()->!reader.comments.loading&&reader.comments.displayed.size()>10,40,"real comment rows");top();shot("comment-swipe-before");
   float[] p=point(reader.comments.commentList,reader.comments.commentList.getHeight()*.8f);swipe(p[0],p[1],p[0],p[1]-reader.d(140),500);
   assertTrue(reader.comments.layer!=null&&reader.comments.commentList.canScrollVertically(-1),"upward swipe scrolls comments");record("upward_swipe_scrolls_without_dismiss",true);
   ui(()->reader.comments.commentList.setSelection(8));waitForIdleSync();int old=reader.comments.commentList.getFirstVisiblePosition();pull(reader.comments.commentList,60);
   assertTrue(reader.comments.layer!=null&&reader.comments.panel.getTranslationY()==0&&reader.comments.commentList.getFirstVisiblePosition()<=old,"non-top downward swipe scrolls list only");record("non_top_pull_keeps_sheet",true);
   ui(()->reader.comments.commentList.setSelection(8));waitForIdleSync();pull(reader.comments.panel,100);
   assertTrue(reader.comments.layer!=null&&reader.comments.panel.getTranslationY()==0,"header also respects list not at top");record("header_respects_content_top_gate",true);
   top();pull(reader.comments.commentList,32);assertTrue(reader.comments.layer!=null&&Math.abs(reader.comments.panel.getTranslationY())<1,"short pull bounces back");record("short_pull_returns",true);
   float[] cancel=point(reader.comments.commentList,reader.d(24));long time=SystemClock.uptimeMillis();
   for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_CANCEL}){MotionEvent e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,cancel[0],cancel[1]+(action==MotionEvent.ACTION_DOWN?0:reader.d(100)),0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();SystemClock.sleep(40);}
   SystemClock.sleep(300);assertTrue(reader.comments.layer!=null&&Math.abs(reader.comments.panel.getTranslationY())<1,"cancel never dismisses");record("cancelled_pull_returns",true);
   top();pull(reader.comments.commentList,130);waitFor(()->reader.comments.layer==null,3,"top list pull closes");assertTrue(reader.anchor==anchor,"dismiss preserves reading anchor");record("top_content_pull_dismisses_preserving_anchor",true);shot("comment-swipe-closed");
   ui(()->reader.comments.open(start));waitFor(()->!reader.comments.loading&&!reader.comments.displayed.isEmpty(),15,"reopen real comments");top();pull(reader.comments.panel,130);waitFor(()->reader.comments.layer==null,3,"top header pull closes");record("top_header_pull_dismisses",true);
   ui(()->reader.comments.open(start));waitFor(()->!reader.comments.loading&&!reader.comments.displayed.isEmpty(),15,"reopen for continuous pull");ui(()->reader.comments.commentList.setSelectionFromTop(0,-reader.d(20)));waitFor(()->reader.comments.commentList.canScrollVertically(-1),3,"first comment partly scrolled");pull(reader.comments.commentList,150);waitFor(()->reader.comments.layer==null,3,"same pull transfers at top");assertTrue(reader.anchor==anchor,"all swipes preserve reading position");record("continuous_pull_scrolls_to_top_then_dismisses",true);
  }finally{
   if(reader!=null)ui(()->reader.finish());
   if(before!=null)db.progress(book,before.optString("chapter"),before.optInt("offset"),before.optInt("index"),before.optInt("total"),before.optString("title"));
   for(Map.Entry<String,Long> e:times.entrySet()){ContentValues v=new ContentValues();v.put("last_read",e.getValue());db.getWritableDatabase().update("books",v,"id=?",new String[]{e.getKey()});}
   db.close();
  }
 }
 public void onStart(){output=new File(getTargetContext().getFilesDir(),"comment-swipe");output.mkdirs();Bundle result=new Bundle();Throwable failure=null;try{runChecks();result.putString("stream","PASS "+checks.length()+" comment swipe checks\n");}catch(Throwable e){failure=e;result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));}try{try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(output,"results.json")),"UTF-8")){w.write(new JSONObject().put("success",failure==null).put("checks",checks).put("failure",failure==null?JSONObject.NULL:android.util.Log.getStackTraceString(failure)).toString(2));}}catch(Exception e){result.putString("stream",e.toString());}finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
