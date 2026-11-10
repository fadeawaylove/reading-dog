package com.example.yuejie;
import java.io.*;
import java.net.*;
import java.util.Locale;

/** Direct CDN downloads. No application credentials, including across redirects. */
final class UpstreamImages {
 interface Connection {HttpURLConnection open(URL url)throws IOException;}
 static byte[] read(String address)throws Exception{return read(address,url->(HttpURLConnection)url.openConnection());}
 static boolean host(String h){return h!=null&&(h.endsWith(".byteimg.com")||h.endsWith(".pstatp.com")||h.endsWith(".toutiaoimg.com")||h.endsWith(".snssdk.com")||h.endsWith(".fqnovelpic.com")||h.endsWith(".fqnovel.com")||h.endsWith(".douyinpic.com")||h.endsWith(".ibytedtos.com"));}
 static byte[] read(String address,Connection connection)throws Exception{
  URL url=new URL(ParagraphCommentApi.secure(address));
  for(int hop=0;hop<4;hop++){
   if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||url.getPort()!=-1||!host(url.getHost()))throw new IOException("图片地址无效");
   HttpURLConnection c=connection.open(url);c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Mozilla/5.0");
   try{
    int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("图片重定向无效");url=new URL(url,location);continue;}
    if(status!=200)throw new IOException("图片暂不可用");String type=c.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).matches("image/(jpeg|png|webp|gif|avif)(;.*)?"))throw new IOException("图片格式无效");
    if(c.getContentLengthLong()>ReaderImages.BYTE_LIMIT)throw new IOException("图片过大");
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>ReaderImages.BYTE_LIMIT)throw new IOException("图片过大");out.write(b,0,n);}return out.toByteArray();}
   }finally{c.disconnect();}
  }
  throw new IOException("图片重定向过多");
 }
}
