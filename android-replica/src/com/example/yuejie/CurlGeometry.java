package com.example.yuejie;
import android.graphics.*;
/** Quadratic fold geometry reconstructed from the reference's FramePager drawing path. */
final class CurlGeometry {
 final PointF start1=new PointF(),control1=new PointF(),end1=new PointF(),vertex1=new PointF();
 final PointF start2=new PointF(),control2=new PointF(),end2=new PointF(),vertex2=new PointF();
 final Path fold=new Path(),back=new Path(),under=new Path();float x,y,w,h,cornerX,cornerY;boolean diagonal;
 static float safe(float n){return Math.abs(n)<.1f?(n<0?-.1f:.1f):n;}
 static void intersection(PointF out,PointF a,PointF b,PointF c,PointF d){float ax=b.x-a.x,ay=b.y-a.y,bx=d.x-c.x,by=d.y-c.y;float t=((c.x-a.x)*by-(c.y-a.y)*bx)/safe(ax*by-ay*bx);out.set(a.x+t*ax,a.y+t*ay);}
 void controls(){float mx=(x+cornerX)/2,my=(y+cornerY)/2;control1.set(mx-(cornerY-my)*(cornerY-my)/safe(cornerX-mx),cornerY);control2.set(cornerX,my-(cornerX-mx)*(cornerX-mx)/safe(cornerY-my));start1.set(control1.x-(cornerX-control1.x)/2,cornerY);start2.set(cornerX,control2.y-(cornerY-control2.y)/2);}
 void update(float width,float height,float fingerX,float fingerY,boolean top){w=width;h=height;cornerX=w;cornerY=top?0:h;x=fingerX;y=fingerY;if(Math.abs(cornerX-x)<.1f)x=cornerX-.1f;if(Math.abs(cornerY-y)<.1f)y=cornerY+(top?.1f:-.1f);controls();
  if(x>0&&x<w&&(start1.x<0||start1.x>w)){float start=start1.x<0?w-start1.x:start1.x;float dx=Math.abs(cornerX-x);float newDx=w*dx/safe(start);float newDy=newDx*Math.abs(cornerY-y)/safe(dx);x=cornerX-newDx;y=cornerY+(top?newDy:-newDy);controls();}
  intersection(end1,new PointF(x,y),control1,start1,start2);intersection(end2,new PointF(x,y),control2,start1,start2);
  vertex1.set((start1.x+2*control1.x+end1.x)/4,(start1.y+2*control1.y+end1.y)/4);
  vertex2.set((start2.x+2*control2.x+end2.x)/4,(start2.y+2*control2.y+end2.y)/4);
  fold.reset();fold.moveTo(start1.x,start1.y);fold.quadTo(control1.x,control1.y,end1.x,end1.y);fold.lineTo(x,y);fold.lineTo(end2.x,end2.y);fold.quadTo(control2.x,control2.y,start2.x,start2.y);fold.lineTo(cornerX,cornerY);fold.close();
  under.reset();under.moveTo(start1.x,start1.y);under.lineTo(vertex1.x,vertex1.y);under.lineTo(vertex2.x,vertex2.y);under.lineTo(start2.x,start2.y);under.lineTo(cornerX,cornerY);under.close();
  back.reset();back.moveTo(vertex2.x,vertex2.y);back.lineTo(vertex1.x,vertex1.y);back.lineTo(end1.x,end1.y);back.lineTo(x,y);back.lineTo(end2.x,end2.y);back.close();diagonal=!top;
 }
 float shadowWidth(){return Math.min((float)Math.hypot(x-cornerX,y-cornerY),w)/3f;}
}
