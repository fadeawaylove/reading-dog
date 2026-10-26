package com.example.yuejie;
import android.content.*;import org.json.*;import java.util.*;
/** Per-chapter model counts, corrected by actual layouts, with cumulative global offsets. */
final class BookPageIndex {
 final String profile,book;final SharedPreferences prefs;final JSONArray directory;final String[] ids;
 final int[] counts,words;final long[] prefix,wordPrefix;final JSONObject actual;int corrections;
 static String profile(int w,int h,float density,float scaled,ReaderSettings s){return "tomato73932-v1:"+w+":"+h+":"+density+":"+scaled+":"+s.font+":"+s.spacing+":"+s.serif+":"+s.fontWeight+":"+s.comments;}
 BookPageIndex(Context c,String book,JSONArray directory,JSONArray metadata,ReaderPaginator layout,float scaled,ReaderSettings settings)throws JSONException{
  this.book=book;this.directory=directory;profile=profile(layout.width,layout.height,layout.density,scaled,settings);prefs=c.getSharedPreferences("book_page_index",0);
  JSONObject saved;try{saved=new JSONObject(prefs.getString(book,"{}"));}catch(JSONException e){saved=new JSONObject();}
  JSONObject stored=profile.equals(saved.optString("profile"))?saved.optJSONObject("actual"):null;actual=stored==null?new JSONObject():stored;
  Map<String,JSONObject> items=new HashMap<>();for(int i=0;i<metadata.length();i++){JSONObject item=metadata.getJSONObject(i);items.put(item.getString("id"),item);}
  int n=directory.length();ids=new String[n];counts=new int[n];words=new int[n];prefix=new long[n+1];wordPrefix=new long[n+1];
  double font=layout.paint.getTextSize(),lineSpacing=Math.ceil(font*settings.lineHeight())-font;
  for(int i=0;i<n;i++){JSONObject item=directory.getJSONObject(i);ids[i]=item.getString("id");JSONObject info=items.get(ids[i]);if(info==null||info.optInt("word_count",-1)<0)throw new JSONException("Missing chapter word count: "+ids[i]);words[i]=info.getInt("word_count");counts[i]=TomatoPageModel.pages(item.getString("title").length(),words[i],font,lineSpacing,layout.paragraphGap,layout.width-2*layout.left,layout.bottom-layout.top);JSONObject known=actual.optJSONObject(ids[i]);if(known!=null&&known.optInt("words",-1)==words[i]&&item.getString("title").equals(known.optString("title"))&&known.optInt("pages")>0)counts[i]=known.getInt("pages");}
  rebuild();
 }
 void rebuild(){for(int i=0;i<counts.length;i++){prefix[i+1]=prefix[i]+counts[i];wordPrefix[i+1]=wordPrefix[i]+words[i];}}
 boolean record(String id,ReaderPaginator layout){for(int i=0;i<ids.length;i++)if(ids[i].equals(id)){int pages=layout.pages.size();JSONObject known=actual.optJSONObject(id);boolean changed=counts[i]!=pages;counts[i]=pages;try{actual.put(id,new JSONObject().put("pages",pages).put("words",words[i]).put("title",layout.title));if(known==null||known.optInt("pages")!=pages)prefs.edit().putString(book,new JSONObject().put("profile",profile).put("actual",actual).toString()).apply();}catch(JSONException e){throw new IllegalStateException(e);}if(changed){corrections++;rebuild();}return changed;}return false;}
 long page(ReaderSurface.Frame f){return prefix[f.chapterIndex]+f.pageIndex+1;}
 long total(){return prefix[counts.length];}
 double percent(ReaderSurface.Frame f){long all=wordPrefix[words.length];float fraction=(f.pageIndex+1f)/f.layout.pages.size();return all>=100?((fraction*words[f.chapterIndex]+wordPrefix[f.chapterIndex])/all)*100f:((f.chapterIndex+fraction)/counts.length)*100f;}
 String label(ReaderSurface.Frame f){return page(f)+"/"+total()+"  ·  "+String.format(Locale.CHINA,"%.2f%%",percent(f));}
}
