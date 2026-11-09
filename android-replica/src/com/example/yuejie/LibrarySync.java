package com.example.yuejie;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Durable outbox and per-entity versions share the local library's transaction. */
final class LibrarySync {
  static void create(SQLiteDatabase db){
    db.execSQL("CREATE TABLE IF NOT EXISTS sync_state(kind TEXT,book TEXT,value TEXT NOT NULL,at INTEGER NOT NULL,op TEXT NOT NULL,PRIMARY KEY(kind,book))");
    db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox(id TEXT PRIMARY KEY,kind TEXT,book TEXT,value TEXT NOT NULL,at INTEGER NOT NULL,event_boot TEXT NOT NULL DEFAULT '',event_elapsed INTEGER NOT NULL DEFAULT -1)");
    db.execSQL("CREATE TABLE IF NOT EXISTS sync_meta(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
    db.execSQL("CREATE TABLE IF NOT EXISTS sync_deferred(book TEXT PRIMARY KEY,value TEXT NOT NULL,at INTEGER NOT NULL)");
  }
  static void upgradeClock(SQLiteDatabase db){Set<String> columns=new HashSet<>();try(Cursor c=db.rawQuery("PRAGMA table_info(sync_outbox)",null)){while(c.moveToNext())columns.add(c.getString(1));}if(!columns.contains("event_boot"))db.execSQL("ALTER TABLE sync_outbox ADD COLUMN event_boot TEXT NOT NULL DEFAULT ''");if(!columns.contains("event_elapsed"))db.execSQL("ALTER TABLE sync_outbox ADD COLUMN event_elapsed INTEGER NOT NULL DEFAULT -1");}
  static String meta(SQLiteDatabase db,String k,String fallback){try(Cursor c=db.rawQuery("SELECT v FROM sync_meta WHERE k=?",new String[]{k})){return c.moveToFirst()?c.getString(0):fallback;}}
  static void putMeta(SQLiteDatabase db,String k,String v){ContentValues values=new ContentValues();values.put("k",k);values.put("v",v);db.insertWithOnConflict("sync_meta",null,values,SQLiteDatabase.CONFLICT_REPLACE);}
  static void record(Context c,SQLiteDatabase db,String kind,String book,JSONObject value,long at){
    record(c,db,kind,book,value,at,true);
  }
  static void record(Context c,SQLiteDatabase db,String kind,String book,JSONObject value,long at,boolean currentEvent){
    try(Cursor previous=db.rawQuery("SELECT at FROM sync_state WHERE kind=? AND book=?",new String[]{kind,book})){if(previous.moveToFirst())at=Math.max(at,previous.getLong(0)+1);}
    String op=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("kind",kind);v.put("book",book);v.put("value",value.toString());v.put("at",at);v.put("op",op);db.insertWithOnConflict("sync_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    // The latest pending value supersedes earlier values of the same entity. Acknowledgments use ID, never entity.
    db.delete("sync_outbox","kind=? AND book=?",new String[]{kind,book});v.remove("op");v.put("id",op);if(currentEvent){v.put("event_boot",SyncClock.boot(c));v.put("event_elapsed",android.os.SystemClock.elapsedRealtime());}db.insertOrThrow("sync_outbox",null,v);
    if(kind.equals("progress"))db.delete("sync_deferred","book=?",new String[]{book});
    if(c.getApplicationContext() instanceof ReadingDogApp)SyncEngine.schedule(c,5000);
  }
  static JSONObject shelf(Book b,boolean present,long added){try{JSONObject v=new JSONObject().put("present",present);if(present)v.put("book",b.raw).put("added",added);return v;}catch(JSONException e){throw new IllegalStateException(e);}}
  static JSONObject position(String chapter,int offset,int index,int total,String title){try{return new JSONObject().put("chapter",chapter).put("offset",offset).put("index",index).put("total",total).put("title",title==null?"":title);}catch(JSONException e){throw new IllegalStateException(e);}}
  static void seed(Context c,SQLiteDatabase db,long correction){
    if(meta(db,"seeded","0").equals("1"))return;
    db.beginTransaction();try{
      try(Cursor cursor=db.rawQuery("SELECT id,json,added FROM books WHERE id NOT IN (SELECT book FROM sync_state WHERE kind='shelf')",null)){while(cursor.moveToNext())try{Book b=new Book(new JSONObject(cursor.getString(1)));record(c,db,"shelf",b.id,shelf(b,true,cursor.getLong(2)),Math.max(0,cursor.getLong(2)+correction),false);}catch(JSONException e){throw new IllegalStateException(e);}}
      try(Cursor cursor=db.rawQuery("SELECT book,chapter,offset,chapter_index,total,title,updated FROM progress WHERE book NOT IN (SELECT book FROM sync_state WHERE kind='progress')",null)){while(cursor.moveToNext())record(c,db,"progress",cursor.getString(0),position(cursor.getString(1),cursor.getInt(2),cursor.getInt(3),cursor.getInt(4),cursor.getString(5)),Math.max(0,cursor.getLong(6)+correction),false);}
      putMeta(db,"seeded","1");db.setTransactionSuccessful();
    }finally{db.endTransaction();}
  }
  static JSONArray pending(SQLiteDatabase db)throws JSONException{JSONArray out=new JSONArray();try(Cursor c=db.rawQuery("SELECT id,kind,book,value,at FROM sync_outbox ORDER BY at,id LIMIT 100",null)){while(c.moveToNext())out.put(new JSONObject().put("id",c.getString(0)).put("kind",c.getString(1)).put("book",c.getString(2)).put("value",new JSONObject(c.getString(3))).put("at",c.getLong(4)));}return out;}
  static void merge(SQLiteDatabase db,JSONObject reply)throws JSONException{
    db.beginTransaction();try{
      JSONArray ack=reply.getJSONArray("ack");for(int i=0;i<ack.length();i++)db.delete("sync_outbox","id=?",new String[]{ack.getString(i)});
      JSONArray changes=reply.getJSONArray("changes");for(int i=0;i<changes.length();i++){
        JSONObject o=changes.getJSONObject(i);String kind=o.getString("kind"),book=o.getString("book"),op=o.getString("id");long at=o.getLong("at");JSONObject value=o.getJSONObject("value");
        long old=-1;String oldOp="";try(Cursor c=db.rawQuery("SELECT at,op FROM sync_state WHERE kind=? AND book=?",new String[]{kind,book})){if(c.moveToFirst()){old=c.getLong(0);oldOp=c.getString(1);}}
        if(at<old||at==old&&op.compareTo(oldOp)<=0)continue;
        ContentValues state=new ContentValues();state.put("kind",kind);state.put("book",book);state.put("value",value.toString());state.put("at",at);state.put("op",op);db.insertWithOnConflict("sync_state",null,state,SQLiteDatabase.CONFLICT_REPLACE);
        if(kind.equals("shelf")){
          if(!value.getBoolean("present")){db.delete("books","id=?",new String[]{book});}
          else{ContentValues b=new ContentValues();b.put("id",book);b.put("json",value.getJSONObject("book").toString());b.put("added",value.optLong("added",at));try(Cursor c=db.rawQuery("SELECT at FROM sync_state WHERE kind='progress' AND book=?",new String[]{book})){b.put("last_read",c.moveToFirst()?c.getLong(0):0);}db.insertWithOnConflict("books",null,b,SQLiteDatabase.CONFLICT_REPLACE);}
        }else if(kind.equals("progress")){
          if(book.equals(SyncEngine.activeBook)){ContentValues v=new ContentValues();v.put("book",book);v.put("value",value.toString());v.put("at",at);db.insertWithOnConflict("sync_deferred",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
          else{applyPosition(db,book,value,at);db.delete("sync_deferred","book=?",new String[]{book});}
        }
      }
      putMeta(db,"cursor",Long.toString(reply.getLong("cursor")));db.setTransactionSuccessful();
    }finally{db.endTransaction();}
  }
  static void applyPosition(SQLiteDatabase db,String book,JSONObject p,long at)throws JSONException{
    ContentValues v=new ContentValues();v.put("book",book);v.put("chapter",p.getString("chapter"));v.put("offset",p.getInt("offset"));v.put("chapter_index",p.getInt("index"));v.put("total",p.getInt("total"));v.put("title",p.optString("title"));v.put("updated",at);db.insertWithOnConflict("progress",null,v,SQLiteDatabase.CONFLICT_REPLACE);ContentValues b=new ContentValues();b.put("last_read",at);db.update("books",b,"id=?",new String[]{book});
  }
  static JSONObject deferred(SQLiteDatabase db,String book){try(Cursor c=db.rawQuery("SELECT value FROM sync_deferred WHERE book=?",new String[]{book})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}catch(JSONException e){return null;}}
  static JSONObject readerPosition(SQLiteDatabase db,String book,String chapter,int offset){JSONObject candidate=deferred(db,book);if(candidate==null)try(Cursor c=db.rawQuery("SELECT chapter,offset,chapter_index,total,title FROM progress WHERE book=?",new String[]{book})){if(c.moveToFirst())candidate=position(c.getString(0),c.getInt(1),c.getInt(2),c.getInt(3),c.getString(4));}return candidate!=null&&(!chapter.equals(candidate.optString("chapter"))||offset!=candidate.optInt("offset"))?candidate:null;}
  static void acceptDeferred(SQLiteDatabase db,String book){db.beginTransaction();try{try(Cursor c=db.rawQuery("SELECT value,at FROM sync_deferred WHERE book=?",new String[]{book})){if(c.moveToFirst())applyPosition(db,book,new JSONObject(c.getString(0)),c.getLong(1));}db.delete("sync_deferred","book=?",new String[]{book});db.setTransactionSuccessful();}catch(JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}}
}
