package com.example.yuejie;

import org.json.*;
import java.util.*;

/** A matched body, paragraph identity and counts travel together, including offline. */
final class ParagraphSnapshot {
 final JSONObject metadata,stats;final Map<Integer,Long> counts;
 ParagraphSnapshot(JSONObject metadata,JSONObject stats){
  this.metadata=metadata;this.stats=stats;Map<Integer,Long> out=new HashMap<>();
  JSONArray paragraphs=metadata.optJSONArray("paragraph_meta");
  for(int i=0;paragraphs!=null&&i<paragraphs.length();i++){JSONObject p=paragraphs.optJSONObject(i);if(p==null)continue;long count=stats.optLong(p.optString("id"));if(count>0)out.put(p.optInt("start"),count);}
  counts=Collections.unmodifiableMap(out);
 }
 boolean matches(JSONObject body){return body!=null&&metadata.optString("id").equals(body.optString("id"))&&metadata.optString("text").equals(body.optString("text"));}
 static ParagraphSnapshot cached(ParagraphCommentApi api,String book,JSONObject body){
  if(body==null)return null;String id=body.optString("id");
  CommentStore.Entry mapping=api.cache.get(ParagraphCommentApi.key("mapping",book,id,"","",""));
  if(mapping==null||!mapping.value.optString("text").equals(body.optString("text")))return null;
  String version=mapping.value.optString("item_version");if(version.isEmpty())return null;
  CommentStore.Entry stats=api.cache.get(ParagraphCommentApi.key("stats",book,id,version,"",""));
  return stats==null?null:new ParagraphSnapshot(mapping.value,stats.value);
 }
}
