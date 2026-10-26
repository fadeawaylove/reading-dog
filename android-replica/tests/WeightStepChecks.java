package com.example.yuejie;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.text.*;import org.json.*;import java.io.*;
/** Full native raster sweep; quantized images are proxies, not e-ink hardware measurements. */
public class WeightStepChecks extends Instrumentation {
 final int[] steps={1,2,5,10,20,25,50,100};
 final long[] transitions=new long[8],failures=new long[8];
 final int[] minRaw=new int[8],minGray=new int[8],minMono=new int[8];
 final double[] minGain=new double[8];int rendered;JSONArray cases=new JSONArray();
 public void onCreate(Bundle args){start();}
 void status(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(0,b);}
 public void onStart(){Bundle result=new Bundle();boolean success=false;try{
  for(int i=0;i<8;i++){minRaw[i]=minGray[i]=minMono[i]=Integer.MAX_VALUE;minGain[i]=Double.POSITIVE_INFINITY;}
  ReaderSettings settings=new ReaderSettings(getTargetContext());
  for(boolean serif:new boolean[]{false,true})for(int density=1;density<=3;density++){
   for(int font=16;font<=30;font++){
    settings.serif=serif;settings.font=font;
    TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setTextSize(font*density);paint.setColor(Color.BLACK);
    int w=600,h=font*density*3;Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);int[] pixels=new int[w*h];int[][] previous=new int[8][];long[] oldInk=new long[8];JSONArray inks=new JSONArray();
    for(int amount=0;amount<=100;amount++){
     settings.fontWeight=amount;settings.configureBodyPaint(paint);canvas.drawColor(Color.WHITE);
     canvas.drawText("永国小说",8,font*density,paint);canvas.drawText("读露Aa1。",8,font*density*2.4f,paint);
     bitmap.getPixels(pixels,0,w,0,0,w,h);long ink=0;for(int pixel:pixels)ink+=255-(pixel&255);inks.put(ink);rendered++;
     for(int s=0;s<8;s++)if(amount%steps[s]==0){
      if(previous[s]!=null){int raw=0,gray=0,mono=0;for(int i=0;i<pixels.length;i++){int a=previous[s][i]&255,b=pixels[i]&255;if(a!=b)raw++;if((a+8)/17!=(b+8)/17)gray++;if((a<128)!=(b<128))mono++;}
       double gain=(double)(ink-oldInk[s])/oldInk[s];transitions[s]++;minRaw[s]=Math.min(minRaw[s],raw);minGray[s]=Math.min(minGray[s],gray);minMono[s]=Math.min(minMono[s],mono);minGain[s]=Math.min(minGain[s],gain);
       if(raw==0||gray==0||mono==0||gain<.01)failures[s]++;
      }
      if(previous[s]==null)previous[s]=new int[pixels.length];System.arraycopy(pixels,0,previous[s],0,pixels.length);oldInk[s]=ink;
     }
    }
    cases.put(new JSONObject().put("serif",serif).put("fontSp",font).put("scaledDensity",density).put("inkByAmount",inks));bitmap.recycle();
   }
   status("Swept "+(serif?"serif":"sans")+" scale="+density+" all 15 font sizes, all 101 amounts");
  }
  JSONArray candidates=new JSONArray();int selected=0;for(int i=0;i<8;i++){JSONObject row=new JSONObject().put("step",steps[i]).put("transitions",transitions[i]).put("failed",failures[i]).put("minChanged8bit",minRaw[i]).put("minChanged16gray",minGray[i]).put("minChanged1bit",minMono[i]).put("minRelativeInkGain",minGain[i]);candidates.put(row);if(selected==0&&failures[i]==0)selected=steps[i];}
  JSONObject report=new JSONObject().put("device",Build.MODEL).put("sdk",Build.VERSION.SDK_INT).put("rendered",rendered).put("criterion","all adjacent levels: raw, 16-gray and 1-bit pixels differ; ink gain >= 1 percent (engineering threshold, not human visibility guarantee)").put("selectedStep",selected).put("candidates",candidates).put("cases",cases);
  File file=new File(getTargetContext().getExternalFilesDir(null),"weight-step-sweep.json");try(Writer writer=new OutputStreamWriter(new FileOutputStream(file),"UTF-8")){writer.write(report.toString(2));}
  result.putString("stream","SWEEP "+rendered+" renders; selected="+selected+"\n"+candidates.toString(2)+"\nReport: "+file+"\n");success=selected==ReaderSettings.FONT_WEIGHT_STEP;if(!success)result.putString("stream",result.getString("stream")+"FAIL configured UI step differs from measured selection\n");
 }catch(Throwable t){result.putString("stream","FAIL "+android.util.Log.getStackTraceString(t));}finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
