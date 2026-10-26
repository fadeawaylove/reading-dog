package com.example.yuejie;

import android.content.Context;
import android.content.pm.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Application-owned work survives leaving About; no Activity is retained while stopped. */
class AppUpdater {
    enum State { IDLE,CHECKING,LATEST,AVAILABLE,DOWNLOADING,VERIFYING,READY,CHECK_FAILED,DOWNLOAD_FAILED }
    interface Listener {void changed();}
    private static AppUpdater instance;
    static synchronized AppUpdater get(Context c){if(instance==null)instance=new AppUpdater(c.getApplicationContext());return instance;}
    final Context context;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newSingleThreadExecutor();
    final Set<Listener> listeners=new HashSet<>();
    volatile int generation;
    volatile HttpURLConnection connection;
    State state=State.IDLE;
    UpdateRelease release;
    String error="";
    long received;
    final String currentVersion;
    final int currentCode;
    AppUpdater(Context c){context=c;try{PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);currentVersion=p.versionName;currentCode=p.versionCode;}catch(Exception e){throw new IllegalStateException(e);}}
    static File folder(Context c){return new File(c.getFilesDir(),"app-update");}
    static File apk(Context c){return new File(folder(c),"update.apk");}
    void observe(Listener listener){listeners.add(listener);listener.changed();}
    void remove(Listener listener){listeners.remove(listener);}
    void notifyChanged(){for(Listener l:new ArrayList<>(listeners))l.changed();}
    boolean busy(){return state==State.CHECKING||state==State.DOWNLOADING||state==State.VERIFYING;}
    void publish(int token,Runnable update){main.post(()->{if(generation==token){update.run();notifyChanged();}});}

    void check(){
        if(busy())return;
        int token=++generation;error="";release=null;state=State.CHECKING;notifyChanged();
        io.execute(()->{
            try{
                UpdateRelease next=new UpdateRelease(new JSONObject(readText(UpdateRelease.API,1024*1024,token)));
                boolean newer=UpdateRelease.compare(next.version,currentVersion)>0;
                boolean ready=false;
                if(newer&&apk(context).isFile()){
                    try{verify(apk(context),next,expectedDigest(next,token));ready=true;}catch(Exception ignored){apk(context).delete();}
                }
                final boolean downloaded=ready;
                publish(token,()->{release=next;state=!newer?State.LATEST:downloaded?State.READY:State.AVAILABLE;});
            }catch(Exception e){publish(token,()->{error=message(e);state=State.CHECK_FAILED;});}
        });
    }

    void download(){
        if(busy()||release==null)return;
        final UpdateRelease target=release;final int token=++generation;
        state=State.DOWNLOADING;received=0;error="";notifyChanged();
        io.execute(()->{
            File partial=new File(folder(context),"update.part");
            try{
                if(!folder(context).isDirectory()&&!folder(context).mkdirs())throw new IOException("无法创建下载目录");
                String digest=expectedDigest(target,token);
                HttpURLConnection c=open(target.url,token);
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(partial)){
                    byte[] buffer=new byte[16384];long total=0,last=0;int n;
                    while((n=in.read(buffer))!=-1){
                        requireActive(token);total+=n;
                        if(total>target.size)throw new IOException("安装包大小不符，请重新下载");
                        out.write(buffer,0,n);
                        long now=SystemClock.uptimeMillis();
                        if(now-last>150){last=now;final long bytes=total;publish(token,()->received=bytes);}
                    }
                    if(total!=target.size)throw new IOException("安装包下载不完整，请重试");
                }finally{c.disconnect();}
                publish(token,()->{received=target.size;state=State.VERIFYING;});
                verify(partial,target,digest);requireActive(token);
                if(apk(context).exists()&&!apk(context).delete())throw new IOException("无法替换旧安装包");
                if(!partial.renameTo(apk(context)))throw new IOException("无法保存安装包");
                publish(token,()->state=State.READY);
            }catch(Exception e){partial.delete();publish(token,()->{error=message(e);state=State.DOWNLOAD_FAILED;});}
        });
    }

    void cancel(){
        if(!busy())return;
        ++generation;HttpURLConnection c=connection;if(c!=null)ioDisconnect(c);
        state=release==null?State.IDLE:State.AVAILABLE;error="";received=0;notifyChanged();
    }
    private void ioDisconnect(HttpURLConnection c){new Thread(c::disconnect,"update-cancel").start();}
    private void requireActive(int token)throws IOException {if(generation!=token)throw new IOException("已取消");}
    String expectedDigest(UpdateRelease r,int token)throws Exception {return r.digest.isEmpty()?r.checksum(readText(r.checksumUrl,64*1024,token)):r.digest;}

    HttpURLConnection open(String address,int token)throws Exception {
        URL url=new URL(address);
        for(int i=0;i<6;i++){
            requireActive(token);
            String host=url.getHost();
            if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||!(host.equals("api.github.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("更新服务地址无效");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();connection=c;
            c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","ReadingDog/"+currentVersion);c.setRequestProperty("Accept",host.equals("api.github.com")?"application/vnd.github+json":"application/octet-stream");
            int code;
            try{code=c.getResponseCode();}catch(Exception e){c.disconnect();throw e;}
            if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("更新服务重定向异常");url=new URL(url,location);continue;}
            if(code!=200){c.disconnect();if(code==403||code==429)throw new IOException("更新服务请求较多，请稍后重试");if(code==404)throw new IOException("暂未找到正式版本或安装包，请稍后重试");throw new IOException("更新服务暂不可用（"+code+"），请稍后重试");}
            return c;
        }
        throw new IOException("更新服务重定向过多");
    }
    String readText(String url,int limit,int token)throws Exception {
        HttpURLConnection c=open(url,token);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){requireActive(token);if(out.size()+n>limit)throw new IOException("更新信息过大");out.write(buffer,0,n);}
            return out.toString("UTF-8");
        }finally{c.disconnect();}
    }

    void verify(File file,UpdateRelease target,String expected)throws Exception {
        if(file.length()!=target.size)throw new IOException("安装包大小不符，请重新下载");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1)sha.update(bytes,0,n);}
        StringBuilder hash=new StringBuilder();for(byte b:sha.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!hash.toString().equalsIgnoreCase(expected))throw new IOException("安装包校验失败，请重新下载");
        PackageManager pm=context.getPackageManager();
        PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
        if(archive==null||!installed.packageName.equals(archive.packageName))throw new IOException("安装包不属于阅读狗");
        if(archive.versionCode<=installed.versionCode||UpdateRelease.compare(archive.versionName,target.version)!=0)throw new IOException("安装包版本不符，已停止安装");
        if(archive.signatures==null||installed.signatures==null||archive.signatures.length==0||!new HashSet<>(Arrays.asList(archive.signatures)).equals(new HashSet<>(Arrays.asList(installed.signatures))))throw new IOException("安装包签名不一致，已停止安装");
        if(Build.VERSION.SDK_INT>=24&&archive.applicationInfo!=null&&archive.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("此版本不支持当前 Android 系统");
    }
    static String message(Exception e){if(e instanceof SocketTimeoutException)return "连接超时，请检查网络后重试";if(e instanceof UnknownHostException||e instanceof ConnectException)return "未能连接更新服务，请检查网络后重试";if(e instanceof IOException)return e.getMessage()==null?"网络连接中断，请重试":e.getMessage();return "更新信息异常，请稍后重试";}
}
