package com.example.yuejie;
import android.content.*;import android.graphics.*;
/** Reader-only preferences, intentionally independent of the library data schema. */
final class ReaderSettings {
 final SharedPreferences prefs;int font,spacing,theme,fontWeight;boolean serif,night,comments,catalogDescending;float brightness;PageTurnMode pageTurnMode;
 static final int[] PAPERS={0xfff8f7f2,0xffe9e4ce,0xffdce8db,0xff202722};
 ReaderSettings(Context c){prefs=c.getSharedPreferences("ReaderActivity",0);pageTurnMode=PageTurnMode.parse(prefs.getString("page_turn_mode","cover"));boolean migrated=prefs.getBoolean("reader_v2",false);font=prefs.getInt("font",20);font=Math.max(16,Math.min(30,font));fontWeight=Math.max(0,Math.min(2,prefs.getInt("font_weight",0)));spacing=prefs.getInt("spacing",1);theme=prefs.getInt("theme",1);serif=prefs.getBoolean("serif",false);night=prefs.getBoolean("night",false);comments=prefs.getBoolean("paragraph_comments",true);catalogDescending=prefs.getBoolean("catalog_descending",false);brightness=prefs.getFloat("brightness",-1);if(!migrated){save();}}
 void save(){prefs.edit().putBoolean("catalog_descending",catalogDescending).putString("page_turn_mode",pageTurnMode.key).putBoolean("reader_v2",true).putInt("font",font).putInt("font_weight",fontWeight).putInt("spacing",spacing).putInt("theme",theme).putBoolean("serif",serif).putBoolean("night",night).putBoolean("paragraph_comments",comments).putFloat("brightness",brightness).apply();}
 int paper(){return night?PAPERS[3]:PAPERS[Math.max(0,Math.min(2,theme))];}int ink(){return night?0xffc4c6bf:0xff000000;}int muted(){return night?0xff858b82:0xff8c8a7b;}int surface(){return night?0xff282d28:0xfff6f4e9;}int accent(){return night?0xffafc8ab:PaperUi.GREEN;}
 float lineHeight(){return new float[]{1.45f,1.65f,1.85f}[Math.max(0,Math.min(2,spacing))];}Typeface typeface(){
  Typeface base=serif?PaperUi.SERIF:Typeface.create("sans-serif",Typeface.NORMAL);
  if(fontWeight==0)return base;
  if(android.os.Build.VERSION.SDK_INT>=28)return Typeface.create(base,fontWeight==1?500:700,false);
  return fontWeight==2?Typeface.create(base,Typeface.BOLD):base;
 }
 void configureBodyPaint(android.text.TextPaint paint){
  paint.setTypeface(typeface());
  // Android 7/8 have no numeric weight API. Slight emboldening keeps the selected family.
  boolean slight=android.os.Build.VERSION.SDK_INT<28&&fontWeight==1;
  paint.setStyle(slight?Paint.Style.FILL_AND_STROKE:Paint.Style.FILL);
  paint.setStrokeWidth(slight?paint.getTextSize()*.012f:0);
 }
}
