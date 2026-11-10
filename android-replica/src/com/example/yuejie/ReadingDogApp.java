package com.example.yuejie;
import android.app.Application;
public final class ReadingDogApp extends Application {
 @Override public void onCreate(){super.onCreate();SyncEngine.install(this);}
}
