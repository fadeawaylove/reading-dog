package com.example.yuejie;
import android.content.*;import org.json.*;import java.io.*;
/** Local comment cache with all upstream work delegated to the private backend. */
final class ParagraphCommentApi {
 interface Remote{Object call(String method,JSONObject body)throws Exception;}
 Remote remote;
 final Context context;final LocalLibrary library;final CommentStore cache;
 ParagraphCommentApi(Context c,LocalLibrary l){context=c.getApplicationContext();library=l;cache=new CommentStore(c);remote=(method,body)->BackendClient.call(context,method,body);}
 ParagraphCommentApi(Context c,LocalLibrary l,NovelApi.Transport transport){this(c,l);}
 static String key(String kind,String book,String chapter,String version,String para,String cursor){return (kind.equals("page")?"page-v2":kind)+":"+book+":"+chapter+":"+version+":"+para+":"+cursor;}
 static String secure(String url){return url.startsWith("http://")?"https://"+url.substring(7):url;}
 // The list summary may include paragraph terminators absent from extracted <p> text.
 // Preserve all other whitespace and characters so a different paragraph is still rejected.
 static boolean sameParagraph(String left,String right){return withoutTerminators(left).equals(withoutTerminators(right));}
 static String withoutTerminators(String text){int end=text.length();while(end>0&&(text.charAt(end-1)=='\n'||text.charAt(end-1)=='\r'))end--;return text.substring(0,end);}

 JSONObject metadata(String book,String chapter)throws Exception{return metadata(book,chapter,()->true);}
 JSONObject metadata(String book,String chapter,java.util.function.BooleanSupplier active)throws Exception{
   if(!active.getAsBoolean())throw new IOException("Cancelled");JSONObject existing=library.chapter(book,chapter);if(existing==null)throw new IOException("正文尚未加载");CommentStore.Entry cached=cache.get(key("mapping",book,chapter,"","",""));if(cached!=null&&!cached.stale&&cached.value.optString("text").equals(existing.optString("text")))return cached.value;
   JSONObject fresh=(JSONObject)remote.call("comments/metadata",new JSONObject().put("book",book).put("chapter",chapter));if(!active.getAsBoolean())throw new IOException("Cancelled");JSONObject latest=library.chapter(book,chapter);if(latest==null||!latest.optString("text").equals(fresh.optString("text")))throw new IOException("正文已更新，请重新打开章节后查看段评");library.chapter(book,chapter,fresh);cache.put(key("mapping",book,chapter,"","",""),fresh);return fresh;
 }
 JSONObject stats(String book,String chapter,String version)throws Exception{JSONObject out=(JSONObject)remote.call("comments/stats",new JSONObject().put("book",book).put("chapter",chapter).put("version",version));cache.put(key("stats",book,chapter,version,"",""),out);return out;}
 JSONObject comments(String book,String chapter,String version,String para,String cursor,String expected)throws Exception{JSONObject out=(JSONObject)remote.call("comments/page",new JSONObject().put("book",book).put("chapter",chapter).put("version",version).put("paragraph",para).put("cursor",cursor).put("expected",expected));cache.put(key("page",book,chapter,version,para,cursor),out);return out;}
}
