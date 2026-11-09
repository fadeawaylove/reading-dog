package com.example.yuejie;
import org.json.*;
import java.util.*;
/** Bounded process cache. User library and progress live exclusively in SyncStore. */
final class LocalLibrary {
  final LinkedHashMap<String,String> cache=new LinkedHashMap<>(16,.75f,true);
  long bytes;
  synchronized JSONObject chapter(String book,String chapter){String s=cache.get("c:"+book+":"+chapter);return s==null?null:new JSONObject(s);}
  synchronized void chapter(String book,String chapter,JSONObject v){put("c:"+book+":"+chapter,v.toString());}
  synchronized JSONArray directory(String book){String s=cache.get("d:"+book);return s==null?null:new JSONArray(s);}
  synchronized void directory(String book,JSONArray v){put("d:"+book,v.toString());}
  void updateBook(Book book){}
  synchronized void put(String k,String v){String old=cache.put(k,v);bytes+=v.length()*2L-(old==null?0:old.length()*2L);while(bytes>16*1024*1024&&!cache.isEmpty()){String first=cache.keySet().iterator().next();bytes-=cache.remove(first).length()*2L;}}
}
