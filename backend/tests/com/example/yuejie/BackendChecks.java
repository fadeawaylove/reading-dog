package com.example.yuejie;
import java.nio.file.*;
import org.json.*;
public final class BackendChecks {
 static int count;
 static void ok(boolean yes,String message){if(!yes)throw new AssertionError(message);count++;}
 static JSONObject op(String kind,String book,long at,JSONObject value)throws Exception{return new JSONObject().put("kind",kind).put("book",book).put("id",java.util.UUID.randomUUID().toString()).put("at",at).put("value",value);}
 static JSONObject position(String chapter,int offset)throws Exception{return new JSONObject().put("chapter",chapter).put("offset",offset).put("index",1).put("total",10).put("title","Chapter");}
 static JSONObject exchange(SyncStore s,long cursor,JSONObject... ops)throws Exception{return s.exchange(new JSONObject().put("cursor",cursor).put("operations",new JSONArray(ops)));}
 public static void main(String[] args)throws Exception{
  Path folder=Files.createTempDirectory("reading-dog-sync-");long now=System.currentTimeMillis()-60000;Path path=folder.resolve("library.db");
  try(SyncStore s=new SyncStore(path)){
   JSONObject shelf=op("shelf","123",now,new JSONObject().put("present",true).put("book",new JSONObject().put("book_id","123").put("book_name","测试")));
   JSONObject r=exchange(s,0,shelf);ok(r.getLong("cursor")==1&&r.getJSONArray("changes").length()==1,"initial upload");
   r=exchange(s,1,shelf);ok(r.getJSONArray("ack").length()==1&&r.getLong("cursor")==1,"idempotent retry");
   JSONObject latest=op("progress","123",now+1000,position("456",20));r=exchange(s,1,latest);ok(r.getLong("cursor")==2,"progress upload");
   JSONObject stale=op("progress","123",now+500,position("457",300));r=exchange(s,0,stale);ok(r.getJSONArray("changes").getJSONObject(1).getJSONObject("value").getString("chapter").equals("456"),"offline stale arrival cannot override recent reading");
   r=exchange(s,2,op("progress","123",now+2000,position("400",0)));ok(r.getJSONArray("changes").getJSONObject(0).getJSONObject("value").getString("chapter").equals("400"),"intentional reread wins");
   JSONObject remove=op("shelf","123",now+3000,new JSONObject().put("present",false));r=exchange(s,0,remove,shelf);ok(!r.getJSONArray("changes").getJSONObject(1).getJSONObject("value").getBoolean("present"),"tombstone prevents resurrection");
   r=exchange(s,99999);ok(r.getJSONArray("changes").length()==2,"cursor reset after backup restore");
   long before=r.getLong("cursor");boolean rejected=false;try{exchange(s,0,op("shelf","124",now,new JSONObject().put("present",false)),op("progress","125",now,position("4",-1)));}catch(IllegalArgumentException e){rejected=true;}ok(rejected&&exchange(s,0).getLong("cursor")==before,"invalid batch rolls back including earlier operations");
   JSONObject tieA=op("progress","123",now+4000,position("401",10)),tieB=op("progress","123",now+4000,position("402",10));r=exchange(s,0,tieA,tieB);String expected=tieA.getString("id").compareTo(tieB.getString("id"))>0?"401":"402";JSONObject last=r.getJSONArray("changes").getJSONObject(1);ok(last.getJSONObject("value").getString("chapter").equals(expected),"deterministic equal-time conflict");
   s.backup(folder.resolve("backup.db"));
  }
  try(SyncStore s=new SyncStore(path);SyncStore restored=new SyncStore(folder.resolve("backup.db"))){ok(exchange(s,0).toString().replaceAll("\"server_time\":[0-9]+","").equals(exchange(restored,0).toString().replaceAll("\"server_time\":[0-9]+","")),"restart and consistent backup preserve data");}
  JSONObject common=new JSONObject().put("group_id","456"),expand=new JSONObject().put("book_id","123").put("item_version","historical").put("comment_pos",new JSONObject().put("start_para_index",1).put("end_para_index",1));
  ok(ParagraphCommentApi.belongsToParagraph(common,expand,"123","456",1),"historical comment in same paragraph accepted");
  ok(!ParagraphCommentApi.belongsToParagraph(common,expand,"124","456",1)&&!ParagraphCommentApi.belongsToParagraph(common,expand,"123","457",1)&&!ParagraphCommentApi.belongsToParagraph(common,expand,"123","456",2),"cross-book, chapter and paragraph comments rejected");
  expand.getJSONObject("comment_pos").put("end_para_index",2);ok(!ParagraphCommentApi.belongsToParagraph(common,expand,"123","456",1),"spanning comment rejected");expand.remove("comment_pos");ok(!ParagraphCommentApi.belongsToParagraph(common,expand,"123","456",1),"missing scope rejected");
  ok(HtmlText.decode("一 &amp; 二<br>😀<b>三</b>").equals("一 & 二\n😀三"),"HTML entity, break and supplementary characters");
  JSONObject illustrated=ChapterContent.parse("456","图片测试","<p idx='1'>  中😀<b>A<img src='https://p3.byteimg.com/a?x=1&amp;y=2' width='800px' height='1200'></b>B<img data-src='//p3.byteimg.com/b'></p><p idx='2'><img></p>");
  String body=illustrated.getString("text");JSONArray images=illustrated.getJSONArray("images"),paragraphs=illustrated.getJSONArray("paragraph_meta");
  ok(body.equals("中😀A\ufffcB\ufffc\n\n\ufffc")&&images.length()==3,"mixed, adjacent and image-only paragraphs keep original text");
  ok(images.getJSONObject(0).getInt("offset")==4&&images.getJSONObject(1).getInt("offset")==6&&images.getJSONObject(2).getInt("offset")==9,"image offsets count UTF-16, trimming and paragraph separators");
  ok(images.getJSONObject(0).getString("url").equals("https://p3.byteimg.com/a?x=1&y=2")&&images.getJSONObject(1).getString("url").equals("https://p3.byteimg.com/b"),"entities and signed query order survive image extraction");
  ok(images.getJSONObject(0).getInt("width")==800&&images.getJSONObject(0).getInt("height")==1200&&images.getJSONObject(2).getString("url").isEmpty(),"dimensions and missing-source placeholder survive");
  ok(paragraphs.getJSONObject(1).getInt("start")==9&&paragraphs.getJSONObject(1).getInt("end")==10&&illustrated.getBoolean("paragraph_mapping"),"paragraph comment identities and ranges preserved");
  JSONObject pure=ChapterContent.parse("456","plain","<p idx='1'> 一 &amp; 二<br>😀<b>三</b> </p>");
  ok(pure.getString("text").equals(HtmlText.decode(" 一 &amp; 二<br>😀<b>三</b> ").trim())&&pure.getJSONArray("images").length()==0,"pure text parity unchanged");
  ok(ChapterContent.current(pure)&&!ChapterContent.current(new JSONObject().put("text",body)),"legacy backend cache must be refetched");
  HtmlText.Decoded adjacent=HtmlText.decodeImages("<img src='bad' width='-1' height='99%'><img src='https://p3.byteimg.com/c' width='999999'>");
  ok(adjacent.text.toString().equals("\ufffc\ufffc")&&adjacent.images.getJSONObject(1).getInt("offset")==1&&adjacent.images.getJSONObject(0).getInt("width")==0&&adjacent.images.getJSONObject(1).getInt("width")==0,"adjacent invalid images retain slots without trusting invalid sizes");
  ok(!Server.mediaHost("127.0.0.1")&&!Server.mediaHost("evil-byteimg.com")&&Server.mediaHost("p3.byteimg.com"),"media allowlist");
  String downloadToken="0123456789abcdef0123456789abcdef0123456789abcdef";
  String hash=ProtocolCrypto.hex(java.security.MessageDigest.getInstance("SHA-256").digest(downloadToken.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
  Path release=folder.resolve("releases/0.24");Files.createDirectories(release);Files.writeString(release.resolve("reading-dog-0.24.apk"),"fixture");
  Files.writeString(folder.resolve("install-links.json"),new JSONObject().put(hash,new JSONObject().put("version","0.24")).toString());
  ok(InstallLinks.resolve(folder,"/install/"+downloadToken+"/reading-dog-0.24.apk")!=null,"browser link resolves only published APK");
  ok(InstallLinks.resolve(folder,"/install/"+downloadToken+"/reading-dog-0.25.apk")==null&&InstallLinks.resolve(folder,"/install/"+downloadToken+"/../library.db")==null,"browser link cannot access other versions or library");
  ok(InstallLinks.resolve(folder,"/install/"+"a".repeat(48)+"/reading-dog-0.24.apk")==null,"unknown browser token rejected");
  Path fonts=folder.resolve("fonts");Files.createDirectories(fonts);Files.writeString(fonts.resolve("reader-body.ttf"),"fixture");Files.writeString(fonts.resolve("catalog.json"),"{}");
  ok(FontFiles.resolve(folder,"/fonts/reader-body.ttf")!=null&&FontFiles.resolve(folder,"/fonts/catalog.json")!=null,"fixed font download routes resolve");
  ok(FontFiles.resolve(folder,"/fonts/../library.db")==null&&FontFiles.resolve(folder,"/fonts/arbitrary.ttf")==null,"font route cannot access private data or arbitrary files");
  System.out.println("PASS "+count+" backend checks");
 }
}
