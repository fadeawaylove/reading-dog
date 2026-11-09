package com.example.yuejie;
import android.graphics.*;import android.view.*;import java.util.*;
/** One reusable native page. Drawing data stays frozen throughout a gesture. */
final class ReaderPageView extends View {
 final Map<Integer,ReaderImages.State> imageStates=new HashMap<>();
 final ReaderSurface owner;ReaderSurface.Frame frame;Map<Integer,Long> counts=Collections.emptyMap();String clock="",progress="";int paper,ink,muted,accent;boolean comments,night;int battery;boolean charging;int contentDraws;long generation;
 ReaderPageView(ReaderSurface owner){super(owner.getContext());this.owner=owner;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setWillNotDraw(false);}
 boolean matches(ReaderSurface.Frame other){return other!=null&&frame!=null&&frame.id.equals(other.id)&&frame.layout==other.layout&&frame.pageIndex==other.pageIndex&&frame.totalChapters==other.totalChapters;}
 void bind(ReaderSurface.Frame value){boolean changed=!matches(value);frame=value;if(value==null||changed)imageStates.clear();if(changed){generation++;refresh(true);}}
 boolean refresh(boolean force){if(frame==null)return false;ReaderSettings s=owner.settings;Map<Integer,Long> next=s.comments?owner.host.commentCounts(frame.id):Collections.emptyMap();if(next==null)next=Collections.emptyMap();String time=owner.footerClock(),nextProgress=owner.host.bookProgress(frame);boolean changed=force||paper!=s.paper()||ink!=s.ink()||muted!=s.muted()||accent!=s.accent()||comments!=s.comments||night!=s.night||!counts.equals(next)||!clock.equals(time)||!progress.equals(nextProgress);if(!changed)return false;if(force){Map<Integer,ReaderImages.State> previous=new HashMap<>(imageStates);imageStates.clear();for(ReaderPaginator.Fragment part:frame.layout.pages.get(frame.pageIndex).fragments)if(part.image!=null){ReaderImages.State old=previous.get(part.image.offset);ReaderImages.State state=old!=null&&old.bitmap!=null?old:owner.images.snapshot(part.image.url,frame.layout.imageRect(part));imageStates.put(part.image.offset,state);if(state.bitmap==null)owner.images.request(part.image.url,false);}}boolean whole=force||paper!=s.paper()||ink!=s.ink()||muted!=s.muted()||accent!=s.accent()||comments!=s.comments||night!=s.night;boolean bubbles=!counts.equals(next);paper=s.paper();ink=s.ink();muted=s.muted();accent=s.accent();comments=s.comments;night=s.night;counts=new HashMap<>(next);clock=time;progress=nextProgress;battery=owner.host.battery();charging=owner.host.charging();generation++;if(owner.stableDisplay()&&this!=owner.pages[1])return true;if(whole)super.invalidate();else{if(bubbles)for(ReaderPaginator.Fragment part:frame.layout.pages.get(frame.pageIndex).fragments){RectF rect=frame.layout.commentBubble(part);if(rect!=null)super.invalidate((int)rect.left-2,(int)rect.top-2,(int)rect.right+2,(int)rect.bottom+2);}super.invalidate(0,getHeight()-(int)owner.d(35),getWidth(),getHeight());}return true;}
 // Necessary content updates do not unfreeze clock, progress or comment geometry.
 boolean refreshImages(){
  if(frame==null)return false;boolean changed=false;
  for(ReaderPaginator.Fragment part:frame.layout.pages.get(frame.pageIndex).fragments)if(part.image!=null){
   ReaderImages.State old=imageStates.get(part.image.offset);
   if(old!=null&&old.bitmap!=null)continue;
   ReaderImages.State next=owner.images.snapshot(part.image.url,frame.layout.imageRect(part));
   if(old==null||old.bitmap!=next.bitmap||!old.label.equals(next.label)){imageStates.put(part.image.offset,next);changed=true;}
  }
  if(changed){generation++;super.invalidate();}return changed;
 }
 protected void onDraw(Canvas canvas){contentDraws++;if(frame!=null)owner.paintPage(canvas,this);}
}
