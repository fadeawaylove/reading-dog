package com.example.yuejie;
import android.content.*;import android.graphics.*;
/** Reader-only preferences, intentionally independent of the library data schema. */
final class ReaderSettings {
 final SharedPreferences prefs;int font,spacing,theme;boolean serif,night,comments,catalogDescending;float brightness;PageTurnMode pageTurnMode;
 static final int[] PAPERS={0xfff8f7f2,0xffe9e4ce,0xffdce8db,0xff202722};
 ReaderSettings(Context c){prefs=c.getSharedPreferences("ReaderActivity",0);pageTurnMode=PageTurnMode.parse(prefs.getString("page_turn_mode","cover"));boolean migrated=prefs.getBoolean("reader_v2",false);font=prefs.getInt("font",20);font=Math.max(16,Math.min(30,font));spacing=prefs.getInt("spacing",1);theme=prefs.getInt("theme",1);serif=prefs.getBoolean("serif",false);night=prefs.getBoolean("night",false);comments=prefs.getBoolean("paragraph_comments",true);catalogDescending=prefs.getBoolean("catalog_descending",false);brightness=prefs.getFloat("brightness",-1);if(!migrated){save();}}
 void save(){prefs.edit().putBoolean("catalog_descending",catalogDescending).putString("page_turn_mode",pageTurnMode.key).putBoolean("reader_v2",true).putInt("font",font).putInt("spacing",spacing).putInt("theme",theme).putBoolean("serif",serif).putBoolean("night",night).putBoolean("paragraph_comments",comments).putFloat("brightness",brightness).apply();}
 int paper(){return night?PAPERS[3]:PAPERS[Math.max(0,Math.min(2,theme))];}int ink(){return night?0xffc4c6bf:0xff262720;}int muted(){return night?0xff858b82:0xff8c8a7b;}int surface(){return night?0xff282d28:0xfff6f4e9;}int accent(){return night?0xffafc8ab:PaperUi.GREEN;}
 float lineHeight(){return new float[]{1.45f,1.65f,1.85f}[Math.max(0,Math.min(2,spacing))];}Typeface typeface(){return serif?PaperUi.SERIF:Typeface.create("sans-serif",Typeface.NORMAL);}
}
