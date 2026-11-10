package com.example.yuejie;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class Server {
  final byte[] token;
  final Path data;
  final String origin;
  final SyncStore sync;
  Server()throws Exception{
    String secret=System.getenv("ACCESS_TOKEN");if(secret==null||secret.length()<32)throw new IllegalStateException("ACCESS_TOKEN required");token=secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    origin=System.getenv().getOrDefault("PUBLIC_ORIGIN","https://reader.fadeaway.ltd");
    data=Paths.get(System.getenv().getOrDefault("DATA_DIR","data"));Files.createDirectories(data);
    sync=new SyncStore(data.resolve("library.db"));
  }
  public static void main(String[] args)throws Exception{
    Server server=new Server();HttpServer http=HttpServer.create(new InetSocketAddress("0.0.0.0",Integer.parseInt(System.getenv().getOrDefault("PORT","3000"))),32);
    http.createContext("/",server::handle);http.setExecutor(new ThreadPoolExecutor(2,4,60,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),new ThreadPoolExecutor.CallerRunsPolicy()));http.start();
    ScheduledExecutorService backups=Executors.newSingleThreadScheduledExecutor();backups.scheduleWithFixedDelay(()->{try{Path folder=server.data.resolve("backups");Files.createDirectories(folder);Path file=folder.resolve("library-"+LocalDate.now(ZoneOffset.UTC)+".db");if(!Files.exists(file))server.sync.backup(file);try(var files=Files.list(folder)){List<Path> sorted=files.sorted(Comparator.reverseOrder()).toList();for(int i=7;i<sorted.size();i++)Files.deleteIfExists(sorted.get(i));}}catch(Exception e){System.err.println("backup failed: "+e.getClass().getSimpleName());}},60,86400,TimeUnit.SECONDS);
    Runtime.getRuntime().addShutdownHook(new Thread(()->{http.stop(3);backups.shutdown();try{server.sync.close();}catch(Exception ignored){}}));System.out.println("Reading Dog backend ready");
  }
  void handle(HttpExchange x)throws IOException{
    String path=x.getRequestURI().getPath();
    try{
      if(path.equals("/health")&&x.getRequestMethod().equals("GET")){json(x,200,new JSONObject().put("status","ok"));return;}
      if(path.startsWith("/fonts/")){
        boolean head=x.getRequestMethod().equals("HEAD");if(!head&&!x.getRequestMethod().equals("GET")){json(x,405,new JSONObject().put("error","请求方法无效"));return;}
        Path file=FontFiles.resolve(data,path);if(file==null){json(x,404,new JSONObject().put("error","字体文件不存在"));return;}
        x.getResponseHeaders().set("Content-Type",path.endsWith(".ttf")?"font/ttf":"application/json; charset=utf-8");
        if(path.endsWith(".ttf"))x.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+file.getFileName()+"\"");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.getResponseHeaders().set("Cache-Control","no-cache");x.getResponseHeaders().set("Content-Length",Long.toString(Files.size(file)));x.sendResponseHeaders(200,head?-1:Files.size(file));if(!head)try(OutputStream out=x.getResponseBody()){Files.copy(file,out);}return;
      }
      if(path.startsWith("/install/")){
        boolean head=x.getRequestMethod().equals("HEAD");
        if(!head&&!x.getRequestMethod().equals("GET")){json(x,405,new JSONObject().put("error","请求方法无效"));return;}
        Path apk=InstallLinks.resolve(data,path);
        if(apk==null){json(x,404,new JSONObject().put("error","下载链接无效"));return;}
        x.getResponseHeaders().set("Content-Type","application/vnd.android.package-archive");
        x.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+apk.getFileName()+"\"");
        x.getResponseHeaders().set("Cache-Control","private, no-store");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.getResponseHeaders().set("Content-Length",Long.toString(Files.size(apk)));
        x.sendResponseHeaders(200,head?-1:Files.size(apk));
        if(!head)try(OutputStream out=x.getResponseBody()){Files.copy(apk,out);}return;
      }
      String auth=x.getRequestHeaders().getFirst("Authorization");byte[] supplied=(auth!=null&&auth.startsWith("Bearer ")?auth.substring(7):"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
      if(!MessageDigest.isEqual(token,supplied)){json(x,401,new JSONObject().put("error","需要有效的应用凭据"));return;}
      if(x.getRequestMethod().equals("GET")){
        if(path.equals("/api/v1/time")){json(x,200,new JSONObject().put("server_time",System.currentTimeMillis()));return;}
        if(path.equals("/api/v1/updates/latest")){Path latest=data.resolve("releases/latest.json");if(!Files.isRegularFile(latest)){json(x,404,new JSONObject().put("error","暂未发布安装包"));return;}json(x,200,new JSONObject(Files.readString(latest)));return;}
        if(path.startsWith("/releases/")){if(!path.matches("/releases/[0-9]+(?:\\.[0-9]+){0,3}/(?:reading-dog-[0-9]+(?:\\.[0-9]+){0,3}\\.apk|SHA256SUMS\\.txt)"))throw new IllegalArgumentException("下载地址无效");Path f=data.resolve(path.substring(1));if(!Files.isRegularFile(f)){json(x,404,new JSONObject().put("error","文件不存在"));return;}x.getResponseHeaders().set("Content-Type",path.endsWith(".apk")?"application/vnd.android.package-archive":"text/plain; charset=utf-8");x.getResponseHeaders().set("Cache-Control","private, max-age=31536000, immutable");x.sendResponseHeaders(200,Files.size(f));try(OutputStream out=x.getResponseBody()){Files.copy(f,out);}return;}
        json(x,404,new JSONObject().put("error","接口不存在"));return;
      }
      if(!x.getRequestMethod().equals("POST")){json(x,405,new JSONObject().put("error","请求方法无效"));return;}
      JSONObject body=new JSONObject(new String(readLimited(x.getRequestBody(),2*1024*1024),java.nio.charset.StandardCharsets.UTF_8));
      if(path.equals("/api/v1/sync")){json(x,200,sync.exchange(body));return;}
      json(x,404,new JSONObject().put("error","接口不存在"));
    }catch(IllegalArgumentException|JSONException e){json(x,400,new JSONObject().put("error","请求参数无效"));}
    catch(Exception e){System.err.println("request failed "+(path.startsWith("/install/")?"/install/[redacted]":path)+" "+e.getClass().getSimpleName());json(x,502,new JSONObject().put("error",e instanceof IOException&&e.getMessage()!=null&&!e.getMessage().contains("http")?e.getMessage():"服务暂不可用，请稍后重试"));}
    finally{x.close();}
  }
  static Map<String,String> query(String raw){Map<String,String> out=new HashMap<>();if(raw!=null)for(String part:raw.split("&")){String[] p=part.split("=",2);out.put(URLDecoder.decode(p[0],java.nio.charset.StandardCharsets.UTF_8),p.length>1?URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8):"");}return out;}
  static byte[] readLimited(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("响应数据过大");out.write(b,0,n);}return out.toByteArray();}
  void json(HttpExchange x,int status,JSONObject value)throws IOException{byte[] b=value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.sendResponseHeaders(status,b.length);try(OutputStream out=x.getResponseBody()){out.write(b);}}
  static boolean mediaHost(String h){return h!=null&&(h.endsWith(".byteimg.com")||h.endsWith(".pstatp.com")||h.endsWith(".toutiaoimg.com")||h.endsWith(".snssdk.com")||h.endsWith(".fqnovelpic.com")||h.endsWith(".fqnovel.com")||h.endsWith(".douyinpic.com")||h.endsWith(".ibytedtos.com"));}
}
