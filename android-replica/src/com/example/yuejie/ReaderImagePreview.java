package com.example.yuejie;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** A modal viewer owns its gestures, so zooming and panning cannot turn a page. */
final class ReaderImagePreview extends Dialog {
 final ReaderImages images;final ZoomView picture;final String url;
 ReaderImages.State state;
 ReaderImagePreview(Context context,String url){
  super(context,android.R.style.Theme_Material_NoActionBar_Fullscreen);this.url=url;
  picture=new ZoomView(context);images=new ReaderImages(context,this::imageReady);state=images.state(url);
  FrameLayout root=new FrameLayout(context);root.setBackgroundColor(0xff111511);root.addView(picture,new FrameLayout.LayoutParams(-1,-1));
  TextView close=PaperUi.text(context,"关闭图片",14,Color.WHITE);close.setGravity(Gravity.CENTER);close.setContentDescription("关闭正文图片");FrameLayout.LayoutParams button=new FrameLayout.LayoutParams(PaperUi.dp(context,110),PaperUi.dp(context,56),Gravity.TOP|Gravity.RIGHT);button.topMargin=PaperUi.dp(context,24);root.addView(close,button);close.setOnClickListener(v->dismiss());setContentView(root);
  images.request(url,true);
 }
 void imageReady(){state=images.state(url);picture.invalidate();}
 public void dismiss(){if(images!=null)images.close();super.dismiss();}
 final class ZoomView extends View {
  float zoom=1,dx,dy,lastX,lastY;boolean moved,multi;final ScaleGestureDetector scaler;
  ZoomView(Context c){super(c);setContentDescription("正文图片，双指缩放，拖动查看，加载失败时点击重试");scaler=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector detector){float old=zoom;zoom=Math.max(1,Math.min(8,zoom*detector.getScaleFactor()));float cx=detector.getFocusX()-getWidth()/2f,cy=detector.getFocusY()-getHeight()/2f;dx=cx-(cx-dx)*zoom/old;dy=cy-(cy-dy)*zoom/old;clamp();invalidate();return true;}});}
  void clamp(){if(state.bitmap==null){dx=dy=0;return;}Bitmap b=state.bitmap;float fit=Math.min((float)getWidth()/b.getWidth(),(float)getHeight()/b.getHeight());float mx=Math.max(0,(b.getWidth()*fit*zoom-getWidth())/2),my=Math.max(0,(b.getHeight()*fit*zoom-getHeight())/2);dx=Math.max(-mx,Math.min(mx,dx));dy=Math.max(-my,Math.min(my,dy));}
  protected void onDraw(Canvas canvas){clamp();canvas.save();canvas.translate(getWidth()/2f+dx,getHeight()/2f+dy);canvas.scale(zoom,zoom);canvas.translate(-getWidth()/2f,-getHeight()/2f);ReaderImages.draw(canvas,new RectF(0,0,getWidth(),getHeight()),state,Color.WHITE);canvas.restore();}
  public boolean onTouchEvent(android.view.MotionEvent e){scaler.onTouchEvent(e);int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();moved=false;multi=false;}else if(action==MotionEvent.ACTION_POINTER_DOWN){multi=true;moved=true;}else if(action==MotionEvent.ACTION_POINTER_UP){int next=e.getActionIndex()==0?1:0;lastX=e.getX(next);lastY=e.getY(next);}else if(action==MotionEvent.ACTION_MOVE){float x=e.getX(),y=e.getY();if(!scaler.isInProgress()&&e.getPointerCount()==1){if(Math.abs(x-lastX)+Math.abs(y-lastY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;dx+=x-lastX;dy+=y-lastY;clamp();invalidate();}lastX=x;lastY=y;}else if(action==MotionEvent.ACTION_UP&&!moved&&!multi){performClick();}return true;}
  public boolean performClick(){super.performClick();images.request(url,true);state=images.state(url);invalidate();return true;}
 }
}
