package com.example.yuejie;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.regex.*;
import org.json.*;

/** Scoped bootstrap links grant access to one immutable APK, never the library API. */
final class InstallLinks {
  static Path resolve(Path data,String path)throws Exception{
    Matcher match=Pattern.compile("/install/([0-9a-f]{48})/(reading-dog-([0-9]+(?:\\.[0-9]+){0,3})\\.apk)").matcher(path);
    if(!match.matches())return null;
    Path links=data.resolve("install-links.json");if(!Files.isRegularFile(links))return null;
    String hash=ProtocolCrypto.hex(MessageDigest.getInstance("SHA-256").digest(match.group(1).getBytes(StandardCharsets.US_ASCII)));
    JSONObject entry=new JSONObject(Files.readString(links)).optJSONObject(hash);
    if(entry==null||!match.group(3).equals(entry.optString("version")))return null;
    Path apk=data.resolve("releases").resolve(match.group(3)).resolve(match.group(2));
    return Files.isRegularFile(apk)?apk:null;
  }
}
