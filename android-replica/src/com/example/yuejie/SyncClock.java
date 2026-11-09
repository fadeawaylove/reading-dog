package com.example.yuejie;

import android.content.*;import android.database.*;import android.database.sqlite.*;import android.os.SystemClock;import android.provider.Settings;import org.json.*;import java.util.*;

/** Server-time anchor advances with elapsedRealtime, so wall-clock changes cannot poison new events. */
final class SyncClock {
  private static final String PROCESS_BOOT="process:"+UUID.randomUUID();
  static String boot(Context c){try{int count=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);return count<0?PROCESS_BOOT:"boot:"+count;}catch(Exception e){return PROCESS_BOOT;}}
  static long now(Context c,SQLiteDatabase db){
    long elapsed=SystemClock.elapsedRealtime();
    if(boot(c).equals(LibrarySync.meta(db,"clock_boot",""))){long anchor=Long.parseLong(LibrarySync.meta(db,"clock_elapsed","0"));if(elapsed>=anchor)return Math.max(0,Long.parseLong(LibrarySync.meta(db,"clock_server","0"))+elapsed-anchor);}
    long offset=Long.parseLong(LibrarySync.meta(db,"clock_offset",Long.toString(c.getSharedPreferences("sync_clock",0).getLong("offset",0))));return Math.max(0,System.currentTimeMillis()+offset);
  }
  static void calibrate(Context c,SQLiteDatabase db,long server,long wall,long elapsed)throws Exception{
    long offset=server-wall;String boot=boot(c);db.beginTransaction();try{
      long oldOffset=Long.parseLong(LibrarySync.meta(db,"clock_offset",Long.toString(c.getSharedPreferences("sync_clock",0).getLong("offset",0))));
      boolean initial=!LibrarySync.meta(db,"clock_initialized","0").equals("1");
      // Capture rows before updating IDs. Rebased operations get new IDs, so a late old acknowledgment cannot remove them.
      List<JSONObject> rows=new ArrayList<>();try(Cursor cursor=db.rawQuery("SELECT id,kind,book,value,at,event_boot,event_elapsed FROM sync_outbox",null)){while(cursor.moveToNext())rows.add(new JSONObject().put("id",cursor.getString(0)).put("kind",cursor.getString(1)).put("book",cursor.getString(2)).put("value",new JSONObject(cursor.getString(3))).put("at",cursor.getLong(4)).put("boot",cursor.getString(5)).put("elapsed",cursor.getLong(6)));}
      for(JSONObject row:rows){
        long at=row.getLong("at"),eventElapsed=row.getLong("elapsed"),corrected=at;
        if(eventElapsed>=0&&boot.equals(row.getString("boot")))corrected=server+eventElapsed-elapsed;
        else if(initial)corrected=at+offset-oldOffset;
        else if(at>server+60000)corrected=Math.min(server,at+offset-oldOffset); // Repair legacy / uncalibrated previous-boot future events.
        corrected=Math.max(0,corrected);if(Math.abs(corrected-at)<=1000)continue;
        String oldId=row.getString("id"),newId=UUID.randomUUID().toString(),kind=row.getString("kind"),book=row.getString("book");JSONObject value=row.getJSONObject("value");
        if(kind.equals("shelf")&&value.optBoolean("present"))value.put("added",corrected);
        ContentValues out=new ContentValues();out.put("id",newId);out.put("at",corrected);out.put("value",value.toString());db.update("sync_outbox",out,"id=?",new String[]{oldId});
        ContentValues state=new ContentValues();state.put("op",newId);state.put("at",corrected);state.put("value",value.toString());
        if(db.update("sync_state",state,"kind=? AND book=? AND op=?",new String[]{kind,book,oldId})>0){
          if(kind.equals("progress")){ContentValues p=new ContentValues();p.put("updated",corrected);db.update("progress",p,"book=?",new String[]{book});ContentValues b=new ContentValues();b.put("last_read",corrected);db.update("books",b,"id=?",new String[]{book});}
          else if(value.optBoolean("present")){ContentValues b=new ContentValues();b.put("added",corrected);db.update("books",b,"id=?",new String[]{book});}
        }
      }
      LibrarySync.putMeta(db,"clock_initialized","1");LibrarySync.putMeta(db,"clock_boot",boot);LibrarySync.putMeta(db,"clock_server",Long.toString(server));LibrarySync.putMeta(db,"clock_elapsed",Long.toString(elapsed));LibrarySync.putMeta(db,"clock_offset",Long.toString(offset));db.setTransactionSuccessful();
    }finally{db.endTransaction();}
    if(!c.getSharedPreferences("sync_clock",0).edit().putLong("offset",offset).commit())throw new java.io.IOException("无法保存同步时间");
  }
}
