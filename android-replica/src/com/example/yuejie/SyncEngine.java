package com.example.yuejie;

import android.app.*;
import android.content.*;
import android.database.sqlite.*;
import android.net.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground lifecycle scheduler. No persistent service, Activity references or lost offline writes. */
final class SyncEngine {
  static volatile String activeBook="";
  static SyncEngine instance;
  static final Set<Runnable> listeners=new HashSet<>();
  static synchronized SyncEngine get(Context c){if(instance==null)instance=new SyncEngine(c.getApplicationContext());return instance;}
  static void schedule(Context c,long delay){get(c).schedule(delay);}
  final Context context;final Handler main=new Handler(Looper.getMainLooper());final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
  ScheduledFuture<?> queued;volatile boolean running,rerun;boolean foreground;
  volatile String status="尚未同步";volatile long lastSuccess;
  interface Remote{JSONObject request(String path,JSONObject body)throws Exception;}
  volatile Remote remote;
  SyncEngine(Context c){context=c;remote=(path,body)->BackendClient.request(context,path,body);lastSuccess=c.getSharedPreferences("sync_clock",0).getLong("last_success",0);}
  synchronized void schedule(long delay){if(running){rerun=true;return;}if(queued!=null&&!queued.isDone()){if(queued.getDelay(TimeUnit.MILLISECONDS)<=delay)return;queued.cancel(false);}queued=worker.schedule(this::run,delay,TimeUnit.MILLISECONDS);}
  void notifyChanged(){main.post(()->{for(Runnable listener:new ArrayList<>(listeners))listener.run();});}
  void run(){
    synchronized(this){running=true;rerun=false;}
    status="正在同步…";notifyChanged();
    try{
      LocalLibrary library=new LocalLibrary(context);SQLiteDatabase db=library.getWritableDatabase();
      long start=System.currentTimeMillis(),elapsedStart=SystemClock.elapsedRealtime();JSONObject time=remote.request("/api/v1/time",null);long wallMid=start+(System.currentTimeMillis()-start)/2,elapsedMid=elapsedStart+(SystemClock.elapsedRealtime()-elapsedStart)/2;long offset=time.getLong("server_time")-wallMid;
      calibrate(context,db,time.getLong("server_time"),wallMid,elapsedMid);
      android.content.SharedPreferences clock=context.getSharedPreferences("sync_clock",0);
      LibrarySync.seed(context,db,offset);
      for(int round=0;round<100;round++){
        JSONArray operations=LibrarySync.pending(db);JSONObject request=new JSONObject().put("cursor",Long.parseLong(LibrarySync.meta(db,"cursor","0"))).put("operations",operations);
        JSONObject reply=remote.request("/api/v1/sync",request);LibrarySync.merge(db,reply);
        if(!reply.optBoolean("more")&&LibrarySync.pending(db).length()==0)break;
      }
      lastSuccess=System.currentTimeMillis();clock.edit().putLong("last_success",lastSuccess).apply();status="已同步";
    }catch(Exception e){status="同步未完成 · "+(e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"稍后重试");}
    finally{synchronized(this){running=false;if(rerun||foreground)schedule(rerun?5000:30000);}notifyChanged();}
  }
  static void calibrate(Context context,SQLiteDatabase db,long serverTime,long wallMid,long elapsedMid)throws Exception{
    SyncClock.calibrate(context,db,serverTime,wallMid,elapsedMid);
  }
  static void install(ReadingDogApp app){
    SyncEngine engine=get(app);
    app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){int started;
      public void onActivityStarted(Activity a){if(++started==1){engine.foreground=true;engine.schedule(0);}}
      public void onActivityStopped(Activity a){if(--started==0){engine.foreground=false;engine.schedule(0);}}
      public void onActivityCreated(Activity a,Bundle b){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
    });
    try{ConnectivityManager cm=(ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network network){engine.schedule(0);}});}catch(Exception ignored){}
  }
}
