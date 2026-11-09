package com.example.yuejie;

import android.app.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Isolated SQLite fixtures; never inserts fixture data into the personal backend. */
public final class SyncChecks extends Instrumentation {
 int count;Bundle options;
 void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);count++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 public void onCreate(Bundle args){super.onCreate(args);options=args;start();}
 static class Isolated extends ContextWrapper {
  final File folder;final String prefix;
  Isolated(Context c,String name){super(c);prefix="sync-check-"+name;folder=new File(c.getCacheDir(),prefix);folder.mkdirs();}
  public Context getApplicationContext(){return this;}
  public File getDatabasePath(String name){return new File(folder,name);}
  public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);}
  public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);}
  public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(prefix+"-"+name,mode);}
 }
 JSONObject change(String kind,String book,long at,JSONObject value)throws Exception{return new JSONObject().put("kind",kind).put("book",book).put("id",UUID.randomUUID().toString()).put("at",at).put("value",value);}
 JSONObject reply(long cursor,JSONArray ack,JSONObject... changes)throws Exception{return new JSONObject().put("cursor",cursor).put("ack",ack).put("changes",new JSONArray(Arrays.asList(changes)));}
 JSONArray ids(JSONArray ops)throws Exception{JSONArray a=new JSONArray();for(int i=0;i<ops.length();i++)a.put(ops.getJSONObject(i).getString("id"));return a;}
 public void onStart(){try{
  Context target=getTargetContext();String unique=Long.toString(System.nanoTime());Isolated ca=new Isolated(target,unique+"a"),cb=new Isolated(target,unique+"b");
  // Construct the actual previous schema, then let the production helper upgrade it.
  SQLiteDatabase old=ca.openOrCreateDatabase("reading-dog.db",0,null);
  old.execSQL("CREATE TABLE books(id TEXT PRIMARY KEY,json TEXT NOT NULL,added INTEGER NOT NULL,last_read INTEGER NOT NULL DEFAULT 0)");old.execSQL("CREATE TABLE progress(book TEXT PRIMARY KEY,chapter TEXT NOT NULL,offset INTEGER NOT NULL,chapter_index INTEGER NOT NULL,total INTEGER NOT NULL,title TEXT,updated INTEGER NOT NULL)");old.execSQL("CREATE TABLE directories(book TEXT PRIMARY KEY,json TEXT NOT NULL)");old.execSQL("CREATE TABLE chapters(book TEXT,chapter TEXT,json TEXT NOT NULL,bytes INTEGER NOT NULL,used INTEGER NOT NULL,PRIMARY KEY(book,chapter))");old.execSQL("CREATE TABLE bookmarks(book TEXT,chapter TEXT,offset INTEGER,title TEXT,created INTEGER,PRIMARY KEY(book,chapter,offset))");old.execSQL("CREATE TABLE library_updates(book TEXT PRIMARY KEY,checked_at INTEGER NOT NULL,acknowledged_tail TEXT NOT NULL)");
  JSONObject raw=new JSONObject().put("book_id","123").put("book_name","Fixture").put("author","Test");old.execSQL("INSERT INTO books VALUES(?,?,?,?)",new Object[]{"123",raw.toString(),100,200});old.execSQL("INSERT INTO progress VALUES('123','456',42,1,10,'chapter',200)");old.setVersion(2);old.close();
  ca.getSharedPreferences("reading_dog",0).edit().putBoolean("sqlite_migrated",true).commit();
  LocalLibrary a=new LocalLibrary(ca),b=new LocalLibrary(cb);SQLiteDatabase da=a.getWritableDatabase(),db=b.getWritableDatabase();
  ok(da.getVersion()==4&&a.contains("123")&&a.progress("123").getInt("offset")==42,"v2 upgrade preserves shelf and character offset");
  LibrarySync.seed(ca,da,0);JSONArray pending=LibrarySync.pending(da);ok(pending.length()==2,"existing data becomes durable initial outbox");LibrarySync.seed(ca,da,0);ok(LibrarySync.pending(da).length()==2,"initial migration is idempotent");
  a.close();a=new LocalLibrary(ca);da=a.getWritableDatabase();ok(LibrarySync.pending(da).length()==2,"offline queue survives helper/process database reopen");
  JSONObject shelf=pending.getJSONObject(0).getString("kind").equals("shelf")?pending.getJSONObject(0):pending.getJSONObject(1);JSONObject position=pending.getJSONObject(0).getString("kind").equals("progress")?pending.getJSONObject(0):pending.getJSONObject(1);
  LibrarySync.merge(db,reply(2,new JSONArray(),shelf,position));ok(b.contains("123")&&b.progress("123").getInt("offset")==42,"second client applies initial shelf and progress");
  a.progress("123","456",99,1,10,"chapter");JSONArray latest=LibrarySync.pending(da);LibrarySync.merge(da,reply(2,ids(pending)));ok(LibrarySync.pending(da).length()==1,"late acknowledgment does not delete newer local edit");
  JSONObject newer=latest.getJSONObject(latest.length()-1);if(!newer.getString("kind").equals("progress"))newer=latest.getJSONObject(0);
  LibrarySync.merge(db,reply(3,new JSONArray(),newer));ok(b.progress("123").getInt("offset")==99,"second client receives changed character anchor");
  a.remove("123");ok(!a.contains("123")&&a.progress("123")!=null,"shelf removal preserves independent progress");JSONArray queue=LibrarySync.pending(da);JSONObject tombstone=null;for(int i=0;i<queue.length();i++)if(queue.getJSONObject(i).getString("kind").equals("shelf"))tombstone=queue.getJSONObject(i);
  LibrarySync.merge(db,reply(4,new JSONArray(),tombstone));LibrarySync.merge(db,reply(5,new JSONArray(),shelf));ok(!b.contains("123"),"old device snapshot cannot resurrect removed book");
  a.add(new Book(raw));queue=LibrarySync.pending(da);JSONObject added=null;for(int i=0;i<queue.length();i++)if(queue.getJSONObject(i).getString("kind").equals("shelf"))added=queue.getJSONObject(i);
  LibrarySync.merge(db,reply(6,new JSONArray(),added));ok(b.contains("123"),"intentional re-add after deletion synchronizes");
  SyncEngine.activeBook="123";JSONObject remote=change("progress","123",System.currentTimeMillis()+1000,LibrarySync.position("456",7,1,10,"reread"));LibrarySync.merge(db,reply(7,new JSONArray(),remote));ok(b.progress("123").getInt("offset")==99&&LibrarySync.deferred(db,"123").getInt("offset")==7,"active reader keeps page and defers remote reread");
  SyncEngine.activeBook="";LibrarySync.acceptDeferred(db,"123");ok(b.progress("123").getInt("offset")==7,"remote earlier chapter/offset is accepted explicitly");
  b.progress("123","456",8,1,10,"reread");ok(LibrarySync.pending(db).length()==1,"read after remote future timestamp advances entity clock");
  LibrarySync.seed(cb,db,0);ok(LibrarySync.pending(db).length()==1,"initial seeding never replaces already synchronized state");
  final String html="一 &amp; 二<br>😀<b>三</b>";ok(android.text.Html.fromHtml(html,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim().equals("一 & 二\n😀三"),"backend HTML fixture matches Android text and UTF-16 offset behavior");
  try(InputStream input=getContext().getAssets().open("text-parity.json")){JSONArray fixtures=new JSONArray(new String(ProtocolCrypto.read(input),"UTF-8"));for(int i=0;i<fixtures.length();i++){JSONObject f=fixtures.getJSONObject(i);if(!android.text.Html.fromHtml(f.getString("html"),android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim().equals(f.getString("decoded")))throw new AssertionError("Native text parity paragraph "+i);}ok(fixtures.length()>0,"all "+fixtures.length()+" native paragraphs preserve exact Android text and offsets");}
  String actual=BackendClient.request(target,"/api/v1/time",null).toString();ok(new JSONObject(actual).getLong("server_time")>0,"Android HTTPS token authentication succeeds");
  if(!"true".equals(options.getString("skipRelease"))){AppUpdater updater=new AppUpdater(target);UpdateRelease release=updater.readRelease(updater.generation);ok(release.url.startsWith(BackendClient.ORIGIN+"/releases/"),"update metadata comes only from backend");}
  boolean rejected=false;try{UpdateRelease.requireAssetUrl("https://github.com/fadeawaylove/reading-dog/releases/download/v0.24/a.apk");}catch(Exception e){rejected=true;}ok(rejected,"legacy GitHub update URL rejected");
  a.close();b.close();Bundle done=new Bundle();done.putString("stream","PASS "+count+" Android synchronization checks\n");finish(0,done);
 }catch(Throwable e){SyncEngine.activeBook="";Bundle result=new Bundle();result.putString("stream",android.util.Log.getStackTraceString(e));finish(1,result);}}
}
