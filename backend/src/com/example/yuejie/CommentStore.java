package com.example.yuejie;
import org.json.*;
import java.util.*;
final class CommentStore {
  static final class Entry {final JSONObject value;final boolean stale;Entry(JSONObject v,boolean s){value=v;stale=s;}}
  final LinkedHashMap<String,String> values=new LinkedHashMap<>(16,.75f,true);final Map<String,Long> times=new HashMap<>();long bytes;
  CommentStore(BackendContext c){}
  synchronized Entry get(String k){String s=values.get(k);return s==null?null:new Entry(new JSONObject(s),System.currentTimeMillis()-times.get(k)>1800000);}
  synchronized void put(String k,JSONObject v){String s=v.toString(),old=values.put(k,s);bytes+=s.length()*2L-(old==null?0:old.length()*2L);times.put(k,System.currentTimeMillis());while(bytes>8*1024*1024&&!values.isEmpty()){String first=values.keySet().iterator().next();bytes-=values.remove(first).length()*2L;times.remove(first);}}
}
