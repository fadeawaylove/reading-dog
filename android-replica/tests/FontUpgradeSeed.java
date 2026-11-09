package com.example.yuejie;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;
/** Runs only against the retained v0.26 APK on an offline isolated emulator. */
public final class FontUpgradeSeed extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){try{if(!Build.MODEL.contains("sdk"))throw new AssertionError("emulator only");Context c=getTargetContext();if(c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode!=26)throw new AssertionError("requires actual v0.26 APK");
  SyncEngine.get(c).remote=(path,body)->{throw new java.io.IOException("offline upgrade fixture");};
  c.getSharedPreferences("ReaderActivity",0).edit().clear().putBoolean("reader_v2",true).putBoolean("serif",true).putInt("font",37).putInt("line_spacing_percent",62).putInt("font_weight_amount",80).putString("page_turn_mode","simulation").putBoolean("night",true).putBoolean("volume_keys",false).putFloat("brightness",.42f).commit();
  ReaderSettings old=new ReaderSettings(c);if(!old.serif||old.typeface()==null)throw new AssertionError("old bundled font did not load");
  LocalLibrary l=new LocalLibrary(c);Book book=new Book(new JSONObject().put("book_id","999991234567893").put("book_name","Offline upgrade fixture").put("author","Test"));l.add(book);l.progress(book.id,"995",923,0,1,"Upgrade");l.toggleBookmark(book.id,"995",923,"Upgrade");l.close();Bundle b=new Bundle();b.putString("stream","PASS seeded actual v0.26 bundled-serif preferences, shelf, progress and bookmark");finish(0,b);
 }catch(Throwable e){Bundle b=new Bundle();b.putString("stream","FAIL "+android.util.Log.getStackTraceString(e));finish(1,b);}}
}
