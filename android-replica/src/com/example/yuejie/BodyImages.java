package com.example.yuejie;
import org.json.*;
import java.util.*;

/** Image offsets address the unmodified UTF-16 chapter text, including U+FFFC. */
final class BodyImages {
 static final int VERSION=1;
 static final class Image {
  final int offset,width,height;final String url;
  Image(int offset,String url,int width,int height){this.offset=offset;this.url=url;this.width=width;this.height=height;}
 }
 static Map<Integer,Image> parse(String text,JSONArray values){
  Map<Integer,Image> out=new HashMap<>();
  for(int i=0;values!=null&&i<values.length();i++){JSONObject v=values.optJSONObject(i);if(v==null)continue;int offset=v.optInt("offset",-1);if(offset<0||offset>=text.length()||text.charAt(offset)!='\ufffc'||out.containsKey(offset))continue;out.put(offset,new Image(offset,v.optString("url"),dimension(v.optInt("width")),dimension(v.optInt("height"))));}
  return Collections.unmodifiableMap(out);
 }
 static int dimension(int n){return n>0&&n<=100000?n:0;}
 static boolean needsUpgrade(JSONObject chapter){return chapter!=null&&chapter.optString("text").indexOf('\ufffc')>=0&&(chapter.optInt("image_version")!=VERSION||chapter.optJSONArray("images")==null);}
 static boolean sameBody(JSONObject a,JSONObject b){
  if(!a.optString("id").equals(b.optString("id"))||!a.optString("text").equals(b.optString("text"))||a.optBoolean("paragraph_mapping")!=b.optBoolean("paragraph_mapping"))return false;
  JSONArray x=a.optJSONArray("paragraph_meta"),y=b.optJSONArray("paragraph_meta");if(x==null||y==null)return x==y;if(x.length()!=y.length())return false;
  for(int i=0;i<x.length();i++){JSONObject p=x.optJSONObject(i),q=y.optJSONObject(i);if(p==null||q==null)return false;for(String key:new String[]{"id","text","start","end"})if(!Objects.equals(p.opt(key),q.opt(key)))return false;}
  return true;
 }
 static JSONObject fallback(JSONObject cached,String reason)throws JSONException {return new JSONObject(cached.toString()).put("image_notice",reason);}
}
