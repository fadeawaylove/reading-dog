package com.example.yuejie;

import android.graphics.*;
import android.text.TextPaint;
import android.icu.text.BreakIterator;
import java.util.*;

/** Immutable shaped clusters and line geometry. Offsets always refer to the original string. */
final class ReaderTextLayout {
 // TTHtmlParser serializes the title/body em ratio with six fractional digits.
 // Preserve that round trip: tiny differences affect Android's hinted advances.
 static float headingSize(float body,float title,float density){float ratio=Float.parseFloat(String.format(Locale.ROOT,"%.6f",title/body));return (body/density*ratio)*density;}
 static final class Cluster {
  final int start,end,kind;final float advance;float before,after;
  Cluster(int start,int end,int kind,float advance){this.start=start;this.end=end;this.kind=kind;this.advance=advance;}
 }
 static final class Line {
  final int start,end,first,last;final float top,bottom,baseline,left,right,hyphenWidth;final float[] x,widths,origins;
  Line(int start,int end,int first,int last,float top,float bottom,float baseline,float left,float right,float hyphenWidth,float[] x,float[] widths,float[] origins){this.start=start;this.end=end;this.first=first;this.last=last;this.top=top;this.bottom=bottom;this.baseline=baseline;this.left=left;this.right=right;this.hyphenWidth=hyphenWidth;this.x=x;this.widths=widths;this.origins=origins;}
 }
 final String text;final TextPaint paint;final int width;final float lineSpace,lineHeight;final List<Cluster> clusters;final List<Line> lines;
 ReaderTextLayout(String text,TextPaint paint,int width,float lineSpace,boolean heading,float bubble){
  this(text,paint,width,lineSpace,heading,bubble,false,0);
 }
 ReaderTextLayout(String text,TextPaint paint,int width,float lineSpace,boolean heading,float bubble,boolean tight,float bubbleHeight){
  this.text=text;this.paint=paint;this.width=width;this.lineSpace=lineSpace;lineHeight=tight?inkHeight(paint):paint.getTextSize()*1.4f;
  ArrayList<Cluster> units=new ArrayList<>();float[] advances=new float[text.length()];TextPaint measuring=new TextPaint();measuring.setTypeface(paint.getTypeface());measuring.setTextSize(paint.getTextSize());measuring.setFakeBoldText(paint.isFakeBoldText());measuring.getTextWidths(text,advances);
  BreakIterator iterator=BreakIterator.getCharacterInstance(Locale.ROOT);iterator.setText(text);int start=iterator.first();
  for(int end=iterator.next();end!=BreakIterator.DONE;start=end,end=iterator.next()){
   float advance=0;for(int i=start;i<end;i++)advance+=advances[i];int kind=heading?0:compressType(text.codePointAt(start));if(kind!=0){Rect bounds=new Rect();measuring.getTextBounds(text,start,end,bounds);if(Math.abs(advance-paint.getTextSize())>1)kind=0;else if(bounds.width()>0&&bounds.right<advance){kind=bounds.left>=advance*.5f?2:bounds.right<=advance*.5f?1:0;}}units.add(new Cluster(start,end,kind,advance));
  }
  for(int i=1;i<units.size();i++){Cluster a=units.get(i-1),b=units.get(i);if(a.kind!=0&&b.kind!=0){if(a.kind==1)a.after=a.advance*.5f;if(b.kind==2)b.before=b.advance*.5f;}}
  clusters=Collections.unmodifiableList(units);ArrayList<Line> built=new ArrayList<>();int first=0;
  while(first<units.size()){
   float indent=!heading&&first==0?2*paint.getTextSize():0,available=Math.max(1,width-indent),used=0,capacity=0,compression=0,ratio=0,hyphenWidth=0;int last=first;
   while(last<units.size()){
    int end=last+1;while(end<units.size()&&!canBreak(units,end,text)&&(word(text.codePointAt(units.get(last).start))||end-last<4))end++;
    float previous=last>first&&units.get(last-1).kind==1&&units.get(last).kind!=0?units.get(last-1).after:0;
    float group=-previous,compress=-previous;boolean punctuation=false;for(int i=last;i<end;i++){Cluster c=units.get(i);float before=i==first&&c.kind==2?c.advance*.5f:c.before,after=i==end-1?0:c.after;group+=c.advance-before-after;if(c.kind!=0){punctuation=true;compress+=Math.max(0,c.advance*.5f-before-after);}}
    if(end==units.size())group+=bubble;
    if(used+group>available+.01f&&(!punctuation||used+group>available+capacity+compress+.01f)){
     if(!heading&&word(text.codePointAt(units.get(last).start))){int wordStart=last,wordEnd=last;while(wordStart>0&&word(text.codePointAt(units.get(wordStart-1).start)))wordStart--;while(wordEnd<units.size()&&word(text.codePointAt(units.get(wordEnd).start)))wordEnd++;int begin=units.get(wordStart).start;boolean[] flags=ReaderHyphenator.breaks(text.substring(begin,units.get(wordEnd-1).end));int split=-1;float part=0,selected=0;for(int i=last;i<wordEnd;i++){part+=units.get(i).advance;int at=units.get(i).end-begin;if(flags[at]&&used+part+paint.getTextSize()*.5f<=available+.0001f){split=i+1;selected=part;}}if(split>last&&(wordEnd==end||last==first)){last=split;hyphenWidth=paint.getTextSize()*.5f;used+=selected+hyphenWidth;}}
     break;
    }
    if(used+group>available){float groupRaw=0,groupCapacity=0;for(int i=last;i<end;i++){Cluster c=units.get(i);groupRaw+=c.advance;if(c.kind!=0)groupCapacity+=c.advance*.5f;}if(end==units.size())groupRaw+=bubble;ratio=(used+groupRaw-available)/Math.max(.001f,capacity+groupCapacity);}
    used+=group;capacity+=compress;last=end;compression=Math.max(0,used-available);
   }
   if(last==first){used=0;while(last<units.size()&&(last==first||used+units.get(last).advance<=available)){used+=units.get(last).advance;last++;}compression=0;capacity=0;}
   boolean end=last==units.size();
   float extra=0;
   float[] x=new float[last-first],w=new float[x.length],origin=new float[x.length];float cursor=indent;Cluster tail=units.get(last-1);float tailCapacity=tail.kind==1?tail.advance*.5f:0;boolean tailOnly=!(end&&bubble>0)&&compression<=tailCapacity+.01f;
   for(int i=first;i<last;i++){Cluster c=units.get(i);int k=i-first;float before=i==first&&c.kind==2?c.advance*.5f:c.before,after=i==last-1?0:c.after;float shrink=tailOnly?(i==last-1?compression:0):(c.kind!=0?Math.max(0,c.advance*.5f*ratio-before-after):0);x[k]=cursor;origin[k]=cursor-before-(c.kind==2?shrink:0);w[k]=c.advance-before-after-shrink;cursor+=w[k];}
   if(!heading&&!end&&last-first>1){int gaps=0;for(int i=first+1;i<last;i++)if(!(word(text.codePointAt(units.get(i-1).start))&&word(text.codePointAt(units.get(i).start))))gaps++;extra=gaps==0?0:Math.max(0,available-cursor+indent-hyphenWidth)/gaps;int gap=0;for(int i=1;i<x.length;i++){if(!(word(text.codePointAt(units.get(first+i-1).start))&&word(text.codePointAt(units.get(first+i).start))))gap++;x[i]+=extra*gap;origin[i]+=extra*gap;}cursor+=extra*gaps;}
   float top=built.size()*(lineHeight+lineSpace),baseline=top+lineSpace/2+(lineHeight-paint.getFontMetrics().descent-paint.getFontMetrics().ascent)/2;
   float bottom=top+lineHeight+lineSpace;
   if(tight){
    Rect ink=new Rect();measuring.getTextBounds(text,units.get(first).start,units.get(last-1).end,ink);
    if(hyphenWidth>0){Rect dash=new Rect();measuring.getTextBounds("-",0,1,dash);ink.union(dash);}
    if(ink.isEmpty()){ink.top=(int)Math.floor(paint.getFontMetrics().ascent);ink.bottom=(int)Math.ceil(paint.getFontMetrics().descent);}
    float stroke=paint.getStyle()==Paint.Style.FILL?0:paint.getStrokeWidth()/2;
    // One physical pixel between measured ink envelopes, including stroke.
    float contentHeight=ink.height()+2*stroke;
    if(end)contentHeight=Math.max(contentHeight,bubbleHeight);
    top=built.isEmpty()?0:built.get(built.size()-1).bottom;
    bottom=top+contentHeight+1+lineSpace;
    baseline=top+(1+lineSpace)/2+(contentHeight-ink.height())/2-ink.top;
   }
   built.add(new Line(units.get(first).start,units.get(last-1).end,first,last,top,bottom,baseline,indent,cursor,hyphenWidth,x,w,origin));first=last;
  }
  lines=Collections.unmodifiableList(built);
 }
 static float inkHeight(TextPaint paint){TextPaint fill=new TextPaint(paint);fill.setStyle(Paint.Style.FILL);Rect bounds=new Rect();fill.getTextBounds("国",0,1,bounds);return bounds.height()+(paint.getStyle()==Paint.Style.FILL?0:paint.getStrokeWidth());}
 static int compressType(int cp){switch(cp){case 0x2018:case 0x201c:case 0x300a:case 0x3010:case 0xff08:return 2;case 0x2019:case 0x201d:case 0x3001:case 0x3002:case 0x300b:case 0x3011:case 0xff01:case 0xff09:case 0xff0c:case 0xff1a:case 0xff1b:case 0xff1f:return 1;default:return 0;}}
 static boolean canBreak(List<Cluster> units,int next,String text){if(next==units.size())return true;int a=text.codePointAt(units.get(next-1).start),b=text.codePointAt(units.get(next).start);if(compressType(a)==2||compressType(b)==1||b==0x2026)return false;if(a==0x2014&&b==0x2014)return false;return !(word(a)&&word(b));}
 static boolean word(int cp){return cp<128&&(Character.isLetterOrDigit(cp)||cp=='_'||cp=='.');}
 CharSequence getText(){return text;}int getWidth(){return width;}int getLineCount(){return lines.size();}int getHeight(){return (int)Math.ceil(lines.isEmpty()?0:lines.get(lines.size()-1).bottom);}int getLineStart(int i){return lines.get(i).start;}int getLineEnd(int i){return lines.get(i).end;}float getLineTop(int i){return lines.get(i).top;}float getLineBottom(int i){return lines.get(i).bottom;}float getLineBaseline(int i){return lines.get(i).baseline;}
 float getPrimaryHorizontal(int offset){for(Line line:lines)if(line.start<=offset&&offset<line.end){for(int i=line.first;i<line.last;i++)if(clusters.get(i).end>offset)return line.x[i-line.first];}return lines.isEmpty()?0:lines.get(lines.size()-1).right;}
 void draw(Canvas canvas){for(Line line:lines){for(int i=line.first;i<line.last;i++){Cluster c=clusters.get(i);canvas.drawTextRun(text,c.start,c.end,0,text.length(),line.origins[i-line.first],line.baseline,false,paint);}if(line.hyphenWidth>0)canvas.drawText("-",line.right,line.baseline,paint);}}
}
