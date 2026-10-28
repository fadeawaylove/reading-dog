package com.example.yuejie;
import android.app.*;import android.os.*;import android.content.*;import android.view.*;import java.util.*;import org.json.*;import java.io.*;
/** Isolated real-device surface using captured text; never opens or modifies the user's library. */
public final class TypographyPreviewActivity extends Activity implements ReaderSurface.Host {
 ReaderSurface surface;ReaderSettings settings;String text,title;Map<Integer,Long> counts=new HashMap<>();int requested;ReaderPaginator model;
 public void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(5894);try{
  String asset=getIntent().getStringExtra("fixture");if(asset==null)asset="actual-font-sweep-24.json";ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getAssets().open(asset)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)out.write(b,0,n);}JSONObject fixture=new JSONObject(out.toString("UTF-8"));JSONArray paras=fixture.getJSONArray("paragraphs");JSONObject delegates=fixture.getJSONObject("inlineDelegates");StringBuilder body=new StringBuilder();
  for(int i=1;i<paras.length();i++){if(i>1)body.append("\n\n");if(delegates.has(Integer.toString(i)))counts.put(body.length(),2L);String value=paras.getJSONObject(i).getString("text");if(value.endsWith("\n"))value=value.substring(0,value.length()-1);body.append(value);}text=body.toString();title=paras.getJSONObject(0).getString("text");if(title.endsWith("\n"))title=title.substring(0,title.length()-1);
  settings=new ReaderSettings(this);settings.font=fixture.getJSONObject("config").getInt("b0")/3;settings.fontWeight=0;settings.spacing=fixture.optInt("spacingSetting",1);settings.serif=fixture.getString("referenceFont").contains("serif");settings.comments=true;settings.night=false;settings.theme=1;settings.pageTurnMode=PageTurnMode.NONE;requested=getIntent().getIntExtra("page",0);surface=new ReaderSurface(this,settings,this);setContentView(surface);
 if(getIntent().getBooleanExtra("software",false))surface.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
 }catch(Exception e){throw new IllegalStateException(e);}}
 public void sized(int w,int h){model=new ReaderPaginator(text,title,w,h,3,3,settings,counts);surface.setFrame(new ReaderSurface.Frame("reference",title,0,1,model,Math.min(requested,model.pages.size()-1)));}
 public ReaderSurface.Frame adjacent(int direction){ReaderSurface.Frame f=surface.frame;int p=f.pageIndex+direction;return p<0||p>=model.pages.size()?null:new ReaderSurface.Frame(f.id,f.title,0,1,model,p);}
 public void commit(ReaderSurface.Frame f){surface.setFrame(f);}public void needChapter(int dir){}public void center(){}public int battery(){return 100;}public Map<Integer,Long> commentCounts(String id){return counts;}public void paragraph(int offset){android.widget.Toast.makeText(this,"段评原文偏移 "+offset,0).show();}public String bookProgress(ReaderSurface.Frame f){return (f.pageIndex+1)+"/"+model.pages.size();}
}
