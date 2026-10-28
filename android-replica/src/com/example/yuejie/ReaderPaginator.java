package com.example.yuejie;
import android.graphics.*;import android.text.TextPaint;import java.util.*;
/** Immutable original offsets and shared geometry for pagination, drawing and hit testing. */
final class ReaderPaginator {
 static final class Page {final int start,end;final boolean first;final List<Fragment> fragments;Page(int a,int b,boolean f,List<Fragment> parts){start=a;end=b;first=f;fragments=Collections.unmodifiableList(new ArrayList<>(parts));}}
 static final class Fragment {final ReaderTextLayout layout;final int firstLine,lastLine;final float y;final int paragraphStart,paragraphEnd;Fragment(ReaderTextLayout l,int first,int last,float y,int start,int end){layout=l;firstLine=first;lastLine=last;this.y=y;paragraphStart=start;paragraphEnd=end;}}
 final boolean comments;final float density,bubbleWidth;final String text,title;final List<Page> pages;final Set<Integer> commentParagraphs;
 final TextPaint paint=new TextPaint(),titlePaint=new TextPaint();final float left,top,bottom,paragraphGap,lineSpace;final int width,height;final ReaderTextLayout heading;final float titleHeight;
 ReaderPaginator(String text,String title,int width,int height,float density,float scaled,ReaderSettings settings){this(text,title,width,height,density,scaled,settings,Collections.emptyMap());}
 ReaderPaginator(String text,String title,int width,int height,float density,float scaled,ReaderSettings settings,Map<Integer,Long> counts){
  this.text=text;this.title=title;this.width=width;this.height=height;this.density=density;comments=settings.comments;paint.setAntiAlias(true);titlePaint.setAntiAlias(true);
  Set<Integer> active=new HashSet<>();if(comments)for(Map.Entry<Integer,Long> e:counts.entrySet())if(e.getValue()!=null&&e.getValue()>0)active.add(e.getKey());commentParagraphs=Collections.unmodifiableSet(active);
  left=24*density;top=30*density;bottom=height-37*density;paragraphGap=(settings.spacing==2?26:20)*density;bubbleWidth=(settings.font<=19?24:settings.font<=29?26:30)*density;
  paint.setTextSize(settings.font*scaled);settings.configureBodyPaint(paint);paint.setColor(settings.ink());paint.setLetterSpacing(0);lineSpace=Math.round(paint.getTextSize()*new double[]{.65,.8,1}[Math.max(0,Math.min(2,settings.spacing))]);
  titlePaint.setTypeface(paint.getTypeface());titlePaint.setFakeBoldText(true);titlePaint.setColor(settings.ink());int increment=settings.font<20?4:settings.font<25?3:settings.font<30?2:1;titlePaint.setTextSize(ReaderTextLayout.headingSize(paint.getTextSize(),(settings.font+increment)*scaled,density));
  int available=Math.max(1,Math.round(width-2*left));heading=new ReaderTextLayout(title,titlePaint,available,titlePaint.getTextSize()*.5f,true,0);titleHeight=heading.lines.isEmpty()?0:heading.lines.get(heading.lines.size()-1).bottom+paragraphGap;pages=Collections.unmodifiableList(paginate(available));
 }
 ReaderTextLayout paragraph(String value,int width,float ignored){return new ReaderTextLayout(value,paint,width,lineSpace,false,0);}
 private List<Page> paginate(int available){
  List<Page> result=new ArrayList<>();List<Fragment> fragments=new ArrayList<>();float y=top+titleHeight;int pageStart=0,pos=0;boolean first=true;
  while(pos<text.length()){
   int end=text.indexOf("\n\n",pos);if(end<0)end=text.length();int next=end;while(next<text.length()&&text.charAt(next)=='\n')next++;if(end==pos){pos=Math.max(pos+1,next);continue;}
   ReaderTextLayout layout=new ReaderTextLayout(text.substring(pos,end),paint,available,lineSpace,false,commentParagraphs.contains(pos)?bubbleWidth+4*density:0);int line=0;
   while(line<layout.getLineCount()){
    int last=line;float room=bottom-y;while(last<layout.getLineCount()&&layout.getLineBottom(last)-layout.lineSpace/2-layout.getLineTop(line)<=room+.001f)last++;
    if(last==line){if(!fragments.isEmpty()||first){int split=pos+layout.getLineStart(line);result.add(new Page(pageStart,split,first,fragments));pageStart=split;first=false;fragments.clear();y=top;continue;}last=line+1;}
    fragments.add(new Fragment(layout,line,last,y,pos,end));y+=layout.getLineBottom(last-1)-layout.getLineTop(line);line=last;
    if(line<layout.getLineCount()){int split=pos+layout.getLineStart(line);result.add(new Page(pageStart,split,first,fragments));pageStart=split;first=false;fragments.clear();y=top;}
   }
   y+=paragraphGap;pos=next;
  }
  if(!fragments.isEmpty()||result.isEmpty())result.add(new Page(pageStart,text.length(),first,fragments));else if(result.get(result.size()-1).end!=text.length()){Page p=result.remove(result.size()-1);result.add(new Page(p.start,text.length(),p.first,p.fragments));}
  int previous=0;for(Page p:result){if(p.start!=previous||p.end<p.start)throw new IllegalStateException("Non-contiguous pagination");previous=p.end;}if(previous!=text.length())throw new IllegalStateException("Incomplete pagination");return result;
 }
 RectF commentBubble(Fragment f){if(!commentParagraphs.contains(f.paragraphStart)||f.lastLine!=f.layout.getLineCount())return null;ReaderTextLayout.Line line=f.layout.lines.get(f.lastLine-1);float h=bubbleWidth,x=(int)(left+line.right),y=(int)(f.y-f.layout.getLineTop(f.firstLine)+line.top+lineSpace/2+(f.layout.lineHeight-h)/2);return new RectF(x,y,x+bubbleWidth,y+h);}
 int locate(int offset){if(offset==Integer.MAX_VALUE)return pages.size()-1;int result=0;for(int i=0;i<pages.size();i++){if(pages.get(i).start<=offset)result=i;else break;}return result;}
 void draw(Canvas c,int pageIndex,int color){paint.setColor(color);titlePaint.setColor(color);Page page=pages.get(pageIndex);if(page.first){c.save();c.translate(left,top);heading.draw(c);c.restore();}for(Fragment f:page.fragments){float a=f.layout.getLineTop(f.firstLine),b=f.layout.getLineBottom(f.lastLine-1);c.save();c.clipRect(left,f.y,width-left,f.y+b-a);c.translate(left,f.y-a);f.layout.draw(c);c.restore();}}
}
