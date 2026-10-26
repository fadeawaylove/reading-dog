package com.example.yuejie;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

/** Separate real-device instrumentation. Fixtures never enter the shipping APK. */
public class UpdateChecks extends Instrumentation {
    Bundle options;JSONArray results=new JSONArray();File output;AboutActivity about;MainActivity home;
    interface Checked {void run()throws Exception;}
    interface Condition {boolean ok();}
    public void onCreate(Bundle args){super.onCreate(args);options=args;start();}
    void ok(boolean condition,String label)throws Exception {if(!condition)throw new AssertionError(label);results.put(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
    void rejects(Checked code,String label)throws Exception {boolean rejected=false;try{code.run();}catch(Exception e){rejected=true;}ok(rejected,label);}
    void ui(Runnable task){runOnMainSync(task);waitForIdleSync();}
    void waitUntil(Condition condition,String label)throws Exception {long end=SystemClock.uptimeMillis()+60000;while(SystemClock.uptimeMillis()<end){boolean[] result={false};runOnMainSync(()->result[0]=condition.ok());if(result[0])return;Thread.sleep(100);}throw new AssertionError("Timeout "+label);}
    void shot(String name)throws Exception {waitForIdleSync();SystemClock.sleep(400);Bitmap b=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(output,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    byte[] asset(String name)throws Exception {try(InputStream in=getContext().getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
    String hash(byte[] data)throws Exception {StringBuilder b=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(data))b.append(String.format(Locale.ROOT,"%02x",value&255));return b.toString();}
    JSONObject fixture(byte[] bytes)throws Exception {String future=new String(asset("future-version.txt"),"UTF-8").trim();return new JSONObject().put("tag_name","v"+future).put("draft",false).put("prerelease",false).put("published_at","2026-10-26T00:00:00Z").put("body","测试夹具 · 不是真实发布\n\n- 新增检查更新\n- 验证下载、校验与安装入口\n\n"+String.join("\n",Collections.nCopies(12,"这是一条较长的更新说明，用于检查滚动区域和按钮可见性。"))).put("assets",new JSONArray().put(new JSONObject().put("name","reading-dog-"+future+".apk").put("size",bytes.length).put("digest","sha256:"+hash(bytes)).put("browser_download_url",UpdateRelease.PROJECT+"/releases/download/v"+future+"/reading-dog-"+future+".apk")));}
    static class FixtureUpdater extends AppUpdater {
        byte[] payload;String metadata;boolean networkError,slow;
        FixtureUpdater(Context c,byte[] bytes,String json){super(c);payload=bytes;metadata=json;}
        @Override String readText(String url,int limit,int token)throws Exception {if(networkError)throw new UnknownHostException();return metadata;}
        @Override HttpURLConnection open(String address,int token)throws Exception {if(networkError)throw new UnknownHostException();return new HttpURLConnection(new URL(address)){
            public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
            public InputStream getInputStream(){return new ByteArrayInputStream(payload){@Override public synchronized int read(byte[] b,int off,int len){if(slow)SystemClock.sleep(100);return super.read(b,off,Math.min(4096,len));}};}
        };}
    }
    View find(View root,String description){if(description.equals(String.valueOf(root.getContentDescription())))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),description);if(v!=null)return v;}return null;}
    public void onStart(){Throwable failure=null;AppUpdater live=null;FixtureUpdater fake=null;
        try{
            output=new File(getTargetContext().getExternalFilesDir(null),"update-checks");output.mkdirs();
            ok(UpdateRelease.compare("0.10","0.9")>0,"numeric version ordering");ok(UpdateRelease.compare("v0.13.0","0.13")==0,"equivalent versions");ok(UpdateRelease.compare("0.12","0.13")<0,"older version ordering");rejects(()->UpdateRelease.compare("0.14-beta","0.13"),"reject prerelease version syntax");
            byte[] nextApk=asset("future.apk");JSONObject json=fixture(nextApk);UpdateRelease target=new UpdateRelease(json);
            rejects(()->new UpdateRelease(new JSONObject(json.toString()).put("prerelease",true)),"reject prerelease metadata");rejects(()->new UpdateRelease(new JSONObject(json.toString()).put("draft",true)),"reject draft metadata");rejects(()->new UpdateRelease(new JSONObject(json.toString()).put("assets",new JSONArray())),"reject missing APK");
            rejects(()->UpdateRelease.requireAssetUrl("https://github.com.evil.example/fadeawaylove/reading-dog/releases/download/x/a.apk"),"reject external asset host");rejects(()->UpdateRelease.requireAssetUrl("http://github.com/fadeawaylove/reading-dog/releases/download/x/a.apk"),"reject plaintext asset URL");
            ok(target.checksum(hash(nextApk)+"  "+target.name+"\n").equals(hash(nextApk)),"checksum file matches exact asset");rejects(()->target.checksum(hash(nextApk)+"  unrelated.apk"),"reject wrong checksum filename");
            live=AppUpdater.get(getTargetContext());final AppUpdater real=live;
            String metadata=real.readText(UpdateRelease.API,1024*1024,real.generation);UpdateRelease actual=new UpdateRelease(new JSONObject(metadata));ok(UpdateRelease.compare(actual.version,real.currentVersion)<=0,"live release is not newer than build under test");
            if(!options.getBoolean("skipLiveDownload",false)&&!"true".equals(options.getString("skipLiveDownload"))){
            HttpURLConnection download=real.open(actual.url,real.generation);byte[] published;
            try(InputStream input=download.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1)out.write(bytes,0,n);published=out.toByteArray();}finally{download.disconnect();}
            ok(published.length==actual.size&&hash(published).equals(real.expectedDigest(actual,real.generation)),"live release APK downloaded on phone and checksum matched");
            File old=new File(output,"old.apk");try(OutputStream out=new FileOutputStream(old)){out.write(published);}rejects(()->real.verify(old,actual,hash(published)),"reject same or older APK installation");old.delete();
            }else{Bundle skipped=new Bundle();skipped.putString("stream","SKIP live APK download (separate integration check)\n");sendStatus(0,skipped);}
            ActivityMonitor homeMonitor=addMonitor(MainActivity.class.getName(),null,false);
            try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("am start -W -n com.example.yuejie/.MainActivity");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}
            home=(MainActivity)homeMonitor.waitForActivityWithTimeout(10000);removeMonitor(homeMonitor);ok(home!=null,"home launched");waitForIdleSync();shot("01-home");
            ActivityMonitor monitor=addMonitor(AboutActivity.class.getName(),null,false);ui(()->find(home.getWindow().getDecorView(),"关于阅读狗").performClick());about=(AboutActivity)monitor.waitForActivityWithTimeout(10000);removeMonitor(monitor);ok(about!=null,"home entry opens About");shot("02-about");
            ui(()->about.action.performClick());waitUntil(()->real.state!=AppUpdater.State.CHECKING,"live check");ok(real.state==AppUpdater.State.LATEST,"installed version reports latest from live API");shot("03-latest-live");
            fake=new FixtureUpdater(getTargetContext(),nextApk,json.toString());final FixtureUpdater test=fake;
            ui(()->{real.remove(about);about.hideDetails();about.updater=test;about.details=true;test.observe(about);test.check();});waitUntil(()->test.state==AppUpdater.State.AVAILABLE,"fixture available");shot("04-available-fixture");
            test.slow=true;ui(()->test.download());waitUntil(()->test.received>0,"progress");shot("05-downloading-fixture");ui(()->{test.cancel();about.hideDetails();});Thread.sleep(600);ok(test.state==AppUpdater.State.AVAILABLE&&!AppUpdater.apk(getTargetContext()).exists(),"cancel removes partial and suppresses stale completion");
            test.networkError=true;ui(()->{about.details=true;test.check();});waitUntil(()->test.state==AppUpdater.State.CHECK_FAILED,"offline check");shot("06-network-error-fixture");ok(test.error.contains("网络"),"network failure has actionable message");test.networkError=false;
            ui(()->test.check());waitUntil(()->test.state==AppUpdater.State.AVAILABLE,"retry check");
            test.payload=nextApk.clone();test.payload[100]^=1;test.slow=false;ui(()->test.download());waitUntil(()->test.state==AppUpdater.State.DOWNLOAD_FAILED,"corrupt download");ok(test.error.contains("校验失败"),"corrupt APK never becomes installable");shot("07-download-error-fixture");
            test.payload=nextApk;ui(()->test.download());waitUntil(()->test.state==AppUpdater.State.READY,"valid download");ok(AppUpdater.apk(getTargetContext()).length()==nextApk.length,"valid future APK downloaded and signature verified");shot("08-ready-fixture");
            try(InputStream in=getTargetContext().getContentResolver().openInputStream(UpdateApkProvider.uri(getTargetContext()))){ok(in.read()=='P',"provider serves verified APK");}
            Intent install=AboutActivity.installIntent(getTargetContext());ok(install.getData().equals(UpdateApkProvider.uri(getTargetContext()))&&"application/vnd.android.package-archive".equals(install.getType())&&(install.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(install.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0&&install.getClipData()!=null,"installer receives read-only content grant");
            rejects(()->getTargetContext().getContentResolver().openOutputStream(UpdateApkProvider.uri(getTargetContext())),"provider rejects writes");rejects(()->getTargetContext().getContentResolver().openInputStream(android.net.Uri.parse("content://com.example.yuejie.updates/../databases/reading_dog")),"provider rejects traversal");
            byte[] wrong=asset("wrong-signer.apk");File wrongFile=new File(output,"wrong.apk");try(OutputStream stream=new FileOutputStream(wrongFile)){stream.write(wrong);}UpdateRelease wrongRelease=new UpdateRelease(fixture(wrong));rejects(()->test.verify(wrongFile,wrongRelease,hash(wrong)),"reject foreign signing certificate");wrongFile.delete();
            // Simulate a cold updater instance: a subsequent explicit check reuses the verified APK.
            FixtureUpdater restored=new FixtureUpdater(getTargetContext(),nextApk,json.toString());ui(()->restored.check());waitUntil(()->restored.state==AppUpdater.State.READY,"restore complete APK");ok(restored.state==AppUpdater.State.READY,"complete APK recovered after updater recreation");restored.io.shutdownNow();
            // Capture the actual install intent without installing the synthetic future version.
            if(Build.VERSION.SDK_INT<26||getTargetContext().getPackageManager().canRequestPackageInstalls()){
                IntentFilter filter=new IntentFilter(Intent.ACTION_VIEW);filter.addDataType("application/vnd.android.package-archive");filter.addDataScheme("content");ActivityMonitor installer=addMonitor(filter,new ActivityResult(Activity.RESULT_CANCELED,null),true);ui(()->about.install());ok(installer.getHits()==1,"system installer intent with content URI");removeMonitor(installer);
            }else{ui(()->about.install());ok(about.permissionPrompt,"install permission explained before Settings");shot("09-install-permission");}
            ui(()->{about.hideDetails();about.updater=real;real.observe(about);});
        }catch(Throwable e){failure=e;}
        finally{
            if(fake!=null){final FixtureUpdater f=fake;ui(()->f.remove(about));f.io.shutdownNow();}
            AppUpdater.apk(getTargetContext()).delete();new File(AppUpdater.folder(getTargetContext()),"update.part").delete();
            if(about!=null)ui(()->about.finish());if(home!=null)ui(()->home.finish());
            try{if(output!=null)try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(output,"results.json")),"UTF-8")){out.write(new JSONObject().put("checks",results).put("liveDownloadSkipped", "true".equals(options.getString("skipLiveDownload"))).put("failure",failure==null?JSONObject.NULL:android.util.Log.getStackTraceString(failure)).toString(2));}}catch(Exception ignored){}
        }
        Bundle bundle=new Bundle();bundle.putString("stream",failure==null?"PASS "+results.length()+" update checks":"FAIL "+android.util.Log.getStackTraceString(failure));finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,bundle);
    }
}
