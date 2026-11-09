package com.example.yuejie;

import android.content.*;import android.graphics.Typeface;import android.os.*;import org.json.*;
import java.io.*;import java.net.*;import java.security.*;import java.util.*;import java.util.concurrent.*;

/** Explicit optional downloads. Only verified, atomically installed files can supply a Typeface. */
final class ReaderFonts {
 static final String SYSTEM="system";
 static final class Font {
  final String id,name,file,sha256,url;final long size;
  Font(JSONObject o)throws JSONException{id=o.getString("id");name=o.getString("name");file=o.getString("file");sha256=o.getString("sha256");url=o.getString("url");size=o.getLong("size");if(!id.matches("sans|serif")||!file.matches("reader-(body|serif)\\.ttf")||!sha256.matches("[0-9a-f]{64}")||size<1||size>32*1024*1024||!url.equals(BackendClient.ORIGIN+"/fonts/"+file))throw new IllegalArgumentException("字体目录无效");}
 }
 static final class State {
  final Font font;Typeface face;volatile boolean downloading;volatile long received;volatile String error="";int generation;
  State(Font f){font=f;}
 }
 interface Source{HttpURLConnection open(String url)throws Exception;}
 private static final Map<String,ReaderFonts> INSTANCES=new HashMap<>();
 static synchronized ReaderFonts get(Context c){Context app=c.getApplicationContext();String key=app.getFilesDir().getAbsolutePath();ReaderFonts fonts=INSTANCES.get(key);if(fonts==null){fonts=new ReaderFonts(app);INSTANCES.put(key,fonts);}return fonts;}
 final Context context;final File folder;final LinkedHashMap<String,State> states=new LinkedHashMap<>();
 final Handler main=new Handler(Looper.getMainLooper());final Set<Runnable> listeners=new HashSet<>();
 final ExecutorService io=Executors.newSingleThreadExecutor();Source source=ReaderFonts::openPublic;
 ReaderFonts(Context c){context=c;folder=new File(c.getFilesDir(),"reader-fonts");if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException("无法创建字体目录");
  try(InputStream input=c.getAssets().open("font-catalog.json")){JSONArray fonts=new JSONObject(new String(ProtocolCrypto.read(input),"UTF-8")).getJSONArray("fonts");for(int i=0;i<fonts.length();i++){Font f=new Font(fonts.getJSONObject(i));State s=new State(f);states.put(f.id,s);File file=file(f.id);if(file.isFile()){try{verify(file,f);s.face=Typeface.createFromFile(file);}catch(Exception invalid){file.delete();}}}File[] partials=folder.listFiles((dir,name)->name.endsWith(".part"));if(partials!=null)for(File p:partials)p.delete();}catch(Exception e){throw new IllegalStateException("无法读取字体目录",e);}
 }
 synchronized List<String> ids(){return new ArrayList<>(states.keySet());}
 synchronized State state(String id){State s=states.get(id);if(s==null)throw new IllegalArgumentException("字体不存在");return s;}
 File file(String id){return new File(folder,state(id).font.file);}
 synchronized boolean available(String id){return !SYSTEM.equals(id)&&states.containsKey(id)&&states.get(id).face!=null&&file(id).isFile();}
 synchronized String resolve(String id){return available(id)?id:SYSTEM;}
 synchronized Typeface face(String id){return available(id)?state(id).face:Typeface.DEFAULT;}
 synchronized String key(String id){return available(id)?id+":"+state(id).font.sha256:"system-v1";}
 void observe(Runnable listener){listeners.add(listener);listener.run();}void remove(Runnable listener){listeners.remove(listener);}
 void changed(){main.post(()->{for(Runnable r:new ArrayList<>(listeners))r.run();});}
 static HttpURLConnection openPublic(String address)throws Exception{URI uri=new URI(address);if(!address.startsWith(BackendClient.ORIGIN+"/fonts/")||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null)throw new IOException("字体下载地址无效");HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);return c;}
 synchronized void download(String id){State s=state(id);if(s.downloading||available(id))return;int generation=++s.generation;s.downloading=true;s.received=0;s.error="";changed();io.execute(()->download(s,generation));}
 private void requireActive(State s,int generation)throws IOException{synchronized(this){if(generation!=s.generation)throw new IOException("已取消");}}
 private void download(State s,int generation){Font f=s.font;File partial=new File(folder,f.file+"."+generation+".part");HttpURLConnection connection=null;
  try{
   connection=source.open(f.url);if(connection.getResponseCode()!=200)throw new IOException("字体下载暂不可用，请重试");long expected=connection.getContentLengthLong();if(expected>=0&&expected!=f.size)throw new IOException("字体大小不符，请重试");
   MessageDigest hash=MessageDigest.getInstance("SHA-256");long received=0,last=0;
   try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(partial)){byte[] b=new byte[16384];int n;while((n=input.read(b))!=-1){requireActive(s,generation);received+=n;if(received>f.size)throw new IOException("字体文件过大，请重试");hash.update(b,0,n);output.write(b,0,n);synchronized(this){s.received=received;}long now=SystemClock.elapsedRealtime();if(now-last>=100){last=now;changed();}}output.getFD().sync();}
   if(received!=f.size||!ProtocolCrypto.hex(hash.digest()).equals(f.sha256))throw new IOException("字体完整性校验失败，请重试");
   Typeface face=Typeface.createFromFile(partial);requireActive(s,generation);
   synchronized(this){requireActive(s,generation);File target=file(f.id);if(target.exists()&&!target.delete())throw new IOException("无法替换字体文件");if(!partial.renameTo(target))throw new IOException("无法保存字体文件");s.face=face;s.received=f.size;s.downloading=false;s.error="";}
  }catch(Exception e){synchronized(this){if(generation==s.generation){s.downloading=false;s.error=e instanceof IOException&&e.getMessage()!=null?e.getMessage():"字体下载失败，请重试";}}}
  finally{if(connection!=null)connection.disconnect();partial.delete();changed();}
 }
 static void verify(File file,Font font)throws Exception{if(file.length()!=font.size)throw new IOException("字体大小不符");MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}if(!ProtocolCrypto.hex(hash.digest()).equals(font.sha256))throw new IOException("字体校验失败");}
 synchronized void delete(String id)throws IOException{State s=state(id);File target=file(id);if(target.exists()&&!target.delete())throw new IOException("无法删除字体文件，请重试");++s.generation;s.face=null;s.downloading=false;s.received=0;s.error="";SharedPreferences prefs=context.getSharedPreferences("ReaderActivity",0);if(id.equals(prefs.getString("font_family",SYSTEM)))prefs.edit().putString("font_family",SYSTEM).apply();changed();}
}
