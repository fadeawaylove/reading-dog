package com.example.yuejie;

import android.text.Html;
import android.text.Spanned;
import android.text.style.ImageSpan;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Keep the original Android HTML text and UTF-16 offsets; extract images from spans. */
final class ChapterContent {
 static String attribute(String tag,String name){
  Matcher m=Pattern.compile("(?i)(?:^|\\s)"+name+"\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").matcher(tag);
  if(!m.find())return "";String value=m.group(1)!=null?m.group(1):m.group(2)!=null?m.group(2):m.group(3);
  return Html.fromHtml(value,Html.FROM_HTML_MODE_LEGACY).toString();
 }
 static int dimension(String value){try{return BodyImages.dimension(Integer.parseInt(value.trim().replaceFirst("(?i)px$","")));}catch(NumberFormatException e){return 0;}}
 static JSONObject parse(String id,String title,String html)throws Exception{
  html=html.replaceAll("(?is)<(script|style|header)\\b[^>]*>.*?</\\1>","");
  Matcher paragraphs=Pattern.compile("(?is)<p\\b([^>]*)>(.*?)</p>").matcher(html);
  StringBuilder text=new StringBuilder();JSONArray values=new JSONArray(),meta=new JSONArray(),images=new JSONArray();Set<String> ids=new HashSet<>();boolean mapped=true;
  while(paragraphs.find()){
   String markup=paragraphs.group(2);Spanned decoded=Html.fromHtml(markup,Html.FROM_HTML_MODE_LEGACY);
   String raw=decoded.toString(),value=raw.trim();if(value.isEmpty())continue;
   int leading=0;while(leading<raw.length()&&raw.charAt(leading)<=32)leading++;
   if(text.length()>0)text.append("\n\n");int start=text.length();
   String pid=attribute(paragraphs.group(1),"idx");if(!pid.matches("[0-9]+")||!ids.add(pid))mapped=false;
   values.put(value);meta.put(new JSONObject().put("id",pid).put("start",start).put("end",start+value.length()).put("text",value));
   ImageSpan[] spans=decoded.getSpans(0,decoded.length(),ImageSpan.class);Arrays.sort(spans,Comparator.comparingInt(decoded::getSpanStart));
   Matcher tags=Pattern.compile("(?is)<img\\b([^>]*)>").matcher(markup);int i=0;
   while(tags.find()&&i<spans.length){ImageSpan span=spans[i++];int offset=decoded.getSpanStart(span)-leading;if(offset<0||offset>=value.length()||value.charAt(offset)!='\ufffc')continue;
    String url=attribute(tags.group(1),"src");if(url.isEmpty())url=attribute(tags.group(1),"data-src");if(url.startsWith("//"))url="https:"+url;url=ParagraphCommentApi.secure(url);
    images.put(new JSONObject().put("offset",start+offset).put("url",url).put("width",dimension(attribute(tags.group(1),"width"))).put("height",dimension(attribute(tags.group(1),"height"))));
   }
   text.append(value);
  }
  if(values.length()==0)throw new IOException("当前章节没有可读正文");
  return new JSONObject().put("id",id).put("title",title).put("text",text.toString()).put("paragraphs",values).put("paragraph_meta",meta).put("paragraph_mapping",mapped).put("image_version",BodyImages.VERSION).put("images",images);
 }
}
