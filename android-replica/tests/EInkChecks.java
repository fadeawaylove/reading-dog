package com.example.yuejie;
import java.util.*;

/** Host JVM checks of the production policy and reflection bridge; no screen emulation. */
public final class EInkChecks {
 static int checks;
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
 static class Device implements EInkSupport.Platform,EInkSupport.Driver {
  String board="bengal";int reads,connections;boolean missing,throwCalls;List<String> calls=new ArrayList<>();
  public String property(String key)throws Exception{reads++;if(board==null)throw new Exception("unavailable");return board;}
  public EInkSupport.Driver connect()throws Exception{connections++;if(missing)throw new ClassNotFoundException();return this;}
  void call(String s)throws Exception{calls.add(s);if(throwCalls)throw new Exception("rejected");}
  public void fast()throws Exception{call("DU");}public void normal()throws Exception{call("DEFAULT");}public void clear()throws Exception{call("GC");}
 }
 public static class Constants {public static int UPDATE_MODE_DEFAULT=73,UPDATE_MODE_DU=91;}
 public static class Helper {static List<Integer> values=new ArrayList<>();public static void setSystemRefreshMode(int mode){values.add(mode);}}
 public static class Update {public static int UI_GC_MODE=117;public static void repaintEverything(int mode){Helper.values.add(mode);}}
 public static class Property {public static String get(String key,String fallback){return "bengal";}}
 static Class<?> lookup(String name)throws Exception{
  switch(name){case "android.os.SystemProperties":return Property.class;case "android.onyx.optimization.Constant":return Constants.class;case "android.onyx.optimization.EInkHelper":return Helper.class;case "android.onyx.ViewUpdateHelper":return Update.class;default:throw new ClassNotFoundException(name);}
 }
 public static void main(String[] args)throws Exception{
  Device ordinary=new Device();EInkSupport phone=new EInkSupport("Xiaomi","bengal",ordinary);phone.activityStarted();phone.readerInitialized();phone.activityStopped(false);
  check(!phone.supported()&&ordinary.connections==0&&ordinary.reads==0,"ordinary phone never probes vendor API");
  for(String vendor:new String[]{"ONYX","allwinner","freescale","boyue"}){Device d=new Device();check(new EInkSupport(vendor,"unknown",d).supported(),"local manufacturer "+vendor);}
  for(String board:new String[]{"rk312x","rk3368","msm8953","sdm660","bengal","rk3288","imx7"}){Device d=new Device();d.board=board;check(new EInkSupport("onyx","unknown",d).supported(),"platform "+board);}
  for(String hw:new String[]{"freescale","vendor-freescale-board","rk30board"}){Device d=new Device();d.board=null;check(new EInkSupport("onyx",hw,d).supported()&&d.reads==0,"hardware shortcut "+hw);}
  for(String board:new String[]{null,"unsupported","BENGAL"}){Device d=new Device();d.board=board;EInkSupport s=new EInkSupport("onyx","other",d);s.activityStarted();check(!s.supported()&&d.connections==0,"nonmatching or inaccessible platform");}
  check(!new EInkSupport(null,null,new Device()).supported(),"null identity");
  Device d=new Device();EInkSupport s=new EInkSupport("onyx","other",d);
  s.readerInitialized();check(d.calls.isEmpty(),"no background clear");
  s.activityStarted();s.activityStarted();s.activityStopped(false);check(d.calls.equals(Arrays.asList("DU")),"navigation inside app keeps mode");
  s.readerInitialized();check(d.calls.equals(Arrays.asList("DU","GC")),"successful reader initialization clears");
  s.activityStopped(true);s.activityStarted();check(d.calls.size()==2,"configuration change does not reset mode");
  s.activityStopped(false);check(d.calls.equals(Arrays.asList("DU","GC","DEFAULT")),"last activity restores default");
  s.activityStopped(false);s.readerInitialized();check(d.calls.size()==3,"duplicate stop and background clear ignored");
  s.activityStarted();check(d.calls.get(3).equals("DU")&&d.connections==1,"foreground reuses adapter");
  Device missing=new Device();missing.missing=true;EInkSupport absent=new EInkSupport("onyx","freescale",missing);absent.activityStarted();absent.readerInitialized();absent.activityStopped(false);absent.activityStarted();check(missing.connections==1&&missing.calls.isEmpty(),"missing adapter cached without retry");
  Device failing=new Device();failing.throwCalls=true;EInkSupport failed=new EInkSupport("onyx","freescale",failing);failed.activityStarted();failed.readerInitialized();failed.activityStopped(false);check(failing.calls.equals(Arrays.asList("DU","GC","DEFAULT")),"call failure does not prevent later restore");
  EInkSupport bridge=new EInkSupport("onyx","unknown",EInkSupport.reflection(EInkChecks::lookup));bridge.activityStarted();bridge.readerInitialized();bridge.activityStopped(false);check(Helper.values.equals(Arrays.asList(91,117,73)),"reflection uses platform constants and static methods");
  Helper.values.clear();EInkSupport incomplete=new EInkSupport("onyx","freescale",EInkSupport.reflection(name->name.endsWith("ViewUpdateHelper")?String.class:lookup(name)));incomplete.activityStarted();incomplete.activityStopped(false);check(Helper.values.isEmpty(),"partial API not activated");
  System.out.println("PASS "+checks+" EInk checks (host JVM, not physical screen acceptance)");
 }
}
