package com.example.yuejie;

import android.app.*;import android.content.*;import android.database.*;import android.database.sqlite.*;import android.os.*;import org.json.*;import java.util.*;import java.util.concurrent.*;

/** Exact regressions: real scheduler, persisted queue and resumed ReaderActivity. Emulator only. */
public final class SyncRegressionChecks extends Instrumentation {
 int checks,failures;ReaderActivity reader;
 interface Checked{void run()throws Exception;}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 void check(boolean yes,String label){if(!yes)throw new AssertionError(label);checks++;}
 void result(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
 void test(String name,Checked c){try{c.run();result("PASS "+name);}catch(Throwable e){failures++;result("FAIL "+name+": "+e);}}
 void ui(Runnable r){runOnMainSync(r);waitForIdleSync();}
 void ready()throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){boolean[] yes={false};ui(()->yes[0]=reader!=null&&!reader.busy&&reader.surface.frame!=null);if(yes[0])return;Thread.sleep(50);}throw new AssertionError("Reader did not become ready");}
 void scheduler()throws Exception{SyncEngine e=new SyncEngine(new SyncChecks.Isolated(getTargetContext(),"scheduler-"+System.nanoTime()));try{e.schedule(30000);e.schedule(5000);long delay=e.queued.getDelay(TimeUnit.MILLISECONDS);check(delay<=5000,"page change must advance 30s poll to 5s; got "+delay+"ms");ScheduledFuture<?> first=e.queued;e.schedule(10000);check(e.queued==first,"later request must not postpone earlier sync");}finally{e.worker.shutdownNow();}}
 void clock()throws Exception{Context c=new SyncChecks.Isolated(getTargetContext(),"clock-"+System.nanoTime());LocalLibrary l=new LocalLibrary(c);SQLiteDatabase db=l.getWritableDatabase();try{
   long server=System.currentTimeMillis(),future=server+3600000;LibrarySync.putMeta(db,"clock_initialized","1");c.getSharedPreferences("sync_clock",0).edit().putLong("offset",0).commit();
   JSONObject value=LibrarySync.position("456",42,0,2,"offline");db.beginTransaction();try{LibrarySync.record(c,db,"progress","123",value,future);db.setTransactionSuccessful();}finally{db.endTransaction();}
   SyncEngine.calibrate(c,db,server,future,SystemClock.elapsedRealtime());JSONObject repaired=LibrarySync.pending(db).getJSONObject(0);check(Math.abs(repaired.getLong("at")-server)<2000,"recalibration must repair future outbox timestamp");
   db.beginTransaction();try{LibrarySync.record(c,db,"progress","123",LibrarySync.position("456",43,0,2,"next"),server+1);db.setTransactionSuccessful();}finally{db.endTransaction();}
   check(LibrarySync.pending(db).getJSONObject(0).getLong("at")<server+300000,"old entity clock must not poison subsequent reading");
   check(Math.abs(SyncClock.now(c,db)-server)<2000,"new events use elapsed server time despite changed wall clock offset");
   JSONObject before=LibrarySync.pending(db).getJSONObject(0);long shifted=server+7200000;db.execSQL("UPDATE sync_outbox SET at=?,event_boot='',event_elapsed=-1",new Object[]{shifted});db.execSQL("UPDATE sync_state SET at=? WHERE op=?",new Object[]{shifted,before.getString("id")});
   SyncEngine.calibrate(c,db,server,System.currentTimeMillis(),SystemClock.elapsedRealtime());JSONObject legacy=LibrarySync.pending(db).getJSONObject(0);check(legacy.getLong("at")<=server+1000,"future operation without boot metadata is repaired");check(!legacy.getString("id").equals(before.getString("id")),"rebasing replaces operation identity");
   LibrarySync.merge(db,new JSONObject().put("ack",new JSONArray().put(before.getString("id"))).put("changes",new JSONArray()).put("cursor",0));check(LibrarySync.pending(db).length()==1,"late old acknowledgment cannot discard repaired operation");
   long historical=server-86400000;db.beginTransaction();try{LibrarySync.record(c,db,"progress","124",value,historical,false);db.setTransactionSuccessful();}finally{db.endTransaction();}SyncEngine.calibrate(c,db,server,System.currentTimeMillis()+3600000,SystemClock.elapsedRealtime());
   try(Cursor row=db.rawQuery("SELECT at FROM sync_outbox WHERE book='124'",null)){check(row.moveToFirst()&&row.getLong(0)==historical,"recalibration preserves valid historical operations from earlier boot");}
 }finally{l.close();}}
 void upgrade()throws Exception{Context c=new SyncChecks.Isolated(getTargetContext(),"upgrade-"+System.nanoTime());LocalLibrary l=new LocalLibrary(c);SQLiteDatabase db=l.getWritableDatabase();long server=System.currentTimeMillis();
  db.execSQL("DROP TABLE sync_outbox");db.execSQL("CREATE TABLE sync_outbox(id TEXT PRIMARY KEY,kind TEXT,book TEXT,value TEXT NOT NULL,at INTEGER NOT NULL)");db.execSQL("INSERT INTO sync_outbox VALUES(?,?,?,?,?)",new Object[]{UUID.randomUUID().toString(),"progress","123",LibrarySync.position("456",17,0,2,"legacy").toString(),server+3600000});db.setVersion(3);l.close();
  l=new LocalLibrary(c);db=l.getWritableDatabase();try{check(db.getVersion()==4&&LibrarySync.pending(db).length()==1,"v0.24 schema migration preserves unsent operations");LibrarySync.putMeta(db,"clock_initialized","1");SyncEngine.calibrate(c,db,server,server+3600000,SystemClock.elapsedRealtime());check(LibrarySync.pending(db).getJSONObject(0).getLong("at")<=server+1000,"migrated v0.24 future operation can upload after calibration");}finally{l.close();}}
 JSONObject reply(long at,String chapter,int offset)throws Exception{return new JSONObject().put("cursor",1).put("ack",new JSONArray()).put("changes",new JSONArray().put(new JSONObject().put("id",UUID.randomUUID().toString()).put("kind","progress").put("book",BOOK).put("at",at).put("value",LibrarySync.position(chapter,offset,chapter.equals("991")?0:1,2,"Remote"))));}
 static final String BOOK="999991234567890";
 void resume()throws Exception{
  Context c=getTargetContext();LocalLibrary l=new LocalLibrary(c);SQLiteDatabase db=l.getWritableDatabase();check(l.books(false).isEmpty()&&LibrarySync.pending(db).length()==0,"requires empty isolated emulator application data");SyncEngine engine=SyncEngine.get(c);SyncEngine.Remote original=engine.remote;
  ReaderSettings settings=new ReaderSettings(c);boolean oldComments=settings.comments;settings.comments=false;settings.save();
  engine.remote=(path,body)->{if(path.endsWith("time"))return new JSONObject().put("server_time",System.currentTimeMillis());JSONArray ack=new JSONArray(),ops=body.getJSONArray("operations");for(int i=0;i<ops.length();i++)ack.put(ops.getJSONObject(i).getString("id"));return new JSONObject().put("cursor",0).put("ack",ack).put("changes",new JSONArray()).put("more",false);};
  try{
   JSONObject raw=new JSONObject().put("book_id",BOOK).put("book_name","Offline sync regression").put("author","Fixture");l.add(new Book(raw));JSONArray dir=new JSONArray().put(new JSONObject().put("id","991").put("title","One")).put(new JSONObject().put("id","992").put("title","Two"));l.directory(BOOK,dir);
   String text=String.join("\n\n",Collections.nCopies(100,"离线阅读位置回归测试，确认恢复后不会覆盖其他设备的最新进度。"));for(String id:new String[]{"991","992"})l.chapter(BOOK,id,new JSONObject().put("id",id).put("title",id).put("text",text).put("paragraphs",new JSONArray().put(text)));
   c.getSharedPreferences("page_metadata",0).edit().putString(BOOK,new JSONArray().put(new JSONObject().put("id","991").put("word_count",text.length())).put(new JSONObject().put("id","992").put("word_count",text.length())).toString()).commit();l.progress(BOOK,"991",0,0,2,"One");
   reader=(ReaderActivity)startActivitySync(new Intent(c,ReaderActivity.class).putExtra("book_id",BOOK).putExtra("book_name","Fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ui(()->{reader.directorySource=(b,f)->dir;reader.source=(b,ch)->l.chapter(b,ch);});ready();
   ui(()->callActivityOnPause(reader));LibrarySync.merge(db,reply(System.currentTimeMillis()+1000,"992",37));ui(()->callActivityOnResume(reader));
   boolean[] offered={false};ui(()->offered[0]=reader.chapter.optString("id").equals("992")||reader.notice!=null&&reader.notice.getText().toString().contains("另一设备有新进度"));check(offered[0],"resume must offer progress merged while reader was paused");
   ui(()->{if(!reader.chapter.optString("id").equals("992"))reader.notice.performClick();});ready();check(reader.chapter.optString("id").equals("992")&&reader.anchor==37,"accepted remote location must be displayed");
   long at=System.currentTimeMillis()+2000;LibrarySync.merge(db,reply(at,"991",71));ui(()->{callActivityOnPause(reader);callActivityOnResume(reader);});
   ui(()->offered[0]=reader.chapter.optString("id").equals("991")||reader.notice!=null&&reader.notice.getText().toString().contains("另一设备有新进度"));check(offered[0],"resume must offer deferred progress consumed by pause");
   ui(()->{if(!reader.chapter.optString("id").equals("991"))reader.notice.performClick();});ready();check(reader.anchor==71&&reader.chapter.optString("id").equals("991"),"deferred reread offset restored");
   LibrarySync.merge(db,reply(System.currentTimeMillis()+3000,"992",119));ActivityMonitor monitor=addMonitor(ReaderActivity.class.getName(),null,false);ui(()->reader.recreate());ReaderActivity recreated=(ReaderActivity)monitor.waitForActivityWithTimeout(10000);removeMonitor(monitor);check(recreated!=null,"reader recreates with saved UI state");reader=recreated;ui(()->{reader.directorySource=(b,f)->dir;reader.source=(b,ch)->l.chapter(b,ch);});ready();
   ui(()->offered[0]=reader.notice!=null&&reader.notice.getText().toString().contains("另一设备有新进度"));check(offered[0],"recreated reader offers newer persisted remote position without overwriting it");
  }finally{
   if(reader!=null)ui(()->reader.finish());db.beginTransaction();try{for(String table:new String[]{"books","progress","directories","chapters","bookmarks","library_updates","sync_state","sync_outbox","sync_deferred"})db.delete(table,(table.equals("books")?"id":"book")+"=?",new String[]{BOOK});db.setTransactionSuccessful();}finally{db.endTransaction();}
   c.getSharedPreferences("page_metadata",0).edit().remove(BOOK).commit();settings.comments=oldComments;settings.save();engine.remote=original;SyncEngine.activeBook="";l.close();
  }
 }
 void staleDeferred()throws Exception{
  Context c=new SyncChecks.Isolated(getTargetContext(),"stale-deferred-"+System.nanoTime());LocalLibrary library=new LocalLibrary(c);SQLiteDatabase db=library.getWritableDatabase();long now=System.currentTimeMillis();
  try{
   library.progress(BOOK,"991",0,0,2,"Original");
   SyncEngine.activeBook=BOOK;LibrarySync.merge(db,reply(now+1000,"991",31));
   check(LibrarySync.deferred(db,BOOK).getInt("offset")==31,"active reading must defer remote position A");
   // No onPause/acceptDeferred: reopen the durable database as after abrupt process death.
   library.close();SyncEngine.activeBook="";library=new LocalLibrary(c);db=library.getWritableDatabase();
   check(LibrarySync.deferred(db,BOOK).getInt("offset")==31,"position A survives abrupt reader termination");
   JSONObject newer=reply(now+2000,"992",83);LibrarySync.merge(db,newer);
   check(library.progress(BOOK).getInt("offset")==83,"background merge applies newer position B");
   check(LibrarySync.deferred(db,BOOK)==null,"background merge must remove obsolete deferred A");
   check(LibrarySync.readerPosition(db,BOOK,"992",83)==null,"opening at B must not offer obsolete A");
   LibrarySync.acceptDeferred(db,BOOK);check(library.progress(BOOK).getInt("offset")==83,"pause/accept cannot overwrite B with A");
   LibrarySync.merge(db,reply(now+1000,"991",31));check(library.progress(BOOK).getInt("offset")==83&&LibrarySync.deferred(db,BOOK)==null,"late older replay cannot resurrect deferred A");
   // The same cleanup must not affect a different book or discard a newer active-reader deferral.
   SyncEngine.activeBook=BOOK;LibrarySync.merge(db,reply(now+3000,"991",119));
   check(library.progress(BOOK).getInt("offset")==83&&LibrarySync.deferred(db,BOOK).getInt("offset")==119,"active reader still defers newer C without jumping");
   SyncEngine.activeBook="";JSONObject other=new JSONObject().put("cursor",2).put("ack",new JSONArray()).put("changes",new JSONArray().put(new JSONObject().put("id",UUID.randomUUID().toString()).put("kind","progress").put("book","999991234567891").put("at",now+4000).put("value",LibrarySync.position("993",4,0,1,"Other"))));LibrarySync.merge(db,other);
   check(LibrarySync.deferred(db,BOOK).getInt("offset")==119,"other book merge leaves C intact");
   LibrarySync.acceptDeferred(db,BOOK);check(library.progress(BOOK).getInt("offset")==119&&LibrarySync.deferred(db,BOOK)==null,"latest valid C remains acceptable");
  }finally{SyncEngine.activeBook="";library.close();}
 }
 public void onStart(){if(!Build.FINGERPRINT.contains("generic")&&!Build.MODEL.contains("sdk")){Bundle b=new Bundle();b.putString("stream","Refusing fixture lifecycle test on non-emulator");finish(1,b);return;}test("30s poll yields to 5s mutation",this::scheduler);test("previously calibrated clock jump recovers",this::clock);test("v0.24 pending queue upgrade",this::upgrade);test("ReaderActivity pause/remote/resume",this::resume);test("process death deferred A then background B",this::staleDeferred);Bundle b=new Bundle();b.putString("stream",(failures==0?"PASS":"FAIL")+" checks="+checks+", failing scenarios="+failures);finish(failures==0?0:1,b);}
}
