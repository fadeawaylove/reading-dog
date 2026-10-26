package com.example.yuejie;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

/** Local-only policy, using the platform contract recovered from Tomato 7.3.9.32. */
final class EInkSupport {
 interface Platform {String property(String key)throws Exception; Driver connect()throws Exception;}
 interface Driver {void fast()throws Exception; void normal()throws Exception; void clear()throws Exception;}
 interface Classes {Class<?> find(String name)throws Exception;}
 private final boolean supported;
 private final Platform platform;
 private boolean connected, foreground;
 private Driver driver;
 private int started;

 EInkSupport(String manufacturer,String hardware,Platform platform){
  this.platform=platform;
  boolean match=manufacturer!=null&&Arrays.asList("onyx","allwinner","freescale","boyue").contains(manufacturer.toLowerCase(Locale.ROOT));
  if(match){
   boolean hardwareMatch=hardware!=null&&(hardware.contains("freescale")||hardware.equals("rk30board"));
   if(!hardwareMatch)try{hardwareMatch=Arrays.asList("rk312x","rk3368","msm8953","sdm660","bengal","rk3288","imx7").contains(platform.property("ro.board.platform"));}catch(Exception ignored){}
   match=hardwareMatch;
  }
  supported=match;
 }
 boolean supported(){return supported;}
 private Driver driver(){
  if(!supported)return null;
  if(!connected){connected=true;try{driver=platform.connect();}catch(Exception ignored){}}
  return driver;
 }
 void activityStarted(){started++;if(!foreground){foreground=true;apply(0);}}
 void activityStopped(boolean changingConfiguration){
  if(started==0)return;
  started--;
  if(started==0&&!changingConfiguration&&foreground){foreground=false;apply(1);}
 }
 void readerInitialized(){if(foreground)apply(2);}
 private void apply(int operation){
  Driver d=driver();if(d==null)return;
  try{if(operation==0)d.fast();else if(operation==1)d.normal();else d.clear();}catch(Exception ignored){}
 }
 static Platform reflection(Classes classes){return new Platform(){
  public String property(String key)throws Exception{
   Method get=classes.find("android.os.SystemProperties").getDeclaredMethod("get",String.class,String.class);
   get.setAccessible(true);return (String)get.invoke(null,key,"");
  }
  public Driver connect()throws Exception{
   Class<?> helper=classes.find("android.onyx.optimization.EInkHelper"),constants=classes.find("android.onyx.optimization.Constant"),updates=classes.find("android.onyx.ViewUpdateHelper");
   Method mode=helper.getDeclaredMethod("setSystemRefreshMode",int.class),clear=updates.getDeclaredMethod("repaintEverything",int.class);
   mode.setAccessible(true);clear.setAccessible(true);
   int normal=constant(constants,"UPDATE_MODE_DEFAULT"),fast=constant(constants,"UPDATE_MODE_DU"),gc=constant(updates,"UI_GC_MODE");
   return new Driver(){public void fast()throws Exception{mode.invoke(null,fast);}public void normal()throws Exception{mode.invoke(null,normal);}public void clear()throws Exception{clear.invoke(null,gc);}};
  }
 };}
 private static int constant(Class<?> type,String name)throws Exception{java.lang.reflect.Field field=type.getDeclaredField(name);field.setAccessible(true);return field.getInt(null);}
}
