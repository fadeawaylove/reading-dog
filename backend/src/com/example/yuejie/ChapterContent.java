package com.example.yuejie;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Canonical body offsets remain identical to the legacy HTML decoder. */
final class ChapterContent {
 static final int IMAGE_VERSION=1;
 static JSONObject parse(String id,String title,String html)throws IOException {
  html=html.replaceAll("(?is)<(script|style|header)\\b[^>]*>.*?</\\1>","");
  Matcher m=Pattern.compile("(?is)<p\\b([^>]*)>(.*?)</p>").matcher(html);
  JSONArray paragraphs=new JSONArray(),meta=new JSONArray(),images=new JSONArray();
  Set<String> ids=new HashSet<>();boolean mapped=true;StringBuilder text=new StringBuilder();
  while(m.find()){
   HtmlText.Decoded decoded=HtmlText.decodeImages(m.group(2));String raw=decoded.text.toString(),value=raw.trim();if(value.isEmpty())continue;
   int leading=0;while(leading<raw.length()&&raw.charAt(leading)<=32)leading++;
   if(text.length()>0)text.append("\n\n");int start=text.length();
   Matcher idx=Pattern.compile("(?i)\\bidx=[\"']([0-9]+)[\"']").matcher(m.group(1));String pid=idx.find()?idx.group(1):"";
   if(pid.isEmpty()||!ids.add(pid))mapped=false;
   paragraphs.put(value);meta.put(new JSONObject().put("id",pid).put("start",start).put("end",start+value.length()).put("text",value));
   for(int i=0;i<decoded.images.length();i++){JSONObject image=decoded.images.getJSONObject(i);image.put("offset",start+image.getInt("offset")-leading);images.put(image);}
   text.append(value);
  }
  if(paragraphs.length()==0)throw new IOException("当前章节没有可读正文");
  return new JSONObject().put("id",id).put("title",title).put("text",text.toString()).put("paragraphs",paragraphs).put("paragraph_meta",meta).put("paragraph_mapping",mapped).put("image_version",IMAGE_VERSION).put("images",images);
 }
 static boolean current(JSONObject chapter){return chapter!=null&&chapter.optInt("image_version")==IMAGE_VERSION&&chapter.optJSONArray("images")!=null;}
}
