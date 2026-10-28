package com.example.yuejie;
import android.app.*;import android.content.*;import android.content.res.*;import android.database.sqlite.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;
/** Real-device render checks with a separate library; never insert fixture books in the user's shelf. */
public final class ReadabilityChecks extends DeviceChecks {
 @Override void ui(Runnable action){final Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
 LocalLibrary fixture,original;Context fixtureContext;AboutActivity about;float scale;int textCount;
 static class FixtureContext extends TestContext {
  FixtureContext(Context c){super(c);}
  public File getDatabasePath(String name){return getBaseContext().getDatabasePath("readability-"+name);}
  public SharedPreferences getSharedPreferences(String name,int mode){return getBaseContext().getSharedPreferences("readability-"+name,mode);}
 }
 void black(View view){if(view.getVisibility()!=View.VISIBLE)return;if(view instanceof TextView){TextView t=(TextView)view;if(t.length()>0||t.getHint()!=null){assertTrue(t.getCurrentTextColor()==Color.BLACK,"black text: "+t.getText());if(t.getHint()!=null)assertTrue(t.getCurrentHintTextColor()==Color.BLACK,"black hint");textCount++;}}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)black(g.getChildAt(i));}}
 @Override View findText(View root,String value){if(root.getContentDescription()!=null&&root.getContentDescription().toString().endsWith("封面"))return null;return super.findText(root,value);}
 void size(String value,float sp){TextView t=(TextView)findText(main.root,value);assertTrue(t!=null,"text exists: "+value);assertTrue(Math.abs(t.getTextSize()/main.getResources().getDisplayMetrics().scaledDensity-sp)<.1,"size: "+value);}
 void notClipped(View view){if(view.getVisibility()!=View.VISIBLE||(view.getContentDescription()!=null&&view.getContentDescription().toString().endsWith("封面")))return;if(view instanceof TextView){TextView t=(TextView)view;if(t.getLayout()!=null&&t.length()>0)assertTrue(t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom(),"vertical clipping: "+t.getText());}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)notClipped(g.getChildAt(i));}}
 void detailsFlow()throws Exception{
  launchMain();scale=main.getResources().getConfiguration().fontScale;
  LocalLibrary library=new LocalLibrary(getTargetContext());Book b=library.books(true).get(0);
  detail=(DetailActivity)startActivitySync(new Intent(getTargetContext(),DetailActivity.class).putExtra("book",b.raw.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{
   waitFor(()->!detail.loading,40,"detail loaded");final View[] primary={null};ui(()->{View root=detail.getWindow().getDecorView();primary[0]=findText(root,library.progress(b.id)==null?"开始阅读":"继续阅读");assertTrue(primary[0] instanceof TextView&&((TextView)primary[0]).getCurrentTextColor()==PaperUi.PAPER,"detail primary white text");primary[0].setVisibility(View.GONE);black(root);primary[0].setVisibility(View.VISIBLE);});shot("book-details");record("book_details_black_and_white_primary",true);
   ui(()->detail.catalog());SystemClock.sleep(2500);waitForIdleSync();android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();assertTrue(root!=null&&!root.findAccessibilityNodeInfosByText(b.title).isEmpty(),"directory dialog shown");shot("detail-directory");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);record("detail_directory_dialog",true);
  }finally{ui(()->detail.finish());library.close();}
 }
 void readerFlow()throws Exception{
  SQLiteDatabase db=original.getWritableDatabase();List<ContentValues> positions=new ArrayList<>();Map<String,Long> order=new HashMap<>();
  try(android.database.Cursor c=db.rawQuery("SELECT * FROM progress",null)){while(c.moveToNext()){ContentValues v=new ContentValues();android.database.DatabaseUtils.cursorRowToContentValues(c,v);positions.add(v);}}
  try(android.database.Cursor c=db.rawQuery("SELECT id,last_read FROM books",null)){while(c.moveToNext())order.put(c.getString(0),c.getLong(1));}
  List<Book> shelf=original.books(true);assertTrue(!shelf.isEmpty(),"real shelf available for continue reading");Book b=shelf.get(0);
  try{
   reader=(ReaderActivity)startActivitySync(new Intent(getTargetContext(),ReaderActivity.class).putExtra("book_id",b.id).putExtra("book_name",b.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ready();
   ui(()->{reader.settings=new ReaderSettings(fixtureContext);reader.settings.night=false;reader.settingChanged();});ready();ui(()->reader.showSettings());ui(()->black(reader.panel));shot("reader-settings");record("continue_reading_and_day_settings",true);
   ui(()->reader.catalog(false));ui(()->black(reader.panel));shot("reader-catalog");final ListView[] catalog={null};ui(()->{for(int i=0;i<reader.panel.getChildCount();i++)if(reader.panel.getChildAt(i) instanceof ListView)catalog[0]=(ListView)reader.panel.getChildAt(i);assertTrue(catalog[0]!=null,"catalog list");catalog[0].performItemClick(catalog[0].getChildAt(0),catalog[0].getFirstVisiblePosition(),0);});ready();record("catalog_jump",true);
   ui(()->reader.catalog(true));ui(()->black(reader.panel));shot("reader-bookmarks");
   ui(()->{reader.hideOverlays();LinearLayout comments=PaperUi.col(reader);try{comments.addView(reader.comments.commentRow(new JSONObject().put("name","测试读者").put("text","检查段评昵称、点赞和内容均为纯黑。").put("likes",12)));}catch(Exception e){throw new RuntimeException(e);}black(comments);});record("comment_row_black_text",true);
   ui(()->{reader.settings.night=true;reader.settingChanged();});ready();ui(()->{reader.showSettings();assertTrue(reader.settings.ink()==0xffc4c6bf&&reader.settings.muted()==0xff858b82,"night colors retained");TextView label=(TextView)findText(reader.panel,"亮度");assertTrue(label!=null&&label.getCurrentTextColor()==reader.ink(),"night setting label readable");});shot("reader-night-settings");record("night_colors_preserved",true);
  }finally{
   if(reader!=null)ui(()->reader.finish());
   db.beginTransaction();try{db.delete("progress",null,null);for(ContentValues v:positions)db.insertOrThrow("progress",null,v);for(Map.Entry<String,Long> e:order.entrySet()){ContentValues v=new ContentValues();v.put("last_read",e.getValue());db.update("books",v,"id=?",new String[]{e.getKey()});}db.setTransactionSuccessful();}finally{db.endTransaction();}
  }
  record("reading_positions_and_shelf_order_restored",true);
 }
 void runChecks()throws Exception{
  fixtureContext=new FixtureContext(getTargetContext());SQLiteDatabase.deleteDatabase(fixtureContext.getDatabasePath("reading-dog.db"));fixture=new LocalLibrary(fixtureContext);
  launchMain();original=main.library;scale=main.getResources().getConfiguration().fontScale;
  ui(()->{main.shelfRefresher.close();main.library=fixture;main.render();});ui(()->{black(main.root);size("尚无阅读记录",16);notClipped(main.root);});shot("empty-home");record("empty_home_black_and_readable",true);
  Book b=new Book(new JSONObject().put("book_id","readability-fixture").put("book_name","这是一本用来检查主页长书名自动换行和字号放大的测试小说").put("author","这是一位名字非常长的测试作者").put("chapter_count",2));
  fixture.add(b);JSONArray dir=new JSONArray();for(int i=0;i<5;i++)dir.put(new JSONObject().put("id","c"+i).put("title","第"+(i+1)+"章 很长的章节标题用于检查省略显示"));fixture.directory(b.id,dir);fixture.progress(b.id,"c0",0,0,5,dir.getJSONObject(0).optString("title"));
  ui(()->main.render());ui(()->{black(main.root);size("书架",20);size("最近阅读",14);size("有新章 · 3",14);notClipped(main.root);assertTrue(main.input.getTextSize()/main.getResources().getDisplayMetrics().scaledDensity==16,"search input 16sp");});shot("populated-home");record("long_title_progress_new_chapters",true);
  for(float value:new float[]{1.3f,1.5f}){ui(()->{Configuration config=new Configuration(main.getResources().getConfiguration());config.fontScale=value;main.getResources().updateConfiguration(config,main.getResources().getDisplayMetrics());main.render();});ui(()->{black(main.root);notClipped(main.root);});shot("home-scale-"+value);}record("font_scale_130_and_150_percent",true);
  ui(()->{Configuration config=new Configuration(main.getResources().getConfiguration());config.fontScale=scale;main.getResources().updateConfiguration(config,main.getResources().getDisplayMetrics());main.searching=true;main.results.clear();main.results.add(b);main.render();});ui(()->{black(main.root);size("搜索结果",20);size(b.title,18);notClipped(main.root);});shot("search-fixture");record("search_result_typography",true);
  final AlertDialog[] dialog={null};ui(()->{dialog[0]=new AlertDialog.Builder(main).setTitle("操作确认").setMessage("浅底弹窗文字检查").setPositiveButton("确定",null).setNegativeButton("取消",null).show();});ui(()->black(dialog[0].getWindow().getDecorView()));shot("dialog");ui(()->dialog[0].dismiss());record("system_dialog_black_text",true);
  about=(AboutActivity)startActivitySync(new Intent(getTargetContext(),AboutActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ui(()->{assertTrue(about.action.getCurrentTextColor()==Color.WHITE,"dark button white text");about.action.setVisibility(View.GONE);black(about.getWindow().getDecorView());about.action.setVisibility(View.VISIBLE);});shot("about");ui(()->about.finish());record("about_black_and_white_primary_button",true);
  // Restore the real shelf before exercising the real search; search does not write reading progress.
  ui(()->{main.library=original;main.clearSearch();main.input.setText("我在精神病院学斩神");main.submit();});waitFor(()->!main.loading,45,"real search completed");assertTrue(!main.results.isEmpty(),"real search has results");ui(()->black(main.root));shot("search-real");record("real_search",main.results.size());
  readerFlow();
  record("black_text_views_checked",textCount);
 }
 public void onStart(){output=new File(getTargetContext().getFilesDir(),"readability-evidence");output.mkdirs();Bundle result=new Bundle();Throwable failure=null;try{if(args!=null&&"true".equals(args.getString("details")))detailsFlow();else runChecks();result.putString("stream","PASS "+checks.length()+" readability scenarios\n");}catch(Throwable e){failure=e;result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));}finally{if(about!=null)ui(()->about.finish());if(main!=null)ui(()->{if(original!=null)main.library=original;Configuration c=new Configuration(main.getResources().getConfiguration());c.fontScale=scale==0?1:scale;main.getResources().updateConfiguration(c,main.getResources().getDisplayMetrics());main.finish();});if(fixture!=null)fixture.close();if(fixtureContext!=null){SQLiteDatabase.deleteDatabase(fixtureContext.getDatabasePath("reading-dog.db"));fixtureContext.getSharedPreferences("reading_dog",0).edit().clear().commit();fixtureContext.getSharedPreferences("ReaderActivity",0).edit().clear().commit();}}
  try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(output,args!=null&&"true".equals(args.getString("details"))?"details-results.json":"results.json")),"UTF-8")){w.write(new JSONObject().put("success",failure==null).put("checks",checks).put("failure",failure==null?JSONObject.NULL:android.util.Log.getStackTraceString(failure)).toString(2));}catch(Exception e){result.putString("stream",e.toString());}finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
