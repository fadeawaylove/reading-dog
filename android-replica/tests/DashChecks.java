package com.example.yuejie;
import android.app.*;import android.os.*;import android.graphics.*;import android.text.TextPaint;import java.io.*;

/** Compare the real Canvas output with an intact native punctuation run. */
public final class DashChecks extends Instrumentation {
 int checks,failures;final String sentence="邵澜往前走了几步，立刻感受到了不对劲——和之前的寂静不同，此刻整个8层变得闹哄哄的。";
 void report(boolean value,String label){checks++;if(!value)failures++;Bundle b=new Bundle();b.putString("stream",(value?"PASS ":"FAIL ")+label+"\n");sendStatus(0,b);}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 void family(String name,Typeface face)throws Exception{
  TextPaint paint=new TextPaint(3);paint.setTypeface(face);paint.setTextSize(64);paint.setColor(Color.BLACK);
  ReaderTextLayout pair=new ReaderTextLayout("——",paint,600,0,false,0,false,0,false);
  Bitmap actual=Bitmap.createBitmap(600,150,Bitmap.Config.ARGB_8888),expected=Bitmap.createBitmap(600,150,Bitmap.Config.ARGB_8888);
  pair.draw(new Canvas(actual));ReaderTextLayout.Line line=pair.lines.get(0);new Canvas(expected).drawTextRun("——",0,2,0,2,line.origins[0],line.baseline,false,paint);
  report(actual.sameAs(expected),name+" pair pixels match one native shaped run");
  Bitmap comparison=Bitmap.createBitmap(600,320,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(comparison);canvas.drawColor(Color.WHITE);canvas.drawBitmap(actual,0,0,null);canvas.drawBitmap(expected,0,160,null);
  try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"dash-"+name+".png"))){comparison.compress(Bitmap.CompressFormat.PNG,100,out);}actual.recycle();expected.recycle();comparison.recycle();
  int at=sentence.indexOf("——");boolean noInternalSpacing=true,noSplit=true;
  for(int width:new int[]{20,220,360,470,600,940}){
   ReaderTextLayout layout=new ReaderTextLayout(sentence,paint,width,0,false,0);
   for(ReaderTextLayout.Line row:layout.lines){if(row.start==at+1||row.end==at+1)noSplit=false;for(int i=row.first+1;i<row.last;i++){ReaderTextLayout.Cluster a=layout.clusters.get(i-1),b=layout.clusters.get(i);if(a.start==at&&b.start==at+1&&Math.abs(row.origins[i-row.first]-row.origins[i-1-row.first]-a.advance)>.01f)noInternalSpacing=false;}}
  }
  report(noInternalSpacing,name+" justified text does not insert a gap inside the dash pair");report(noSplit,name+" dash pair is never split across lines");
  boolean unchanged=true;String plain="这是正常的一横与破折号—，不能删除；English words and extraordinarilylongword，😀也保留。";
  for(int size:new int[]{16,30,60})for(int width:new int[]{240,360,720}){
   paint.setTextSize(size);ReaderTextLayout now=new ReaderTextLayout(plain,paint,width,2,false,0);LegacyReaderTextLayout old=new LegacyReaderTextLayout(plain,paint,width,2,false,0);
   if(now.lines.size()!=old.lines.size())unchanged=false;else for(int i=0;i<now.lines.size();i++){ReaderTextLayout.Line n=now.lines.get(i);LegacyReaderTextLayout.Line o=old.lines.get(i);if(n.start!=o.start||n.end!=o.end||!java.util.Arrays.equals(n.origins,o.origins))unchanged=false;}
  }
  report(unchanged,name+" ordinary characters/single dashes retain pre-fix line boundaries and positions");
 }
 public void onStart(){Bundle result=new Bundle();try{
  ReaderHyphenator.load(getTargetContext());family("system",Typeface.DEFAULT);family("system-serif",Typeface.create("serif",0));family("bundled-sans",Typeface.createFromAsset(getTargetContext().getAssets(),"reader-body.ttf"));family("bundled-serif",Typeface.createFromAsset(getTargetContext().getAssets(),"reader-serif.ttf"));
  result.putString("stream",(failures==0?"PASS ":"FAIL ")+checks+" checks, "+failures+" failures\n");finish(failures==0?0:1,result);
 }catch(Throwable error){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(1,result);}}
}
