package com.example.yuejie;
import android.graphics.Color;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.concurrent.*;import static com.example.yuejie.PaperUi.*;
/** Owns requests and overlays; changed inline elements request a reflow when the reader is idle. */
final class ParagraphComments {
 final ReaderActivity a;ParagraphCommentApi api;final ThreadPoolExecutor worker=(ThreadPoolExecutor)Executors.newFixedThreadPool(1);
 String chapterId="",version="",attached="",error="",paraId="",paraText="",cursor="";JSONObject metadata,stats;final Map<Integer,Long> counts=new HashMap<>();final Map<String,Map<Integer,Long>> chapterCounts=new ConcurrentHashMap<>();final Map<String,ParagraphSnapshot> snapshots=new ConcurrentHashMap<>();volatile int generation,panelGeneration;volatile boolean closed;boolean loading,more,cacheOnly;FrameLayout layer,imageLayer;LinearLayout panel,rows;ListView commentList;BaseAdapter adapter;final List<JSONObject> displayed=new ArrayList<>();TextView status,footer,emptyState;final Set<String> seen=new HashSet<>();final List<String> imageUrls=new ArrayList<>();Future<?> task;
 ParagraphComments(ReaderActivity a){this.a=a;api=new ParagraphCommentApi(a,a.library);}
 final ThreadPoolExecutor warmer=(ThreadPoolExecutor)Executors.newFixedThreadPool(1);
 volatile int warmGeneration;String warmWindow="";
 // Disk reads run only on IO/typesetting workers. The UI consumes immutable snapshots.
 Map<Integer,Long> cachedCounts(JSONObject body){
  ParagraphSnapshot value=ParagraphSnapshot.cached(api,a.bookId,body);String id=body.optString("id");
  if(value!=null){snapshots.put(id,value);chapterCounts.put(id,value.counts);return value.counts;}
  snapshots.remove(id);chapterCounts.remove(id);return Collections.emptyMap();
 }
 void stopWarm(){warmGeneration++;warmWindow="";warmer.getQueue().clear();}
 void warmUpcoming(){
  if(closed||!a.foreground||!a.settings.comments||a.directory==null){stopWarm();return;}
  List<String> targets=new ArrayList<>();for(int n=1;n<=ChapterPrefetcher.AHEAD&&a.index+n<a.directory.length();n++)targets.add(a.chapterId(a.index+n));
  String window=targets.toString();if(!window.equals(warmWindow)){warmGeneration++;warmWindow=window;}
  final int token=warmGeneration;warmer.getQueue().clear();
  Set<String> keep=new HashSet<>(targets);keep.add(a.chapterId(a.index));if(a.index>0)keep.add(a.chapterId(a.index-1));snapshots.keySet().retainAll(keep);chapterCounts.keySet().retainAll(keep);
  warmer.execute(()->{for(String id:targets){if(!warmValid(token))return;JSONObject body=a.library.chapter(a.bookId,id);
   // Next chapter has priority; its body completion schedules another pass.
   if(body==null)return;
   ParagraphSnapshot old=ParagraphSnapshot.cached(api,a.bookId,body);if(old!=null)publishWarm(token,id,old);
   try{JSONObject meta=api.metadata(a.bookId,id,()->warmValid(token));if(!warmValid(token))return;String v=meta.optString("item_version");CommentStore.Entry stats=api.cache.get(ParagraphCommentApi.key("stats",a.bookId,id,v,"",""));JSONObject result=stats!=null&&!stats.stale?stats.value:api.stats(a.bookId,id,v);if(warmValid(token))publishWarm(token,id,new ParagraphSnapshot(meta,result));}catch(Exception ignored){}
  }});
 }
 boolean warmValid(int token){return !closed&&token==warmGeneration;}
 void publishWarm(int token,String id,ParagraphSnapshot value){NovelApi.MAIN.post(()->{if(!warmValid(token)||!a.foreground||!a.settings.comments)return;for(JSONObject body:a.chapters.values())if(id.equals(body.optString("id"))&&!value.matches(body))return;
  snapshots.put(id,value);Map<Integer,Long> previous=chapterCounts.put(id,value.counts);
  if(id.equals(chapterId)&&value.matches(a.chapter)){metadata=value.metadata;version=metadata.optString("item_version");setStats(value.stats);}
  if(previous==null||!previous.equals(value.counts))a.neighborCommentsChanged(id);
 });}
 void applyCached(JSONObject data){if(!a.settings.comments||data==null||!data.optString("id").equals(chapterId))return;ParagraphSnapshot value=snapshots.get(chapterId);if(value!=null&&value.matches(data)){metadata=value.metadata;version=metadata.optString("item_version");setStats(value.stats);}}
 void attach(JSONObject data){
  if(closed||!a.foreground||data==null)return;String id=data.optString("id");if(id.equals(attached))return;dismiss();attached=id;chapterId=id;metadata=null;stats=null;version="";counts.clear();error="";int token=++generation;if(task!=null)task.cancel(false);
  if(!a.settings.comments){stopWarm();a.surface.invalidate();return;}
  ParagraphSnapshot memory=snapshots.get(id);if(memory!=null&&memory.matches(data)){metadata=memory.metadata;version=metadata.optString("item_version");setStats(memory.stats);}
  task=worker.submit(()->{if(!valid(token,id))return;
   ParagraphSnapshot cached=ParagraphSnapshot.cached(api,a.bookId,data);if(cached!=null)NovelApi.MAIN.post(()->{if(valid(token,id)){metadata=cached.metadata;version=metadata.optString("item_version");setStats(cached.stats);}});
   try{JSONObject meta=api.metadata(a.bookId,id,()->valid(token,id));if(!valid(token,id))return;NovelApi.MAIN.post(()->{if(valid(token,id)){metadata=meta;version=meta.optString("item_version");loadStats(token,id,false);}});}
   catch(Exception e){NovelApi.MAIN.post(()->{if(valid(token,id))error=cached==null?"段评暂不可用":"当前使用缓存段评";});}
  });
 }
 boolean valid(int token,String id){return !closed&&token==generation&&id.equals(chapterId);}
 void loadStats(int token,String id,boolean offline){String v=version;worker.execute(()->{if(!valid(token,id))return;CommentStore.Entry old=api.cache.get(ParagraphCommentApi.key("stats",a.bookId,id,v,"",""));if(old!=null)NovelApi.MAIN.post(()->{if(valid(token,id))setStats(old.value);});if(old!=null&&!old.stale)return;if(!valid(token,id))return;try{JSONObject result=api.stats(a.bookId,id,v);NovelApi.MAIN.post(()->{if(valid(token,id)){setStats(result);error="";}});}catch(Exception e){NovelApi.MAIN.post(()->{if(valid(token,id))error=old==null?"段评数量加载失败，点击菜单重试":"当前使用缓存段评";});}});}
 void setStats(JSONObject value){stats=value;ParagraphSnapshot snapshot=new ParagraphSnapshot(metadata,value);counts.clear();counts.putAll(snapshot.counts);chapterCounts.put(chapterId,snapshot.counts);snapshots.put(chapterId,snapshot);a.commentsChanged();a.surface.invalidate();}
 Map<Integer,Long> counts(String id){return id.equals(chapterId)?counts:chapterCounts.getOrDefault(id,Collections.emptyMap());}
 void retry(){attached="";attach(a.chapter);}
 void open(int start){if(metadata==null||!a.settings.comments||a.surface.animating())return;JSONArray paras=metadata.optJSONArray("paragraph_meta");JSONObject chosen=null;for(int i=0;paras!=null&&i<paras.length();i++){JSONObject p=paras.optJSONObject(i);if(p.optInt("start",-1)==start){chosen=p;break;}}if(chosen==null)return;dismiss();a.hideOverlays();a.surface.cancelAnimation();paraId=chosen.optString("id");paraText=chosen.optString("text");cursor="";more=true;loading=false;seen.clear();imageUrls.clear();panelGeneration++;
  layer=new FrameLayout(a);View shade=new View(a);shade.setBackgroundColor(0x55000000);shade.setOnClickListener(v->dismiss());layer.addView(shade,new FrameLayout.LayoutParams(-1,-1));panel=new CommentSheet(a,()->dismiss());panel.setPadding(a.d(20),a.d(12),a.d(20),a.d(16));panel.setBackground(bg(a,a.settings.surface(),18));LinearLayout head=row(a);TextView title=text(a,"段评 · "+(stats==null?0:stats.optLong(paraId)),18,a.ink());head.addView(title,new LinearLayout.LayoutParams(0,a.d(44),1));head.addView(a.item("关闭",()->dismiss()));panel.addView(head);TextView quote=text(a,paraText,13,a.muted());quote.setMaxLines(3);quote.setEllipsize(android.text.TextUtils.TruncateAt.END);quote.setPadding(a.d(12),a.d(10),a.d(12),a.d(10));quote.setBackground(bg(a,a.settings.night?0xff343a31:0xffe8e7dc,8));quote.setContentDescription("所选段落："+paraText);panel.addView(quote,new LinearLayout.LayoutParams(-1,-2));status=text(a,"正在加载段评…",11,a.muted());status.setPadding(0,a.d(8),0,a.d(8));panel.addView(status);commentList=new ListView(a);commentList.setVerticalScrollBarEnabled(false);commentList.setDivider(new android.graphics.drawable.ColorDrawable(a.settings.night?0xff3b4037:0xffe1dfd3));commentList.setDividerHeight(a.d(1));adapter=new BaseAdapter(){public int getCount(){return displayed.size();}public Object getItem(int p){return displayed.get(p);}public long getItemId(int p){return p;}public View getView(int p,View convert,ViewGroup parent){return commentRow(displayed.get(p));}};commentList.setAdapter(adapter);((CommentSheet)panel).list=commentList;FrameLayout listArea=new FrameLayout(a);listArea.addView(commentList,new FrameLayout.LayoutParams(-1,-1));TextView empty=text(a,"正在加载段评…",14,a.muted());emptyState=empty;empty.setGravity(Gravity.CENTER);listArea.addView(empty,new FrameLayout.LayoutParams(-1,-1));commentList.setEmptyView(empty);panel.addView(listArea,new LinearLayout.LayoutParams(-1,0,1));footer=a.item("正在加载…",()->load());panel.addView(footer,new LinearLayout.LayoutParams(-1,a.d(48)));commentList.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){}public void onScroll(AbsListView v,int first,int visible,int total){if(more&&!loading&&total>0&&first+visible>=total)load();}});layer.addView(panel,new FrameLayout.LayoutParams(-1,(int)(a.height*.75),Gravity.BOTTOM));a.root.addView(layer,new FrameLayout.LayoutParams(-1,-1));layer.bringToFront();load();
 }
 void load(){if(layer==null||loading||!more)return;loading=true;final int token=panelGeneration;final String id=chapterId,v=version,pid=paraId,pageCursor=cursor,expected=paraText;footer.setText("正在加载…");worker.execute(()->{if(!panelValid(token,id,pid))return;String key=ParagraphCommentApi.key("page",a.bookId,id,v,pid,pageCursor);CommentStore.Entry old=api.cache.get(key);if(old!=null)NovelApi.MAIN.post(()->{if(panelValid(token,id,pid)){apply(old.value,pageCursor,true);status.setText(old.stale?"缓存段评 · 正在刷新":"已缓存的段评");}});if(old!=null&&!old.stale&&old.value.optInt("schema")>=3){NovelApi.MAIN.post(()->{if(panelValid(token,id,pid)){loading=false;updateFooter();}});return;}try{JSONObject result=api.comments(a.bookId,id,v,pid,pageCursor,expected);NovelApi.MAIN.post(()->{if(panelValid(token,id,pid)){apply(result,pageCursor,false);loading=false;cacheOnly=false;status.setText("默认排序 · 只读");updateFooter();}});}catch(Exception e){NovelApi.MAIN.post(()->{if(!panelValid(token,id,pid))return;loading=false;cacheOnly=old!=null||!seen.isEmpty();status.setText(cacheOnly?"缓存内容 · 联网后可重试":"段评加载失败："+(e.getMessage()==null?"请重试":e.getMessage()));if(emptyState!=null&&seen.isEmpty())emptyState.setText("段评暂时无法加载");footer.setText("点击重试");footer.setOnClickListener(view->{if(old!=null){cursor=pageCursor;more=true;}load();});});}});}
 boolean panelValid(int token,String id,String pid){return !closed&&layer!=null&&token==panelGeneration&&id.equals(chapterId)&&pid.equals(paraId);}
 void apply(JSONObject result,String requested,boolean cached){
  if(requested.isEmpty()){displayed.clear();seen.clear();}
  Map<String,Integer> positions=new HashMap<>();for(int i=0;i<displayed.size();i++)positions.put(displayed.get(i).optString("id"),i);
  JSONArray items=result.optJSONArray("items");
  for(int i=0;items!=null&&i<items.length();i++){
   JSONObject item=items.optJSONObject(i);if(item==null)continue;String id=item.optString("id");Integer position=positions.get(id);
   if(position!=null)displayed.set(position,item);
   else{positions.put(id,displayed.size());displayed.add(item);seen.add(id);}
  }
  // Refresh image references too: a replaced comment may have changed its images.
  imageUrls.clear();for(JSONObject item:displayed){JSONArray pictures=item.optJSONArray("images");for(int j=0;pictures!=null&&j<pictures.length();j++){JSONObject picture=pictures.optJSONObject(j);if(picture!=null)imageUrls.add(picture.optString("url"));}}
  cursor=result.optString("cursor");more=result.optBoolean("more");if(emptyState!=null)emptyState.setText("这一段还没有评论");adapter.notifyDataSetChanged();updateFooter();
 }
 void updateFooter(){if(footer==null)return;footer.setText(more?"加载更多":"已显示全部段评");footer.setOnClickListener(v->load());}
 View commentRow(JSONObject c){LinearLayout row=row(a);row.setGravity(Gravity.TOP);row.setPadding(0,a.d(16),0,a.d(16));ImageView avatar=new ImageView(a);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackground(bg(a,a.settings.night?0xff3b4438:0xffdce2d5,18));avatar.setClipToOutline(true);avatar.setContentDescription(c.optString("name")+"的头像");row.addView(avatar,new LinearLayout.LayoutParams(a.d(32),a.d(32)));String url=c.optString("avatar");if(url.startsWith("https://"))CommentImages.show(a,url,avatar);LinearLayout copy=col(a);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=a.d(12);row.addView(copy,lp);LinearLayout meta=row(a);TextView name=text(a,c.optString("name","读者"),12,a.muted());name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);meta.addView(name,new LinearLayout.LayoutParams(0,a.d(24),1));TextView likes=text(a,"赞 "+c.optLong("likes"),11,a.muted());likes.setContentDescription("点赞数 "+c.optLong("likes"));meta.addView(likes);copy.addView(meta);TextView body=text(a,c.optString("text"),15,a.ink());body.setLineSpacing(a.d(3),1.15f);copy.addView(body);JSONArray images=c.optJSONArray("images");for(int i=0;images!=null&&i<images.length();i++){JSONObject image=images.optJSONObject(i);String src=image.optString("url");if(!src.startsWith("https://"))continue;ImageView im=new ImageView(a);im.setScaleType(ImageView.ScaleType.FIT_CENTER);im.setBackground(bg(a,a.settings.night?0xff333a31:0xffe7e8df,8));im.setContentDescription("段评图片，点击查看大图");LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,a.d(170));size.topMargin=a.d(10);copy.addView(im,size);CommentImages.show(a,src,im);im.setOnClickListener(v->{CommentImages.show(a,src,im);preview(src);});}String when=commentTime(c.optLong("create_timestamp"));if(!when.isEmpty()){TextView time=text(a,when,11,a.muted());time.setPadding(0,a.d(8),0,0);time.setContentDescription("评论时间："+when);copy.addView(time);}return row;}
 static String commentTime(long seconds){if(seconds<=0||seconds>253402300799L)return "";return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.ROOT).format(new Date(seconds*1000));}
 void preview(String url){if(layer==null)return;closeImage();imageLayer=new FrameLayout(a);imageLayer.setBackgroundColor(0xff111511);ImageView im=new ImageView(a);im.setScaleType(ImageView.ScaleType.FIT_CENTER);imageLayer.addView(im,new FrameLayout.LayoutParams(-1,-1));TextView close=text(a,"关闭图片",14,Color.WHITE);close.setGravity(Gravity.CENTER);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(a.d(110),a.d(52),Gravity.TOP|Gravity.RIGHT);lp.topMargin=a.d(20);imageLayer.addView(close,lp);close.setOnClickListener(v->closeImage());im.setContentDescription("评论图片预览，点击重试加载");im.setOnClickListener(v->CommentImages.show(a,url,im));CommentImages.show(a,url,im);layer.addView(imageLayer,new FrameLayout.LayoutParams(-1,-1));}
 void closeImage(){if(imageLayer!=null&&layer!=null)layer.removeView(imageLayer);imageLayer=null;}
 boolean back(){if(imageLayer!=null){closeImage();return true;}if(layer!=null){dismiss();return true;}return false;}
 void dismiss(){panelGeneration++;loading=false;closeImage();if(layer!=null)a.root.removeView(layer);layer=null;panel=null;rows=null;footer=null;status=null;emptyState=null;commentList=null;adapter=null;displayed.clear();}
 void pause(){stopWarm();generation++;attached="";dismiss();if(task!=null)task.cancel(false);worker.getQueue().clear();}
 void close(){closed=true;pause();worker.shutdownNow();warmer.shutdownNow();}
}
