package com.example.yuejie;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** The only application data origin. Private credentials are injected from an ignored asset. */
final class BackendClient {
  static final java.util.concurrent.atomic.AtomicInteger readingRequests=new java.util.concurrent.atomic.AtomicInteger();
  static final ThreadLocal<Integer> commentPriority=new ThreadLocal<>();
  static final String ORIGIN="https://reader.fadeaway.ltd";
  private static String token;
  static synchronized String token(Context c)throws Exception{if(token==null)try(InputStream in=c.getAssets().open("backend-private.json")){token=new JSONObject(new String(ProtocolCrypto.read(in),"UTF-8")).getString("token");}return token;}
  static HttpURLConnection open(Context c,String address)throws Exception{
    URI u=new URI(address);if(!"https".equals(u.getScheme())||!"reader.fadeaway.ltd".equals(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1||u.getFragment()!=null)throw new IOException("服务地址无效");
    HttpURLConnection con=(HttpURLConnection)u.toURL().openConnection();con.setInstanceFollowRedirects(false);con.setConnectTimeout(12000);con.setReadTimeout(45000);con.setRequestProperty("Authorization","Bearer "+token(c));return con;
  }
  static JSONObject request(Context c,String path,JSONObject body)throws Exception{
    HttpURLConnection con=open(c,ORIGIN+path);
    try{
      if(path.startsWith("/api/v1/comments/")){Integer priority=commentPriority.get();con.setRequestProperty("X-Reader-Priority",Integer.toString(priority==null?0:priority));}
      if(body!=null){con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=con.getOutputStream()){out.write(ProtocolCrypto.utf(body.toString()));}}
      int status=con.getResponseCode();InputStream stream=status==200?con.getInputStream():con.getErrorStream();if(stream==null)throw new IOException("服务暂不可用（"+status+"）");
      byte[] bytes;try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>16*1024*1024)throw new IOException("服务数据过大");out.write(b,0,n);}bytes=out.toByteArray();}
      JSONObject result=new JSONObject(new String(bytes,"UTF-8"));if(status!=200)throw new IOException(result.optString("error","服务暂不可用，请重试"));return result;
    }finally{con.disconnect();}
  }
  static Object call(Context c,String method,JSONObject body)throws Exception{boolean reading=!method.startsWith("comments/");if(reading)readingRequests.incrementAndGet();try{return request(c,"/api/v1/"+method,body).get("data");}finally{if(reading)readingRequests.decrementAndGet();}}
  static String media(String url)throws Exception{return ORIGIN+"/api/v1/media?url="+URLEncoder.encode(url,"UTF-8");}
}
