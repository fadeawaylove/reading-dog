package com.example.yuejie;
import android.content.*;import android.graphics.*;
/** Reader-only preferences, intentionally independent of the library data schema. */
final class ReaderSettings {
 private static Typeface referenceSans,referenceSerif;
 static synchronized void loadFonts(Context c){if(referenceSans==null){referenceSans=Typeface.createFromAsset(c.getAssets(),"reader-body.ttf");referenceSerif=Typeface.createFromAsset(c.getAssets(),"reader-serif.ttf");}ReaderHyphenator.load(c);}
 // Native raster sweep: 2 families x 15 sizes x 3 scales; step 10 passes all transitions.
 static final int FONT_WEIGHT_STEP=10,FONT_WEIGHT_MAX=100;
 static int normalizeFontWeight(int value){int bounded=Math.max(0,Math.min(FONT_WEIGHT_MAX,value));return Math.min(FONT_WEIGHT_MAX,((bounded+FONT_WEIGHT_STEP/2)/FONT_WEIGHT_STEP)*FONT_WEIGHT_STEP);}
 final SharedPreferences prefs;int font,spacing,theme,fontWeight;boolean serif,night,comments,catalogDescending;float brightness;PageTurnMode pageTurnMode;
 static final int[] PAPERS={0xfff8f7f2,0xffe9e4ce,0xffdce8db,0xff202722};
 ReaderSettings(Context c){loadFonts(c);prefs=c.getSharedPreferences("ReaderActivity",0);pageTurnMode=PageTurnMode.parse(prefs.getString("page_turn_mode","cover"));boolean migrated=prefs.getBoolean("reader_v2",false);font=prefs.getInt("font",20);font=Math.max(16,Math.min(30,font));int legacyWeight=prefs.getInt("font_weight",0);fontWeight=normalizeFontWeight(prefs.getInt("font_weight_amount",legacyWeight==1?33:legacyWeight==2?100:0));spacing=prefs.getInt("spacing",1);theme=prefs.getInt("theme",1);serif=prefs.getBoolean("serif",false);night=prefs.getBoolean("night",false);comments=prefs.getBoolean("paragraph_comments",true);catalogDescending=prefs.getBoolean("catalog_descending",false);brightness=prefs.getFloat("brightness",-1);if(!migrated){save();}}
 void save(){fontWeight=normalizeFontWeight(fontWeight);prefs.edit().putBoolean("catalog_descending",catalogDescending).putString("page_turn_mode",pageTurnMode.key).putBoolean("reader_v2",true).putInt("font",font).putInt("font_weight_amount",fontWeight).putInt("spacing",spacing).putInt("theme",theme).putBoolean("serif",serif).putBoolean("night",night).putBoolean("paragraph_comments",comments).putFloat("brightness",brightness).apply();}
 int paper(){return night?PAPERS[3]:PAPERS[Math.max(0,Math.min(2,theme))];}int ink(){return night?0xffc4c6bf:0xff000000;}int muted(){return night?0xff858b82:0xff000000;}int surface(){return night?0xff282d28:0xfff6f4e9;}int accent(){return night?0xffafc8ab:PaperUi.GREEN;}
 float lineHeight(){return 1.4f+new float[]{.65f,.8f,1f}[Math.max(0,Math.min(2,spacing))];}Typeface typeface(){return serif?referenceSerif:referenceSans;}

 void configureBodyPaint(android.text.TextPaint paint){
  paint.setTypeface(typeface());
  // Many system CJK fonts quantize numeric weights. A small continuous stroke
  // preserves glyph metrics and antialiasing; the UI step is selected by raster checks.
  boolean slight=fontWeight>0;
  paint.setStyle(slight?Paint.Style.FILL_AND_STROKE:Paint.Style.FILL);
  paint.setStrokeWidth(slight?paint.getTextSize()*.035f*fontWeight/100f:0);
 }
}
