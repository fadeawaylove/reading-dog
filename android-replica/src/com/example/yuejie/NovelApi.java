package com.example.yuejie;

import android.content.*;
import android.text.Html;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

/** All network operations are synchronous here; callers use the bounded worker pool. */
final class NovelApi {
  static final ExecutorService IO=Executors.newFixedThreadPool(4);
  static final android.os.Handler MAIN=new android.os.Handler(android.os.Looper.getMainLooper());
  interface Work<T>{T run()throws Exception;}
  interface Result<T>{void done(T value,String error);}
  static <T> void async(Work<T> w,Result<T> cb){IO.execute(()->{try{T v=w.run();MAIN.post(()->cb.done(v,null));}catch(Exception e){Log.w("ReadingDogApi","request failed",e);String msg=e.getMessage();MAIN.post(()->cb.done(null,msg==null?"请求失败，请重试":msg));}});}
  static final class EmptyResponse extends IOException{EmptyResponse(){super("服务返回空响应，请重试");}}
  interface Transport{String request(String url,Map<String,String> headers,JSONObject body)throws Exception;}
  final Context context;final LocalLibrary store;
  interface ChapterFetcher{JSONObject fetch(String book,String chapter)throws Exception;}
  ChapterFetcher chapterFetcher;
  NovelApi(Context c,LocalLibrary s){context=c.getApplicationContext();store=s;chapterFetcher=(book,chapter)->(JSONObject)BackendClient.call(context,"chapter",new JSONObject().put("book",book).put("chapter",chapter));}
  NovelApi(Context c,LocalLibrary s,Transport transport){this(c,s);}
  static String http(String url,Map<String,String> headers,JSONObject body)throws Exception{throw new IOException("请使用阅读服务接口");}
  static final class SearchPage{final List<Book> books=new ArrayList<>();boolean more;int next;String searchId,passback;}
  SearchPage search(String keyword,int offset,String searchId)throws Exception{return search(keyword,offset,searchId,"0");}
  SearchPage search(String keyword,int offset,String searchId,String passback)throws Exception{
    JSONObject r=(JSONObject)BackendClient.call(context,"search",new JSONObject().put("keyword",keyword).put("offset",offset).put("search_id",searchId==null?"":searchId).put("passback",passback==null?"0":passback));SearchPage page=new SearchPage();JSONArray books=r.getJSONArray("books");for(int i=0;i<books.length();i++)page.books.add(new Book(books.getJSONObject(i)));page.more=r.optBoolean("more");page.next=r.getInt("next");page.searchId=r.optString("search_id");page.passback=r.optString("passback","0");return page;
  }
  static void id(String s){if(s==null||!s.matches("[0-9]+"))throw new IllegalArgumentException("书籍或章节标识不可用");}
  Book detail(String id)throws Exception{id(id);Book b=new Book((JSONObject)BackendClient.call(context,"detail",new JSONObject().put("book",id)));store.updateBook(b);return b;}
  JSONArray directory(String id,boolean force)throws Exception{id(id);JSONArray cached=store.directory(id);if(cached!=null&&!force)return cached;JSONArray out=(JSONArray)BackendClient.call(context,"directory",new JSONObject().put("book",id).put("force",force));if(out.length()==0)throw new IOException("目录为空");store.directory(id,out);return out;}
  JSONArray pageMetadata(String book,JSONArray directory)throws Exception{
    SharedPreferences cache=context.getSharedPreferences("page_metadata",0);JSONArray saved=null;try{saved=new JSONArray(cache.getString(book,"[]"));}catch(JSONException ignored){}if(metadataCovers(saved,directory))return saved;
    JSONArray out=(JSONArray)BackendClient.call(context,"page-metadata",new JSONObject().put("book",book));if(!metadataCovers(out,directory))throw new IOException("章节字数目录不完整，请刷新目录后重试");cache.edit().putString(book,out.toString()).apply();return out;
  }
  static boolean metadataCovers(JSONArray metadata,JSONArray directory){if(metadata==null||directory==null||directory.length()==0)return false;Set<String> ids=new HashSet<>();for(int i=0;i<metadata.length();i++){JSONObject item=metadata.optJSONObject(i);if(item!=null&&item.optInt("word_count",-1)>=0)ids.add(item.optString("id"));}for(int i=0;i<directory.length();i++)if(!ids.contains(directory.optJSONObject(i).optString("id")))return false;return true;}
  synchronized JSONObject chapter(String book,String chapter)throws Exception{return chapter(book,chapter,false);}
  synchronized JSONObject chapter(String book,String chapter,boolean force)throws Exception{
    id(book);id(chapter);JSONObject cached=store.chapter(book,chapter);if(!force&&cached!=null&&!BodyImages.needsUpgrade(cached))return cached;
    try{
      JSONObject result=chapterFetcher.fetch(book,chapter);
      if(!force&&cached!=null){
        if(!BodyImages.sameBody(cached,result))return BodyImages.fallback(cached,"章节内容已有变化，点击确认刷新").put("image_refresh",true);
        if(BodyImages.needsUpgrade(result))return BodyImages.fallback(cached,"图片信息暂不可用，点击重试");
      }
      store.chapter(book,chapter,result);return result;
    }catch(Exception e){if(cached!=null&&!force)return BodyImages.fallback(cached,"正文图片信息未补齐，联网后点击重试");throw e;}
  }
}
