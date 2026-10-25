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
  static final String BASE="https://api5-normal-sinfonlinec.fqnovel.com";
  interface Transport{String request(String url,Map<String,String> headers,JSONObject body)throws Exception;}
  final Context context;final LocalLibrary store;final Transport transport;byte[] sessionKey;String keyIdentity="";
  NovelApi(Context c,LocalLibrary s){this(c,s,NovelApi::http);}
  NovelApi(Context c,LocalLibrary s,Transport transport){context=c.getApplicationContext();store=s;this.transport=transport;}
  JSONObject asset(String name)throws Exception{try(InputStream in=context.getAssets().open(name)){return new JSONObject(new String(ProtocolCrypto.read(in),"UTF-8"));}}
  static String http(String url,Map<String,String> headers,JSONObject body)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(25000);
    try{for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(ProtocolCrypto.utf(body.toString()));}}
      int status=c.getResponseCode();if(status!=200)throw new IOException("服务暂不可用（HTTP "+status+"）");byte[] data;try(InputStream raw=c.getInputStream();InputStream in="gzip".equalsIgnoreCase(c.getContentEncoding())?new GZIPInputStream(raw):raw){data=ProtocolCrypto.read(in);}if(data.length==0)throw new EmptyResponse();Log.i("ReadingDogApi",new URL(url).getPath()+" HTTP200 bytes="+data.length);return new String(data,"UTF-8");
    }finally{c.disconnect();}
  }
  JSONObject profile(String kind)throws Exception{synchronized(NovelApi.class){return loadProfile(kind);}}
  JSONObject loadProfile(String kind)throws Exception{
    SharedPreferences prefs=context.getSharedPreferences("api_profiles",0);String saved=prefs.getString(kind,"");if(!saved.isEmpty())return new JSONObject(saved);
    JSONObject body=asset("registration-template.json"),h=body.getJSONObject("header");long now=System.currentTimeMillis();body.put("_gen_time",now);for(String k:new String[]{"cdid","clientudid","req_id"})h.put(k,UUID.randomUUID().toString());h.put("openudid",UUID.randomUUID().toString().replace("-","").substring(0,16));h.put("apk_first_install_time",now-60000);
    Map<String,String> headers=new HashMap<>();headers.put("User-Agent","com.dragon.read.oversea.gp/68132 (Linux; U; Android 13; zh_CN; OnePlus11; Build/V291IR;tt-ok/3.12.13.4-tiktok)");JSONObject r=new JSONObject(transport.request("https://log.snssdk.com/service/2/device_register/",headers,body));String iid=r.optString("install_id_str",r.optString("install_id")),did=r.optString("device_id_str",r.optString("device_id"));if(!iid.matches("[1-9][0-9]*")||!did.matches("[1-9][0-9]*"))throw new IOException("匿名初始化失败，请重试");
    JSONObject p=asset(kind+"-defaults.json");JSONArray a=new JSONArray();a.put(new JSONArray().put("iid").put(iid));a.put(new JSONArray().put("device_id").put(did));JSONArray defaults=p.getJSONArray("query_defaults");boolean cdid=false,session=false;for(int i=0;i<defaults.length();i++){JSONArray pair=defaults.getJSONArray(i);String k=pair.getString(0);if(k.equals("bookshelf_search_plan")){a.put(new JSONArray().put("cdid").put(h.getString("cdid")));cdid=true;}if(k.equals("charging")){for(String sk:new String[]{"normal_session_id","cold_start_session_id"})a.put(new JSONArray().put(sk).put(UUID.randomUUID().toString()));session=true;}a.put(pair);}if(!cdid)a.put(new JSONArray().put("cdid").put(h.getString("cdid")));p.put("query_defaults",a);p.getJSONObject("headers").put("Cookie","store-region=cn-zj; store-region-src=did; install_id="+iid);if(!prefs.edit().putString(kind,p.toString()).commit())throw new IOException("无法保存匿名配置");return p;
  }
  static Map<String,String> headers(JSONObject p)throws Exception{Map<String,String> h=new LinkedHashMap<>();JSONObject o=p.getJSONObject("headers");Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();h.put(k,o.getString(k));}return h;}
  JSONObject signed(String path,List<String[]> pairs,JSONObject p,JSONObject body)throws Exception{
    StringBuilder q=new StringBuilder();for(String[] pair:pairs){if(q.length()>0)q.append('&');q.append(pair[0]).append('=').append(pair[1]);}long ms=System.currentTimeMillis(),ts=ms/1000;Map<String,String> h=headers(p);h.put("X-Argus",ProtocolCrypto.argus(q.toString(),ts,ProtocolCrypto.RANDOM.nextInt(0x7fffffff)));h.put("X-Ladon",ProtocolCrypto.ladon(ts,ProtocolCrypto.random(4)));h.put("X-Helios",ProtocolCrypto.ladon(ts,ProtocolCrypto.random(4)));h.put("X-Khronos",Long.toString(ts));h.put("x-ss-req-ticket",Long.toString(ms));h.put("x-reading-request",ms+"-"+ProtocolCrypto.RANDOM.nextInt(2000000000));h.put("Authorization","Bearer");JSONObject r=new JSONObject(transport.request(BASE+path+"?"+q,h,body));if(r.optInt("code",-1)!=0)throw new IOException("服务请求失败（"+r.optString("code")+"）");return r;
  }
  static String encode(String s)throws Exception{return URLEncoder.encode(s,"UTF-8").replace("+","%20").replace("*","%2A").replace("%7E","~");}
  static void before(List<String[]> pairs,String k,String[] value){int i=0;while(i<pairs.size()&&!pairs.get(i)[0].equals(k))i++;pairs.add(i,value);}
  static final class SearchPage{final List<Book> books=new ArrayList<>();boolean more;int next;String searchId,passback;}
  SearchPage search(String keyword,int offset,String searchId)throws Exception{return search(keyword,offset,searchId,"0");}
  SearchPage search(String keyword,int offset,String searchId,String passback)throws Exception{
    try{return searchRequest(keyword,offset,searchId,passback);}catch(EmptyResponse e){
      if(offset!=0)throw e;
      // A previously accepted anonymous identity can subsequently receive an empty reply.
      // Reinitialize at most once per minute, and retry once; never loop through identities.
      synchronized(NovelApi.class){SharedPreferences prefs=context.getSharedPreferences("api_profiles",0);long now=System.currentTimeMillis(),last=prefs.getLong("search_reinitialized_at",0);if(now-last<60000&&now>=last)throw e;prefs.edit().remove("search").putLong("search_reinitialized_at",now).commit();}
      Log.i("ReadingDogApi","Reinitializing anonymous search after an empty response");return searchRequest(keyword,offset,null,"0");
    }
  }
  SearchPage searchRequest(String keyword,int offset,String searchId,String passback)throws Exception{
    JSONObject p=profile("search");List<String[]> pairs=new ArrayList<>();JSONArray a=p.getJSONArray("query_defaults");for(int i=0;i<a.length();i++){JSONArray x=a.getJSONArray(i);String k=x.getString(0);if(!Arrays.asList("query","_rticket","offset","search_id","client_ab_info").contains(k))pairs.add(new String[]{k,k.equals("passback")?encode(passback==null?"0":passback):x.getString(1)});}before(pairs,"host_abi",new String[]{"_rticket",Long.toString(System.currentTimeMillis())});before(pairs,"from_rs",new String[]{"offset",Integer.toString(offset)});before(pairs,"count",new String[]{"query",encode(keyword)});if(searchId!=null&&!searchId.isEmpty())pairs.add(new String[]{"search_id",encode(searchId)});
    JSONObject r=signed("/reading/bookapi/search/tab/v",pairs,p,null);JSONArray tabs=r.optJSONArray("search_tabs");if(tabs==null&&r.optJSONObject("data")!=null)tabs=r.getJSONObject("data").optJSONArray("search_tabs");if(tabs==null)throw new IOException("搜索响应缺少结果列表");SearchPage page=new SearchPage();Set<String> ids=new HashSet<>();for(int i=0;i<tabs.length();i++){JSONObject tab=tabs.getJSONObject(i);if(tab.optInt("tab_type")!=3)continue;page.more=tab.optBoolean("has_more",tab.optInt("has_more",0)!=0);page.next=tab.optInt("next_offset",offset+20);page.searchId=tab.optString("search_id");page.passback=tab.optString("passback","0");JSONArray data=tab.optJSONArray("data");if(data!=null)for(int j=0;j<data.length();j++){JSONArray books=data.getJSONObject(j).optJSONArray("book_data");if(books!=null)for(int k=0;k<books.length();k++){Book b=new Book(books.getJSONObject(k));if(!b.id.isEmpty()&&!b.title.isEmpty()&&ids.add(b.id))page.books.add(b);}}}if(page.next<=offset)page.more=false;return page;
  }
  static Map<String,String> webHeaders(String id){Map<String,String> h=new HashMap<>();h.put("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36");h.put("Referer","https://fanqienovel.com/page/"+id);h.put("Accept-Encoding","identity");return h;}
  static void id(String s){if(s==null||!s.matches("[0-9]+"))throw new IllegalArgumentException("书籍或章节标识不可用");}
  Book detail(String id)throws Exception{
    id(id);String html=http("https://fanqienovel.com/page/"+id,webHeaders(id),null);Matcher m=Pattern.compile("(?:window\\.)?__INITIAL_STATE__\\s*=\\s*").matcher(html);if(!m.find())throw new IOException("书籍详情暂不可用");JSONObject state=(JSONObject)new JSONTokener(html.substring(m.end())).nextValue(),p=state.getJSONObject("page");if(!id.equals(p.optString("bookId")))throw new IOException("书籍详情标识不匹配");JSONObject o=new JSONObject();o.put("book_id",id).put("book_name",p.optString("bookName")).put("author",p.optString("author",p.optString("authorName"))).put("abstract",p.optString("abstract",p.optString("description"))).put("category",p.optString("category")).put("word_number",p.optString("wordNumber")).put("chapter_count",p.optInt("chapterTotal")).put("creation_status",p.optString("creationStatus")).put("last_chapter_title",p.optString("lastChapterTitle"));String cover=p.optString("thumbUrl");if(cover.isEmpty()){Matcher ld=Pattern.compile("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",Pattern.DOTALL).matcher(html);while(ld.find()){JSONObject data=new JSONObject(ld.group(1));Object image=data.opt("image");if(image==null)image=data.opt("images");if(image instanceof JSONArray)image=((JSONArray)image).opt(0);if(image instanceof String&&((String)image).startsWith("http")){cover=(String)image;break;}}}o.put("thumb_url",cover);if(o.optString("category").isEmpty()){String categories=p.optString("categoryV2");if(!categories.isEmpty())try{JSONArray cats=new JSONArray(categories);StringBuilder names=new StringBuilder();for(int i=0;i<cats.length();i++){String label=cats.getJSONObject(i).optString("Name");if(!label.isEmpty()){if(names.length()>0)names.append(" · ");names.append(label);}}o.put("category",names.toString());}catch(JSONException ignored){}}Book b=new Book(o);if(b.title.isEmpty()||b.author.isEmpty())throw new IOException("书籍详情不完整");store.updateBook(b);return b;
  }
  JSONArray directory(String id,boolean force)throws Exception{
    id(id);JSONArray cached=store.directory(id);if(cached!=null&&!force)return cached;JSONObject r=new JSONObject(http("https://fanqienovel.com/api/reader/directory/detail?bookId="+id,webHeaders(id),null));if(r.optInt("code",-1)!=0)throw new IOException("目录加载失败");JSONObject data=r.getJSONObject("data");JSONArray groups=data.getJSONArray("chapterListWithVolume"),all=data.getJSONArray("allItemIds"),out=new JSONArray();Set<String> seen=new HashSet<>();for(int i=0;i<groups.length();i++){JSONArray group=groups.getJSONArray(i);for(int j=0;j<group.length();j++){JSONObject c=group.getJSONObject(j);String cid=c.getString("itemId");id(cid);if(!seen.add(cid)||c.optString("title").isEmpty())throw new IOException("目录数据不完整");out.put(new JSONObject().put("id",cid).put("title",c.getString("title")).put("volume",i).put("locked",c.optBoolean("isChapterLock",false)||c.optBoolean("needPay",false)));}}if(out.length()==0||out.length()!=all.length())throw new IOException("目录数量不一致");for(int i=0;i<all.length();i++)if(!all.getString(i).equals(out.getJSONObject(i).getString("id")))throw new IOException("目录顺序不一致");store.directory(id,out);return out;
  }
  List<String[]> contentPairs(JSONObject p)throws Exception{List<String[]> out=new ArrayList<>();JSONArray a=p.getJSONArray("query_defaults");for(int i=0;i<a.length();i++){JSONArray x=a.getJSONArray(i);String k=x.getString(0);if(k.equals("bookshelf_search_plan"))break;if(k.equals("_rticket"))continue;if(k.equals("host_abi"))out.add(new String[]{"_rticket",Long.toString(System.currentTimeMillis())});out.add(new String[]{k,x.getString(1)});}return out;}
  static String profileIdentity(JSONObject profile)throws JSONException{return profile.getJSONArray("query_defaults").getJSONArray(0).getString(1);}
  boolean refreshContentProfile(JSONObject failed)throws Exception{
    synchronized(NovelApi.class){SharedPreferences prefs=context.getSharedPreferences("api_profiles",0);String current=prefs.getString("content","");
      if(!current.isEmpty()&&!profileIdentity(new JSONObject(current)).equals(profileIdentity(failed)))return true;
      long now=System.currentTimeMillis(),last=prefs.getLong("content_reinitialized_at",0);if(now>=last&&now-last<60000)return false;
      if(!prefs.edit().remove("content").putLong("content_reinitialized_at",now).commit())throw new IOException("无法更新阅读配置");
      // Initialize while holding the same lock: concurrent readers reuse this one profile.
      profile("content");Log.i("ReadingDogApi","Reinitialized anonymous content after an empty response");return true;
    }
  }
  // Independently verified native directory route supplies each chapter's exact word count.
  JSONArray pageMetadata(String book,JSONArray directory)throws Exception{
    SharedPreferences cache=context.getSharedPreferences("page_metadata",0);JSONArray stored=null;try{stored=new JSONArray(cache.getString(book,"[]"));}catch(JSONException ignored){}
    if(metadataCovers(stored,directory))return stored;
    JSONObject p=profile("content");List<String[]> pairs=contentPairs(p);pairs.add(new String[]{"book_id",book});pairs.add(new String[]{"need_version","true"});pairs.add(new String[]{"book_type","0"});
    JSONObject response=signed("/reading/bookapi/directory/all_items/v/",pairs,p,null);JSONArray raw=response.getJSONObject("data").getJSONArray("item_data_list"),out=new JSONArray();
    for(int i=0;i<raw.length();i++){JSONObject item=raw.getJSONObject(i);int words=item.getInt("chapter_word_number");if(words<0)throw new IOException("章节字数无效");out.put(new JSONObject().put("id",item.getString("item_id")).put("title",item.getString("title")).put("word_count",words));}
    if(!metadataCovers(out,directory))throw new IOException("章节字数目录不完整，请刷新目录后重试");cache.edit().putString(book,out.toString()).apply();return out;
  }
  static boolean metadataCovers(JSONArray metadata,JSONArray directory){if(metadata==null||directory==null||directory.length()==0)return false;Set<String> ids=new HashSet<>();for(int i=0;i<metadata.length();i++){JSONObject item=metadata.optJSONObject(i);if(item!=null&&item.optInt("word_count",-1)>=0)ids.add(item.optString("id"));}for(int i=0;i<directory.length();i++)if(!ids.contains(directory.optJSONObject(i).optString("id")))return false;return true;}
  synchronized JSONObject chapter(String bookId,String chapterId)throws Exception{
    id(bookId);id(chapterId);JSONObject cached=store.chapter(bookId,chapterId);if(cached!=null)return cached;
    JSONObject p=profile("content");try{return chapterRequest(bookId,chapterId,p);}catch(EmptyResponse error){
      sessionKey=null;keyIdentity="";if(!refreshContentProfile(p))throw error;
      // Exactly one retry, with a fresh key bound to the current anonymous identity.
      return chapterRequest(bookId,chapterId,profile("content"));
    }
  }
  JSONObject chapterRequest(String bookId,String chapterId,JSONObject p)throws Exception{
    String identity=profileIdentity(p);if(!identity.equals(keyIdentity)){sessionKey=null;keyIdentity="";}if(sessionKey==null){String did=p.getJSONArray("query_defaults").getJSONArray(1).getString(1);JSONObject body=new JSONObject().put("content",ProtocolCrypto.registerContent(did,ProtocolCrypto.random(16))).put("keyver",1);JSONObject r=signed("/reading/crypt/registerkey",contentPairs(p),p,body);byte[] key=ProtocolCrypto.unwrap(r.getJSONObject("data").getString("key"),ProtocolCrypto.REGISTER_KEY);if(key.length!=16)throw new IOException("正文密钥无效");sessionKey=key;keyIdentity=identity;}
    List<String[]> pairs=contentPairs(p);pairs.add(new String[]{"item_ids",chapterId});pairs.add(new String[]{"key_register_ts","0"});pairs.add(new String[]{"book_id",bookId});pairs.add(new String[]{"req_type","1"});JSONObject r;try{r=signed("/reading/reader/batch_full/v",pairs,p,null);}catch(Exception e){sessionKey=null;throw e;}JSONObject item=r.getJSONObject("data").optJSONObject(chapterId);if(item==null)throw new IOException("当前章节暂不可用");String html;try{html=ProtocolCrypto.chapter(item.getString("content"),sessionKey);}catch(Exception e){sessionKey=null;throw new IOException("正文解密失败，请重试",e);}html=html.replaceAll("(?is)<(script|style|header)\\b[^>]*>.*?</\\1>","");Matcher m=Pattern.compile("(?is)<p\\b([^>]*)>(.*?)</p>").matcher(html);JSONArray paragraphs=new JSONArray(),meta=new JSONArray();Set<String> paragraphIds=new HashSet<>();boolean mapped=true;StringBuilder text=new StringBuilder();while(m.find()){String value=Html.fromHtml(m.group(2),Html.FROM_HTML_MODE_LEGACY).toString().trim();if(!value.isEmpty()){paragraphs.put(value);if(text.length()>0)text.append("\n\n");Matcher idx=Pattern.compile("(?i)\\bidx=[\"\']([0-9]+)[\"\']").matcher(m.group(1));String pid=idx.find()?idx.group(1):"";if(pid.isEmpty()||!paragraphIds.add(pid))mapped=false;meta.put(new JSONObject().put("id",pid).put("start",text.length()).put("end",text.length()+value.length()).put("text",value));text.append(value);}}if(paragraphs.length()==0)throw new IOException("当前章节没有可读正文");JSONObject chapter=new JSONObject().put("id",chapterId).put("title",item.optString("title")).put("text",text.toString()).put("paragraphs",paragraphs).put("paragraph_meta",meta).put("paragraph_mapping",mapped);store.chapter(bookId,chapterId,chapter);return chapter;
  }
}
