package com.example.yuejie;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import org.json.*;import java.io.*;import java.net.*;import java.util.*;

/** Runs with no private backend asset, in its own package and database. */
public final class DirectChecks extends Instrumentation {
 int checks;Bundle args;LocalLibrary library;Context context;final Set<String> paths=new HashSet<>();
 void ok(boolean value,String label){if(!value)throw new AssertionError(label);checks++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 public void onCreate(Bundle options){super.onCreate(options);args=options;start();}
 void parsed()throws Exception{
  String markup="<p idx='1'> 一——二 😀<img src='https://p3.byteimg.com/a?x=1&amp;y=2' width='80' height='60'>尾<br>行 </p><p idx='2'>下一段</p>";
  JSONObject body=ChapterContent.parse("1","图",markup);String text=body.getString("text");JSONObject image=body.getJSONArray("images").getJSONObject(0);
  ok(text.equals("一——二 😀\ufffc尾\n行\n\n下一段"),"canonical Android text preserves real 一, dashes, emoji and line breaks");
  ok(image.getInt("offset")==text.indexOf('\ufffc')&&image.getString("url").endsWith("?x=1&y=2")&&image.getInt("width")==80,"image metadata retains UTF-16 offsets and decoded query");
  JSONObject lazy=ChapterContent.parse("1","图","<p idx='1'><img data-src='//p3.byteimg.com/lazy'></p>");ok(lazy.getJSONArray("images").getJSONObject(0).getString("url").equals("https://p3.byteimg.com/lazy"),"lazy image source retained");
  ok(body.getJSONArray("paragraph_meta").getJSONObject(1).getInt("start")==text.indexOf("下一段"),"paragraph offsets remain canonical after images");
 }
 void parity()throws Exception{
  try(InputStream in=context.getAssets().open("text-parity.json")){JSONArray fixtures=new JSONArray(new String(ProtocolCrypto.read(in),"UTF-8"));for(int i=0;i<fixtures.length();i++){JSONObject f=fixtures.getJSONObject(i);String expected=android.text.Html.fromHtml(f.getString("html"),android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();if(expected.isEmpty())continue;JSONObject body=ChapterContent.parse("1","", "<p idx='1'>"+f.getString("html")+"</p>");if(!expected.equals(body.getString("text")))throw new AssertionError("Android text parity "+i);}ok(fixtures.length()>0,fixtures.length()+" optional historical paragraphs preserve native Android text");}
 }
 void routing()throws Exception{
  NovelApi.Transport reject=(url,headers,body)->{URI u=new URI(url);ok(!u.getHost().equals("reader.fadeaway.ltd"),"content routes directly to upstream");ok(!headers.containsKey("Authorization")||headers.get("Authorization").equals("Bearer"),"no private credential in upstream headers");paths.add(u.getPath());throw new IOException("fixture stop");};
  NovelApi api=new NovelApi(context,library,reject);JSONObject profile=api.asset("content-defaults.json");JSONArray defaults=profile.getJSONArray("query_defaults");defaults.put(0,new JSONArray().put("iid").put("1111"));defaults.put(1,new JSONArray().put("device_id").put("2222"));context.getSharedPreferences("api_profiles",0).edit().putString("content",profile.toString()).putString("search",profile.toString()).commit();
  interfaceCall(()->api.search("测试",0,null));interfaceCall(()->api.detail("123"));interfaceCall(()->api.directory("123",true));interfaceCall(()->api.pageMetadata("123",new JSONArray().put(new JSONObject().put("id","456"))));interfaceCall(()->api.chapter("123","456",true));
  ParagraphCommentApi comments=new ParagraphCommentApi(context,library,reject);interfaceCall(()->comments.stats("123","456","1"));
  ok(paths.contains("/reading/bookapi/search/tab/v")&&paths.contains("/page/123")&&paths.contains("/api/reader/directory/detail")&&paths.contains("/reading/bookapi/directory/all_items/v/")&&paths.contains("/reading/crypt/registerkey")&&paths.contains("/novel/commentapi/idea/list/456/v1"),"all content API entrypoints reach injectable upstream transport");
  ok(NetworkWork.readingRequests.get()==0,"failed requests release reading priority");
  ByteArrayOutputStream png=new ByteArrayOutputStream();Bitmap bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);bitmap.compress(Bitmap.CompressFormat.PNG,100,png);bitmap.recycle();
  byte[] image=UpstreamImages.read("https://p3.byteimg.com/test",url->new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){ok(getRequestProperty("Authorization")==null,"image request contains no backend credential");return 200;}public String getContentType(){return "image/png";}public InputStream getInputStream(){return new ByteArrayInputStream(png.toByteArray());}});
  ok(Arrays.equals(image,png.toByteArray()),"direct image transport preserves original bytes");
  boolean denied=false;try{UpstreamImages.read("https://reader.fadeaway.ltd/test",url->{throw new AssertionError();});}catch(IOException expected){denied=true;}ok(denied,"media path cannot fall back to backend");
  context.getSharedPreferences("api_profiles",0).edit().clear().commit();
 }
 interface Work{Object run()throws Exception;}
 void interfaceCall(Work work)throws Exception{try{work.run();throw new AssertionError("expected intercepted request");}catch(IOException expected){}}
 void live()throws Exception{
  NovelApi.Transport wire=(url,headers,body)->{if(new URI(url).getHost().equals("reader.fadeaway.ltd"))throw new AssertionError("backend content request");paths.add(new URI(url).getPath());return NovelApi.http(url,headers,body);};
  NovelApi api=new NovelApi(context,library,wire);NovelApi.SearchPage search=api.search("我在精神病院学斩神",0,null);ok(!search.books.isEmpty(),"live direct search without backend credentials");
  String book="7383244104223689790",chapter="7383333629616718398";Book detail=api.detail(book);ok(!detail.title.isEmpty(),"live direct detail");JSONArray directory=api.directory(book,true);ok(directory.length()>0,"live direct directory");ok(api.pageMetadata(book,directory).length()>0,"live direct chapter word counts");
  JSONObject body=api.chapter(book,chapter,true);ok(!body.getString("text").isEmpty()&&body.has("images"),"live direct decrypted chapter with image metadata");
  ParagraphCommentApi comments=new ParagraphCommentApi(context,library,wire);JSONObject meta=comments.metadata(book,chapter),stats=comments.stats(book,chapter,meta.getString("item_version"));JSONObject selected=null;JSONArray paragraphs=meta.getJSONArray("paragraph_meta");for(int i=0;i<paragraphs.length();i++){JSONObject p=paragraphs.getJSONObject(i);if(stats.optLong(p.getString("id"))>0){selected=p;break;}}ok(selected!=null,"live direct paragraph statistics");JSONObject page=comments.comments(book,chapter,meta.getString("item_version"),selected.getString("id"),"",selected.getString("text"));ok(page.getJSONArray("items").length()>0,"live direct validated comments");
  ok(UpstreamImages.read(detail.cover).length>0,"live direct cover CDN");
  NovelApi offline=new NovelApi(context,library,(u,h,b)->{throw new AssertionError("offline cache attempted network");});ok(offline.chapter(book,chapter).getString("text").equals(body.getString("text")),"chapter remains readable with all networking forbidden");
  ok(library.progress(book)==null,"read-only live checks preserve progress");
 }
 public void onStart(){Bundle result=new Bundle();try{context=getTargetContext();library=new LocalLibrary(context);if("live".equals(args.getString("phase")))live();else if("parity".equals(args.getString("phase")))parity();else{parsed();routing();DirectRecoveryChecks recovery=new DirectRecoveryChecks(context);recovery.contentRecovery();ok(recovery.checks==9,"nine identity/key/empty-response recovery regressions");}result.putString("stream","PASS "+checks+" direct checks\n");finish(0,result);}catch(Throwable e){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));finish(1,result);}finally{if(library!=null)library.close();}}
}
