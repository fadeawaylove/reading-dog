package com.example.yuejie;

import org.json.*;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.util.Locale;

/** Contract for the public, stable reading-dog GitHub releases. */
final class UpdateRelease {
    static final String PROJECT="https://github.com/fadeawaylove/reading-dog";
    static final String API="https://api.github.com/repos/fadeawaylove/reading-dog/releases/latest";
    static final long MAX_APK=100L*1024*1024;
    final String version,notes,date,name,url,checksumUrl,digest;
    final long size;
    final JSONObject json;

    UpdateRelease(JSONObject data)throws Exception {
        if(data.optBoolean("draft")||data.optBoolean("prerelease"))throw new IOException("暂时没有可用的正式版本");
        version=normalize(data.getString("tag_name"));
        notes=data.optString("body","").trim();
        date=data.optString("published_at","");
        json=data;
        name="reading-dog-"+version+".apk";
        JSONArray assets=data.getJSONArray("assets");
        JSONObject apk=null;String sums="";
        for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.getJSONObject(i);
            if(name.equals(asset.optString("name"))){if(apk!=null)throw new IOException("版本附件重复，请稍后重试");apk=asset;}
            if("SHA256SUMS.txt".equals(asset.optString("name")))sums=asset.getString("browser_download_url");
        }
        if(apk==null)throw new IOException("新版本安装包尚未准备好，请稍后重试");
        size=apk.getLong("size");
        if(size<=0||size>MAX_APK)throw new IOException("安装包大小异常");
        url=apk.getString("browser_download_url");
        requireAssetUrl(url);
        if(!sums.isEmpty())requireAssetUrl(sums);
        checksumUrl=sums;
        String hash=apk.optString("digest","").toLowerCase(Locale.ROOT);
        digest=hash.matches("sha256:[0-9a-f]{64}")?hash.substring(7):"";
        if(digest.isEmpty()&&checksumUrl.isEmpty())throw new IOException("此版本缺少文件校验信息");
    }

    static void requireAssetUrl(String url)throws Exception {
        URI u=new URI(url);
        if(!"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1||u.getQuery()!=null||u.getFragment()!=null||!u.getPath().startsWith("/fadeawaylove/reading-dog/releases/download/"))
            throw new IOException("更新附件地址无效");
    }

    static String normalize(String version)throws IOException {
        String v=version.startsWith("v")?version.substring(1):version;
        if(!v.matches("[0-9]{1,9}(\\.[0-9]{1,9}){0,3}"))throw new IOException("无法识别版本号");
        return v;
    }

    static int compare(String a,String b)throws IOException {
        String[] x=normalize(a).split("\\."),y=normalize(b).split("\\.");
        for(int i=0;i<Math.max(x.length,y.length);i++){
            int c=new BigInteger(i<x.length?x[i]:"0").compareTo(new BigInteger(i<y.length?y[i]:"0"));
            if(c!=0)return c;
        }
        return 0;
    }

    String checksum(String text)throws IOException {
        for(String line:text.split("\\r?\\n")){
            String[] fields=line.trim().split("\\s+",2);
            if(fields.length==2&&fields[0].matches("[a-fA-F0-9]{64}")&&name.equals(fields[1].replaceFirst("^\\*","")))return fields[0].toLowerCase(Locale.ROOT);
        }
        throw new IOException("未找到此安装包的校验信息");
    }

    static String bytes(long value){return value<1024*1024?String.format(Locale.CHINA,"%.0f KB",value/1024.0):String.format(Locale.CHINA,"%.1f MB",value/(1024.0*1024));}
}
