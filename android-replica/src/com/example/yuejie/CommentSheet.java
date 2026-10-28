package com.example.yuejie;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.ListView;

/** Downward sheet dragging yields to comments until the list reaches its top. */
final class CommentSheet extends LinearLayout {
 final Runnable dismiss;
 final int slop;
 final float threshold;
 ListView list;
 float downX,downY;
 boolean dragging,blocked,settling;

 CommentSheet(Context context,Runnable dismiss){
  super(context);this.dismiss=dismiss;setOrientation(VERTICAL);
  slop=ViewConfiguration.get(context).getScaledTouchSlop();
  threshold=72*getResources().getDisplayMetrics().density;
 }
 boolean listCanScrollUp(){return list!=null&&list.canScrollVertically(-1);}
 @Override public boolean dispatchTouchEvent(MotionEvent event){
  int action=event.getActionMasked();
  if(settling)return true;
  if(action==MotionEvent.ACTION_DOWN){
   animate().cancel();setTranslationY(0);downX=event.getRawX();downY=event.getRawY();dragging=false;blocked=false;
  }else if(action==MotionEvent.ACTION_POINTER_DOWN){blocked=true;if(dragging)settle(false);
  }else if(action==MotionEvent.ACTION_MOVE&&!blocked&&!dragging){
   float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
   if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy))blocked=true;
   if(listCanScrollUp()){downX=event.getRawX();downY=event.getRawY();}
   else if(!blocked&&dy>slop&&dy>Math.abs(dx)){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);}
  }
  return super.dispatchTouchEvent(event);
 }
 @Override public boolean onInterceptTouchEvent(MotionEvent event){return dragging;}
 // Keep observing list movement so a continuous downward pull can transfer at its top.
 @Override public void requestDisallowInterceptTouchEvent(boolean disallow){super.requestDisallowInterceptTouchEvent(false);}
 @Override public boolean onTouchEvent(MotionEvent event){
  if(settling)return true;
  switch(event.getActionMasked()){
   case MotionEvent.ACTION_MOVE:
    if(dragging)setTranslationY(Math.max(0,event.getRawY()-downY-slop));
    break;
   case MotionEvent.ACTION_POINTER_DOWN:
   case MotionEvent.ACTION_CANCEL:
    settle(false);break;
   case MotionEvent.ACTION_UP:
    settle(dragging&&getTranslationY()>=threshold);break;
  }
  return true;
 }
 void settle(boolean close){
  dragging=false;getParent().requestDisallowInterceptTouchEvent(false);
  if(close){settling=true;animate().translationY(getHeight()).setDuration(180).withEndAction(dismiss).start();}
  else animate().translationY(0).setDuration(160).start();
 }
 @Override protected void onDetachedFromWindow(){animate().cancel();super.onDetachedFromWindow();}
}
