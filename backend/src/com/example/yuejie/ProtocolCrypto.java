package com.example.yuejie;

import java.io.*;
import java.net.URLDecoder;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Byte-for-byte port of the verified independent protocol, not a downloader bridge. */
public final class ProtocolCrypto {
  static final SecureRandom RANDOM=new SecureRandom();
  static byte[] utf(String s){return s.getBytes(StandardCharsets.UTF_8);}
  static byte[] hex(String s){byte[] a=new byte[s.length()/2];for(int i=0;i<a.length;i++)a[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return a;}
  static String hex(byte[] a){StringBuilder s=new StringBuilder();for(byte b:a)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
  static byte[] cat(byte[]... arrays){ByteArrayOutputStream o=new ByteArrayOutputStream();for(byte[] a:arrays)o.write(a,0,a.length);return o.toByteArray();}
  static byte[] random(int n){byte[] a=new byte[n];RANDOM.nextBytes(a);return a;}
  static byte[] md5(byte[] a)throws Exception{return MessageDigest.getInstance("MD5").digest(a);}
  static final String B64="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  static String b64(byte[] a){StringBuilder o=new StringBuilder();for(int i=0;i<a.length;i+=3){int n=(a[i]&255)<<16;if(i+1<a.length)n|=(a[i+1]&255)<<8;if(i+2<a.length)n|=a[i+2]&255;o.append(B64.charAt(n>>>18)).append(B64.charAt((n>>>12)&63)).append(i+1<a.length?B64.charAt((n>>>6)&63):'=').append(i+2<a.length?B64.charAt(n&63):'=');}return o.toString();}
  static byte[] un64(String s){s=s.replaceAll("\\s","");if(s.length()%4!=0)throw new IllegalArgumentException("Invalid Base64");ByteArrayOutputStream o=new ByteArrayOutputStream();for(int i=0;i<s.length();i+=4){int n=0,pad=0;for(int j=0;j<4;j++){char c=s.charAt(i+j);int v=B64.indexOf(c);if(c=='='){v=0;pad++;}else if(v<0||pad>0)throw new IllegalArgumentException("Invalid Base64");n=(n<<6)|v;}if(pad>2||(pad>0&&i+4!=s.length()))throw new IllegalArgumentException("Invalid Base64 padding");o.write(n>>>16);if(pad<2)o.write(n>>>8);if(pad<1)o.write(n);}return o.toByteArray();}
  static byte[] aes(byte[] a,byte[] key,byte[] iv,boolean decrypt)throws Exception{Cipher c=Cipher.getInstance("AES/CBC/PKCS5Padding");c.init(decrypt?Cipher.DECRYPT_MODE:Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));return c.doFinal(a);}
  static byte[] pad(byte[] a){int n=16-a.length%16;byte[] b=Arrays.copyOf(a,a.length+n);Arrays.fill(b,a.length,b.length,(byte)n);return b;}
  static long le(byte[] a,int off){return ByteBuffer.wrap(a,off,8).order(ByteOrder.LITTLE_ENDIAN).getLong();}
  static byte[] le(long... a){ByteBuffer b=ByteBuffer.allocate(a.length*8).order(ByteOrder.LITTLE_ENDIAN);for(long v:a)b.putLong(v);return b.array();}
  static byte[] sm3(byte[] input){
    int[] v={0x7380166f,0x4914b2b9,0x172442d7,0xda8a0600,0xa96f30bc,0x163138aa,0xe38dee4d,0xb0fb0e4e};
    byte[] m=Arrays.copyOf(input,((input.length+9+63)/64)*64);m[input.length]=(byte)128;ByteBuffer.wrap(m,m.length-8,8).putLong((long)input.length*8);
    for(int off=0;off<m.length;off+=64){int[] w=new int[68];ByteBuffer b=ByteBuffer.wrap(m,off,64);for(int i=0;i<16;i++)w[i]=b.getInt();for(int j=16;j<68;j++){int x=w[j-16]^w[j-9]^Integer.rotateLeft(w[j-3],15);w[j]=x^Integer.rotateLeft(x,15)^Integer.rotateLeft(x,23)^Integer.rotateLeft(w[j-13],7)^w[j-6];}
      int a=v[0],bb=v[1],c=v[2],d=v[3],e=v[4],f=v[5],g=v[6],h=v[7];for(int j=0;j<64;j++){int ar=Integer.rotateLeft(a,12),s1=Integer.rotateLeft(ar+e+Integer.rotateLeft(j<16?0x79cc4519:0x7a879d8a,j),7),s2=s1^ar;int ff=j<16?a^bb^c:(a&bb)|(a&c)|(bb&c),gg=j<16?e^f^g:(e&f)|(~e&g);int t1=ff+d+s2+(w[j]^w[j+4]),t2=gg+h+s1+w[j];d=c;c=Integer.rotateLeft(bb,9);bb=a;a=t1;h=g;g=Integer.rotateLeft(f,19);f=e;e=t2^Integer.rotateLeft(t2,9)^Integer.rotateLeft(t2,17);}int[] x={a,bb,c,d,e,f,g,h};for(int i=0;i<8;i++)v[i]^=x[i];}
    ByteBuffer o=ByteBuffer.allocate(32);for(int x:v)o.putInt(x);return o.array();
  }
  static void var(ByteArrayOutputStream o,long n){while((n&~127L)!=0){o.write((int)(n&127)|128);n>>>=7;}o.write((int)n);}
  static byte[] fields(SortedMap<Integer,Object> fields){ByteArrayOutputStream o=new ByteArrayOutputStream();for(Map.Entry<Integer,Object> e:fields.entrySet()){Object v=e.getValue();if(v instanceof Number){var(o,e.getKey()<<3);var(o,((Number)v).longValue());}else{byte[] a=v instanceof byte[]?(byte[])v:utf((String)v);var(o,(e.getKey()<<3)|2);var(o,a.length);o.write(a,0,a.length);}}return o.toByteArray();}
  static SortedMap<Integer,Object> map(Object... pairs){SortedMap<Integer,Object> m=new TreeMap<>();for(int i=0;i<pairs.length;i+=2)m.put((Integer)pairs[i],pairs[i+1]);return m;}
  static byte[] simon(byte[] a){byte[] key=hex("fc78e0a9657a0c748ce51559903ccf03510e51d3cff232d71343e88a321c5304");long[] k=new long[72];for(int i=0;i<4;i++)k[i]=le(key,i*8);long z=0x3DC94C3A046D678BL;for(int i=4;i<72;i++){long t=k[i-3]^Long.rotateLeft(k[i-1],61);k[i]=k[i-4]^(-4L|((z>>>((i-4)%62))&1))^t^Long.rotateLeft(t,63);}ByteArrayOutputStream o=new ByteArrayOutputStream();for(int off=0;off<a.length;off+=16){long x=le(a,off),y=le(a,off+8);for(long kk:k){long t=y;y=x^(Long.rotateLeft(y,1)&Long.rotateLeft(y,8))^Long.rotateLeft(y,2)^kk;x=t;}byte[] b=le(x,y);o.write(b,0,b.length);}return o.toByteArray();}
  public static String argus(String query,long ts,int random31)throws Exception{return argus(query,ts,random31,null);}
  public static String argus(String query,long ts,int random31,byte[] body)throws Exception{
    Map<String,String> q=new HashMap<>();for(String s:query.split("&")){int n=s.indexOf('=');if(n>=0)q.put(URLDecoder.decode(s.substring(0,n),"UTF-8"),URLDecoder.decode(s.substring(n+1),"UTF-8"));}
    String os=q.containsKey("os_version")?q.get("os_version"):"",digits=os.replaceAll("[^0-9]","");while(digits.length()<6)digits="0"+digits;long num=0;long[] weights={20480,2048,20971520,0x200000,1342177280,0x8000000};for(int i=0;i<6;i++)num+=(digits.charAt(i)-'0')*weights[i];
    SortedMap<Integer,Object> f=map(1,1077940818,2,2,3,random31,4,"1967",6,"1611921764",8,"v04.04.05-ov-android",9,134744640,10,new byte[8],11,0,12,2*ts,13,Arrays.copyOf(sm3(body==null?new byte[16]:MessageDigest.getInstance("MD5").digest(body)),6),14,Arrays.copyOf(sm3(utf(query)),6),20,738,23,fields(map(1,q.containsKey("device_type")?q.get("device_type"):"",2,os,3,"googleplay",4,num)));
    if(q.containsKey("device_id"))f.put(5,q.get("device_id"));if(q.containsKey("version_name"))f.put(7,q.get("version_name"));byte[] cipher=simon(pad(fields(f))),xor=hex("f2f7fcfff2f7fcff"),mixed=cat(xor,cipher);for(int i=0;i<cipher.length;i++)mixed[i+8]=(byte)(cipher[i]^xor[i%8]);for(int i=0;i<mixed.length/2;i++){byte t=mixed[i];mixed[i]=mixed[mixed.length-1-i];mixed[mixed.length-1-i]=t;}
    byte[] envelope=cat(hex("a66ead9f7701d00c18"),mixed,hex("616f"));return b64(cat(hex("f281"),aes(envelope,md5(hex("ac1adaae95a7af94a5114ab3b3a97dd8")),md5(hex("0050aa0a39314c40528caec95256c28c")),false)));
  }
  public static String ladon(long ts,byte[] seed)throws Exception{byte[] key=utf(hex(md5(cat(seed,utf("1967")))));long word=le(key,0);ArrayDeque<Long> queue=new ArrayDeque<>();for(int i=1;i<4;i++)queue.add(le(key,i*8));long[] keys=new long[34];keys[0]=word;for(int i=0;i<33;i++){long w=(Long.rotateRight(queue.remove(),8)+word)^i;queue.add(w);word=w^Long.rotateLeft(word,3);keys[i+1]=word;}byte[] a=utf(ts+"-1611921764-3019");int n=(16-a.length%16)%16;byte[] b=Arrays.copyOf(a,a.length+n);Arrays.fill(b,a.length,b.length,(byte)n);ByteArrayOutputStream o=new ByteArrayOutputStream();o.write(seed);for(int off=0;off<b.length;off+=16){long l=le(b,off),r=le(b,off+8);for(long k:keys){r=(Long.rotateRight(r,8)+l)^k;l=r^Long.rotateLeft(l,3);}o.write(le(l,r));}return b64(o.toByteArray());}
  static final byte[] REGISTER_KEY=hex("ac25c67ddd8f38c1b37a2348828e222e");
  static String registerContent(String device,byte[] iv)throws Exception{return b64(cat(iv,aes(le(Long.parseUnsignedLong(device),0),REGISTER_KEY,iv,false)));}
  static byte[] unwrap(String s,byte[] key)throws Exception{byte[] a=un64(s);if(a.length<32||a.length%16!=0)throw new IOException("Invalid encrypted content");return aes(Arrays.copyOfRange(a,16,a.length),key,Arrays.copyOf(a,16),true);}
  static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}
  static String chapter(String s,byte[] key)throws Exception{byte[] a=unwrap(s,key);if(a.length>2&&(a[0]&255)==31&&(a[1]&255)==139){try(InputStream in=new GZIPInputStream(new ByteArrayInputStream(a))){a=read(in);}}return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(a)).toString();}
}
