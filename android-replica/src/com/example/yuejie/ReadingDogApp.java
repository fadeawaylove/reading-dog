package com.example.yuejie;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

public final class ReadingDogApp extends Application {
 private EInkSupport eink;
 private String lastEinkStatus="";
 private void reportEink(){String status=eink.status();if(!status.equals(lastEinkStatus)){lastEinkStatus=status;android.util.Log.i("ReadingDogEink",status);}}
 @Override public void onCreate(){
  super.onCreate();
  SyncEngine.install(this);
  eink=new EInkSupport(Build.MANUFACTURER,Build.HARDWARE,EInkSupport.reflection(Class::forName));
  android.util.Log.i("ReadingDogEink","stable display="+EInkSupport.stableDisplay(Build.MANUFACTURER,Build.BRAND,Build.MODEL,Build.DEVICE,Build.PRODUCT));
  registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
   public void onActivityStarted(Activity activity){eink.activityStarted();reportEink();}
   public void onActivityStopped(Activity activity){eink.activityStopped(activity.isChangingConfigurations());reportEink();}
   public void onActivityCreated(Activity activity,Bundle state){}
   public void onActivityResumed(Activity activity){}
   public void onActivityPaused(Activity activity){}
   public void onActivitySaveInstanceState(Activity activity,Bundle state){}
   public void onActivityDestroyed(Activity activity){}
  });
 }
 void readerInitialized(){eink.readerInitialized();reportEink();}
}
