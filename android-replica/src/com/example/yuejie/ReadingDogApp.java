package com.example.yuejie;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

public final class ReadingDogApp extends Application {
 private EInkSupport eink;
 @Override public void onCreate(){
  super.onCreate();
  eink=new EInkSupport(Build.MANUFACTURER,Build.HARDWARE,EInkSupport.reflection(Class::forName));
  registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
   public void onActivityStarted(Activity activity){eink.activityStarted();}
   public void onActivityStopped(Activity activity){eink.activityStopped(activity.isChangingConfigurations());}
   public void onActivityCreated(Activity activity,Bundle state){}
   public void onActivityResumed(Activity activity){}
   public void onActivityPaused(Activity activity){}
   public void onActivitySaveInstanceState(Activity activity,Bundle state){}
   public void onActivityDestroyed(Activity activity){}
  });
 }
 void readerInitialized(){eink.readerInitialized();}
}
