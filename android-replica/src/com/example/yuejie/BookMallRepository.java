package com.example.yuejie;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

final class BookMallRepository {
  interface Callback { void done(List<Book> books, String error); }
  static final ExecutorService IO=Executors.newFixedThreadPool(3);
  static final Handler MAIN=new Handler(Looper.getMainLooper());
  static void fetch(final Callback callback) {
    IO.execute(() -> {
      List<Book> books=new ArrayList<>(); String error=null; HttpURLConnection c=null;
      try {
        String q="tab_type=0&tab_index=0&offset=0&client_req_type=0&bottom_tab_type=0&client_fetch_unlimited_mode=FIRST_FETCH&screen_width_px=1080&device_level=high&session_uuid="+UUID.randomUUID()+"&aid=1967&app_name=novelapp&version_code=73932&device_platform=android";
        c=(HttpURLConnection)new URL("https://reading.snssdk.com/reading/bookapi/bookmall/tab/v?"+q).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","ReadingDog/0.2 (Android)");
        int http=c.getResponseCode(); if(http!=200)throw new IOException("HTTP "+http);
        StringBuilder body=new StringBuilder(); try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){String l;while((l=r.readLine())!=null)body.append(l);}
        JSONObject root=new JSONObject(body.toString());if(root.optInt("code",-1)!=0)throw new IOException(root.optString("message","接口暂不可用"));
        collect(root.opt("data"),books,new HashSet<String>());if(books.isEmpty())throw new IOException("本次推荐暂无书籍");
        Log.i("ReadingDogSource","host=reading.snssdk.com http="+http+" business=0 bytes="+body.length()+" books="+books.size());
      }catch(Exception e){error=e.getMessage();Log.w("ReadingDogSource","fetch failed: "+error);}finally{if(c!=null)c.disconnect();}
      final String err=error;MAIN.post(()->callback.done(books,err));
    });
  }
  static void collect(Object node,List<Book> out,Set<String> seen)throws JSONException {
    if(node instanceof JSONObject){JSONObject o=(JSONObject)node;
      if(!o.optString("book_id").isEmpty()&&!o.optString("book_name").isEmpty()) {Book b=new Book(o);if(seen.add(b.id))out.add(b);return;}
      Iterator<String> k=o.keys();while(k.hasNext())collect(o.opt(k.next()),out,seen);
    }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collect(a.opt(i),out,seen);}
  }
}
