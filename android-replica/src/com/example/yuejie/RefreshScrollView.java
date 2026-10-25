package com.example.yuejie;
import android.content.*;import android.view.*;import android.widget.*;
/** Pull recognition only intercepts downward motion at the very top of the local home. */
final class RefreshScrollView extends ScrollView {
 interface Host{boolean enabled();void distance(float dp);void released(boolean refresh);}
 final Host host;float startX,startY;boolean pulling,tracking;final float density;
 RefreshScrollView(Context c,Host host){super(c);this.host=host;density=getResources().getDisplayMetrics().density;}
 public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==0){startX=e.getX();startY=e.getY();tracking=getScrollY()==0&&host.enabled();pulling=false;}if(tracking&&e.getActionMasked()==2&&e.getY()-startY>14*density&&Math.abs(e.getY()-startY)>Math.abs(e.getX()-startX)*1.3f){pulling=true;host.distance((e.getY()-startY)/density);return true;}if(e.getActionMasked()==1||e.getActionMasked()==3)tracking=false;return super.onInterceptTouchEvent(e);}
 public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==0){startX=e.getX();startY=e.getY();tracking=getScrollY()==0&&host.enabled();pulling=false;}if(tracking&&!pulling&&e.getActionMasked()==2&&e.getY()-startY>14*density&&Math.abs(e.getY()-startY)>Math.abs(e.getX()-startX)*1.3f)pulling=true;if(pulling){float distance=Math.max(0,(e.getY()-startY)/density);if(e.getActionMasked()==2)host.distance(distance);if(e.getActionMasked()==1||e.getActionMasked()==3){host.released(e.getActionMasked()==1&&distance>=72);pulling=tracking=false;}return true;}return super.onTouchEvent(e);}
}
