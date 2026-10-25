package com.example.yuejie;
import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;
/** Separate bounded cache; no migration or writes to library/progress tables. */
final class CommentStore extends SQLiteOpenHelper {
 static final long TTL=30*60*1000L,LIMIT=10*1024*1024L;
 static final class Entry {final JSONObject value;final boolean stale;Entry(JSONObject v,boolean s){value=v;stale=s;}}
 CommentStore(Context c){super(c.getApplicationContext(),"paragraph-comments.db",null,1);}
 public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE cache(k TEXT PRIMARY KEY,json TEXT NOT NULL,bytes INTEGER NOT NULL,updated INTEGER NOT NULL,used INTEGER NOT NULL)");}
 public void onUpgrade(SQLiteDatabase d,int f,int t){throw new IllegalStateException("Unsupported comments schema");}
 synchronized Entry get(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT json,updated FROM cache WHERE k=?",new String[]{key})){if(!c.moveToFirst())return null;JSONObject v=new JSONObject(c.getString(0));long now=System.currentTimeMillis();ContentValues used=new ContentValues();used.put("used",now);getWritableDatabase().update("cache",used,"k=?",new String[]{key});return new Entry(v,now<c.getLong(1)||now-c.getLong(1)>TTL);}catch(JSONException e){getWritableDatabase().delete("cache","k=?",new String[]{key});return null;}}
 synchronized void put(String key,JSONObject value)throws Exception{String json=value.toString();ContentValues v=new ContentValues();long now=System.currentTimeMillis();v.put("k",key);v.put("json",json);v.put("bytes",ProtocolCrypto.utf(json).length);v.put("updated",now);v.put("used",now);SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.insertWithOnConflict("cache",null,v,SQLiteDatabase.CONFLICT_REPLACE);long size;try(Cursor c=d.rawQuery("SELECT COALESCE(SUM(bytes),0) FROM cache",null)){c.moveToFirst();size=c.getLong(0);}try(Cursor c=d.rawQuery("SELECT k,bytes FROM cache ORDER BY used",null)){while(size>LIMIT&&c.moveToNext()){d.delete("cache","k=?",new String[]{c.getString(0)});size-=c.getLong(1);}}d.setTransactionSuccessful();}finally{d.endTransaction();}}
}
