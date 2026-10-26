package com.example.yuejie;
import android.graphics.*;import android.graphics.drawable.GradientDrawable;
/** Mode-specific drawing shares native page containers and a Scroller-based controller. */
abstract class PageTurnRenderer {
 interface Pages {void draw(Canvas c,ReaderSurface.Frame f);}
 void prepare(ReaderSurface surface,ReaderSurface.Frame current,ReaderSurface.Frame target){}
 abstract void draw(Canvas c,ReaderSurface s,Pages pages);void warm(ReaderSurface s){}void release(){}
 static PageTurnRenderer create(PageTurnMode mode){switch(mode){case SIMULATION:return new Curl();case SLIDE:return new Slide();case NONE:return new None();default:return new Cover();}}
 static void translated(Canvas c,Pages pages,ReaderSurface.Frame f,float x){c.save();c.translate(x,0);pages.draw(c,f);c.restore();}
 static int shadowColor(ReaderSurface s){return s.settings.night?0x66000000:s.settings.theme==1?0x50b5ab9d:s.settings.theme==2?0x50a9aaa8:0x50999999;}
 static final class None extends PageTurnRenderer {void draw(Canvas c,ReaderSurface s,Pages pages){pages.draw(c,s.frame);}}
 static final class Slide extends PageTurnRenderer {void draw(Canvas c,ReaderSurface s,Pages pages){float w=s.getWidth();translated(c,pages,s.frame,s.drag);translated(c,pages,s.target,s.direction>0?s.drag+w:s.drag-w);}}
 static final class Cover extends PageTurnRenderer {
  final GradientDrawable shadow=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x50999999,0});
  void warm(ReaderSurface s){shadow.setColors(new int[]{shadowColor(s),0});}
  void draw(Canvas c,ReaderSurface s,Pages pages){float w=s.getWidth(),edge;if(s.direction>0){pages.draw(c,s.target);translated(c,pages,s.frame,s.drag);edge=w+s.drag;}else{pages.draw(c,s.frame);translated(c,pages,s.target,-w+s.drag);edge=s.drag;}shadow.setBounds((int)edge,0,(int)(edge+s.d(12)),s.getHeight());shadow.draw(c);}
 }
 static final class Curl extends PageTurnRenderer {
  Bitmap front,next;final Paint bitmapPaint=new Paint(3),backPaint=new Paint(3);final Matrix reflection=new Matrix();final float[] matrix=new float[9];final Path reflected=new Path(),crease=new Path(),mask=new Path();final CurlGeometry geometry=new CurlGeometry();int allocations;int shadeColor;
  final ReaderSurface.Frame[] frames=new ReaderSurface.Frame[3];final Bitmap[] images=new Bitmap[3];final long[] generations=new long[3];
  final GradientDrawable creaseShadow=new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,new int[]{0,0x33000000});
  final GradientDrawable backShadow=new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,new int[]{0,0x33000000});
  final GradientDrawable horizontalShadow=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0,0x33000000});
  final GradientDrawable verticalShadow=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0,0x33000000});
  boolean same(ReaderSurface.Frame a,ReaderSurface.Frame b){return a!=null&&b!=null&&a.id.equals(b.id)&&a.layout==b.layout&&a.pageIndex==b.pageIndex&&a.totalChapters==b.totalChapters;}
  Bitmap find(ReaderSurface.Frame f){for(int i=0;i<3;i++)if(same(f,frames[i]))return images[i];return null;}
  void warm(ReaderSurface s){if(s.frame==null||s.getWidth()<1||s.getHeight()<1||s.animating()||s.dragging)return;
   int color=shadowColor(s);if(color!=shadeColor){shadeColor=color;for(GradientDrawable g:new GradientDrawable[]{creaseShadow,backShadow,horizontalShadow,verticalShadow})g.setColors(new int[]{0,color});}
   ReaderSurface.Frame[] wanted={s.pages[1].frame,s.pages[0].frame,s.pages[2].frame};Bitmap[] keep=new Bitmap[3];long[] stamps=new long[3];
   for(int i=0;i<3;i++){ReaderPageView page=s.view(wanted[i]);if(page==null)continue;stamps[i]=page.generation;for(int j=0;j<3;j++)if(same(wanted[i],frames[j])&&generations[j]==stamps[i])keep[i]=images[j];}
   for(Bitmap old:images){boolean used=false;for(Bitmap b:keep)if(old==b)used=true;if(old!=null&&!used)old.recycle();}
   for(int i=0;i<3;i++){frames[i]=wanted[i];images[i]=keep[i];generations[i]=stamps[i];if(wanted[i]!=null&&images[i]==null){images[i]=Bitmap.createBitmap(s.getWidth(),s.getHeight(),Bitmap.Config.ARGB_8888);allocations++;s.drawFrame(new Canvas(images[i]),wanted[i]);}}front=next=null;
  }
  void prepare(ReaderSurface s,ReaderSurface.Frame current,ReaderSurface.Frame target){front=find(current);next=find(target);}
  void release(){for(int i=0;i<3;i++){if(images[i]!=null)images[i].recycle();images[i]=null;frames[i]=null;}front=next=null;}
  void draw(Canvas canvas,ReaderSurface s,Pages pages){if(front==null||next==null){pages.draw(canvas,s.frame);return;}float w=s.getWidth(),h=s.getHeight(),p=Math.min(1,Math.abs(s.drag)/w);if(p>=.9999f){pages.draw(canvas,s.target);return;}if(p<=.0001f){pages.draw(canvas,s.frame);return;}
   boolean forward=s.direction>0,top=s.downY<=h*.5f;float fingerX=s.lastX;
   if(s.settling){float span=s.settleEnd-s.settleStart;float t=span==0?1:(s.drag-s.settleStart)/span;float end=forward?(s.pendingCommit?-w:w):(s.pendingCommit?w:-w);fingerX=s.settleFingerX+(end-s.settleFingerX)*t;}
   // Keep the touch height in the middle band and on backward turns too.
   // Snapping it to the corner flattens the curl into a cover-like vertical strip.
   float fingerY=Math.max(1,Math.min(h-1,s.lastY));
   geometry.update(w,h,fingerX,fingerY,top);CurlGeometry g=geometry;Bitmap turning=forward?front:next,under=forward?next:front;
   canvas.drawBitmap(under,0,0,bitmapPaint);canvas.save();if(android.os.Build.VERSION.SDK_INT>=26)canvas.clipOutPath(g.fold);else canvas.clipPath(g.fold,Region.Op.DIFFERENCE);canvas.drawBitmap(turning,0,0,bitmapPaint);canvas.restore();
   float length=(float)Math.hypot(w,h);float angle=(float)Math.toDegrees(Math.atan2(g.control1.x-w,g.control2.y-g.cornerY));
   canvas.save();canvas.clipPath(g.fold);canvas.clipPath(g.under);canvas.rotate(angle,g.start1.x,g.start1.y);float size=g.shadowWidth();creaseShadow.setOrientation(top?GradientDrawable.Orientation.RIGHT_LEFT:GradientDrawable.Orientation.LEFT_RIGHT);creaseShadow.setBounds((int)(g.start1.x-(top?0:size)),(int)g.start1.y,(int)(g.start1.x+(top?size:0)),(int)(g.start1.y+length));creaseShadow.draw(canvas);canvas.restore();
   float dx=w-g.control1.x,dy=g.control2.y-g.cornerY,len=(float)Math.hypot(dx,dy),nx=dy/Math.max(.1f,len),ny=-dx/Math.max(.1f,len);
   matrix[0]=1-2*nx*nx;matrix[1]=-2*nx*ny;matrix[2]=2*(g.control1.x*nx+g.control1.y*ny)*nx;matrix[3]=matrix[1];matrix[4]=1-2*ny*ny;matrix[5]=2*(g.control1.x*nx+g.control1.y*ny)*ny;matrix[6]=matrix[7]=0;matrix[8]=1;reflection.setValues(matrix);
   reflected.set(g.back);crease.reset();crease.moveTo(g.start1.x,g.start1.y);crease.quadTo(g.control1.x,g.control1.y,g.end1.x,g.end1.y);
   canvas.save();canvas.clipPath(g.fold);canvas.clipPath(g.back);canvas.drawBitmap(turning,reflection,bitmapPaint);backPaint.setColor((s.settings.paper()&0xffffff)|0xc8000000);canvas.drawPaint(backPaint);canvas.rotate(angle,g.start1.x,g.start1.y);float backWidth=Math.min(Math.abs((g.start1.x-g.control1.x)/2),Math.abs((g.start2.y-g.control2.y)/2));backShadow.setOrientation(top?GradientDrawable.Orientation.LEFT_RIGHT:GradientDrawable.Orientation.RIGHT_LEFT);backShadow.setBounds((int)(g.start1.x-(top?1:backWidth+1)),(int)g.start1.y,(int)(g.start1.x+(top?backWidth+1:1)),(int)(g.start1.y+length));backShadow.draw(canvas);canvas.restore();
   // Reference front shadows: clipped triangles follow each quadratic control.
   float frontWidth=Math.min(16,Math.min((float)Math.hypot(g.x-g.end1.x,g.y-g.end1.y),(float)Math.hypot(g.x-g.end2.x,g.y-g.end2.y)));
   double slope=top?Math.atan2(g.control1.y-g.y,g.x-g.control1.x):Math.atan2(g.y-g.control1.y,g.x-g.control1.x);double theta=Math.PI/4-slope;float tipX=g.x+(float)(Math.cos(theta)*frontWidth*1.414),tipY=g.y+(top?1:-1)*(float)(Math.sin(theta)*frontWidth*1.414);
   mask.reset();mask.moveTo(tipX,tipY);mask.lineTo(g.x,g.y);mask.lineTo(g.control1.x,g.control1.y);mask.lineTo(g.start1.x,g.start1.y);mask.close();
   canvas.save();if(android.os.Build.VERSION.SDK_INT>=26)canvas.clipOutPath(g.fold);else canvas.clipPath(g.fold,Region.Op.DIFFERENCE);canvas.clipPath(mask);canvas.rotate((float)Math.toDegrees(Math.atan2(g.x-g.control1.x,g.control1.y-g.y)),g.control1.x,g.control1.y);horizontalShadow.setOrientation(top?GradientDrawable.Orientation.RIGHT_LEFT:GradientDrawable.Orientation.LEFT_RIGHT);horizontalShadow.setBounds((int)(g.control1.x-(top?0:frontWidth)),(int)(g.control1.y-length),(int)(g.control1.x+(top?frontWidth:1)),(int)g.control1.y);horizontalShadow.draw(canvas);canvas.restore();
   mask.reset();mask.moveTo(tipX,tipY);mask.lineTo(g.x,g.y);mask.lineTo(g.control2.x,g.control2.y);mask.lineTo(g.start2.x,g.start2.y);mask.close();
   canvas.save();if(android.os.Build.VERSION.SDK_INT>=26)canvas.clipOutPath(g.fold);else canvas.clipPath(g.fold,Region.Op.DIFFERENCE);canvas.clipPath(mask);canvas.rotate((float)Math.toDegrees(Math.atan2(g.control2.y-g.y,g.control2.x-g.x)),g.control2.x,g.control2.y);verticalShadow.setOrientation(top?GradientDrawable.Orientation.BOTTOM_TOP:GradientDrawable.Orientation.TOP_BOTTOM);float hy=g.control2.y<0?g.control2.y-h:g.control2.y;float reach=(float)Math.hypot(g.control2.x,hy);float x1=reach>length?g.control2.x-frontWidth-reach:g.control2.x-length,x2=reach>length?g.control2.x+length-reach:g.control2.x;verticalShadow.setBounds((int)x1,(int)(g.control2.y-(top?0:frontWidth)),(int)x2,(int)(g.control2.y+(top?frontWidth:1)));verticalShadow.draw(canvas);canvas.restore();
  }
 }
}
