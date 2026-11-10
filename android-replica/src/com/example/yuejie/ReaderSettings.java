package com.example.yuejie;
import android.content.*;import android.graphics.*;
/** Reader-only preferences, intentionally independent of the library data schema. */
final class ReaderSettings {
 static void loadFonts(Context c){ReaderHyphenator.load(c);}
 // Native raster sweep: 2 families x 15 sizes x 3 scales; step 10 passes all transitions.
 static final int FONT_MIN=16,FONT_MAX=60;
 static int normalizeFont(int value){return Math.max(FONT_MIN,Math.min(FONT_MAX,value));}
 static final int FONT_WEIGHT_STEP=10,FONT_WEIGHT_MAX=100;
 static int normalizeFontWeight(int value){int bounded=Math.max(0,Math.min(FONT_WEIGHT_MAX,value));return Math.min(FONT_WEIGHT_MAX,((bounded+FONT_WEIGHT_STEP/2)/FONT_WEIGHT_STEP)*FONT_WEIGHT_STEP);}
 static final int LINE_SPACING_MIN=160,LINE_SPACING_MAX=320;
 static int legacySpacing(int value){return new int[]{205,220,240}[Math.max(0,Math.min(2,value))];}
 static int normalizeSpacing(int value){return Math.max(LINE_SPACING_MIN,Math.min(LINE_SPACING_MAX,value));}
 final SharedPreferences prefs;final ReaderFonts fonts;String fontFamily,fontKey;Typeface selectedTypeface;int font,spacing,lineSpacing,spacingPercent,theme,fontWeight;boolean legacyLineSpacing;boolean commentPrefetch;boolean einkStable;boolean serif,night,comments,catalogDescending,volumeKeys;float brightness;PageTurnMode pageTurnMode;
 static final int[] PAPERS={0xfff8f7f2,0xffe9e4ce,0xffdce8db,0xff202722};
 int loadingNoticeDelay(){return EInkSupport.loadingNoticeDelay(einkStable);}
 ReaderSettings(Context c){einkStable=EInkSupport.stableDisplay(android.os.Build.MANUFACTURER,android.os.Build.BRAND,android.os.Build.MODEL,android.os.Build.DEVICE,android.os.Build.PRODUCT);loadFonts(c);fonts=ReaderFonts.get(c);prefs=c.getSharedPreferences("ReaderActivity",0);String family=prefs.getString("font_family",null);if(family==null)family=prefs.getBoolean("serif",false)?"serif":"sans";fontFamily=fonts.resolve(family);if(!fontFamily.equals(prefs.getString("font_family","")))prefs.edit().putString("font_family",fontFamily).apply();refreshTypeface();pageTurnMode=PageTurnMode.parse(prefs.getString("page_turn_mode","cover"));boolean migrated=prefs.getBoolean("reader_v2",false);font=prefs.getInt("font",20);font=normalizeFont(font);int legacyWeight=prefs.getInt("font_weight",0);fontWeight=normalizeFontWeight(prefs.getInt("font_weight_amount",legacyWeight==1?33:legacyWeight==2?100:0));spacing=prefs.getInt("spacing",1);lineSpacing=normalizeSpacing(prefs.getInt("line_spacing_hundredths",legacySpacing(spacing)));legacyLineSpacing=!prefs.contains("line_spacing_percent");spacingPercent=Math.max(0,Math.min(100,prefs.getInt("line_spacing_percent",Math.round((lineSpacing/100f-1f)*100))));volumeKeys=prefs.getBoolean("volume_keys",true);theme=prefs.getInt("theme",1);serif="serif".equals(fontFamily);night=prefs.getBoolean("night",false);comments=prefs.getBoolean("paragraph_comments",true);commentPrefetch=prefs.getBoolean("comment_content_prefetch",true);catalogDescending=prefs.getBoolean("catalog_descending",false);brightness=prefs.getFloat("brightness",-1);if(legacyLineSpacing){android.text.TextPaint p=new android.text.TextPaint();p.setTextSize(font*c.getResources().getDisplayMetrics().scaledDensity);configureBodyPaint(p);float ink=ReaderTextLayout.inkHeight(p);spacingPercent=Math.max(0,Math.min(100,Math.round((p.getTextSize()*lineSpacing/100f-ink-1)*100/Math.max(1,ink))));}if(!migrated){save();}}
 void save(){refreshTypeface();font=normalizeFont(font);if(!legacyLineSpacing)prefs.edit().putInt("line_spacing_percent",spacingPercent).apply();lineSpacing=normalizeSpacing(lineSpacing);fontWeight=normalizeFontWeight(fontWeight);prefs.edit().putBoolean("catalog_descending",catalogDescending).putString("page_turn_mode",pageTurnMode.key).putBoolean("reader_v2",true).putInt("font",font).putInt("font_weight_amount",fontWeight).putInt("spacing",spacing).putInt("line_spacing_hundredths",lineSpacing).putBoolean("volume_keys",volumeKeys).putInt("theme",theme).putBoolean("serif",serif).putString("font_family",fontFamily).putBoolean("night",night).putBoolean("paragraph_comments",comments).putBoolean("comment_content_prefetch",commentPrefetch).putFloat("brightness",brightness).apply();}
 int paper(){return night?PAPERS[3]:PAPERS[Math.max(0,Math.min(2,theme))];}int ink(){return night?0xffc4c6bf:0xff000000;}int muted(){return night?0xff858b82:0xff000000;}int surface(){return night?0xff282d28:0xfff6f4e9;}int accent(){return night?0xffafc8ab:PaperUi.GREEN;}
 float lineHeight(){return normalizeSpacing(lineSpacing)/100f;}Typeface typeface(){return selectedTypeface;}
 void refreshTypeface(){fontFamily=fonts.resolve(fontFamily);fontKey=fonts.key(fontFamily);selectedTypeface=fonts.face(fontFamily);serif="serif".equals(fontFamily);}
 boolean selectFont(String id){if(!ReaderFonts.SYSTEM.equals(id)&&!fonts.available(id))return false;fontFamily=id;refreshTypeface();return true;}
 String fontLabel(){return "sans".equals(fontFamily)?"黑体":"serif".equals(fontFamily)?"宋体":"系统字体";}

 void configureBodyPaint(android.text.TextPaint paint){
  paint.setTypeface(typeface());
  // Many system CJK fonts quantize numeric weights. A small continuous stroke
  // preserves glyph metrics and antialiasing; the UI step is selected by raster checks.
  boolean slight=fontWeight>0;
  paint.setStyle(slight?Paint.Style.FILL_AND_STROKE:Paint.Style.FILL);
  paint.setStrokeWidth(slight?paint.getTextSize()*.035f*fontWeight/100f:0);
 }
}
