package com.example.yuejie;
import java.nio.file.*;
import java.util.regex.*;
import org.json.*;
/** Generate local-only paragraph fixtures from existing native ciphertext vectors. */
public final class TextParityVectors {
 public static void main(String[] args)throws Exception{JSONArray fixtures=new JSONArray();int chapters=0;for(String line:Files.readAllLines(Path.of(args[0]))){String[] v=line.split("\t");if(!v[0].equals("chapter"))continue;chapters++;String html=ProtocolCrypto.chapter(v[1],ProtocolCrypto.hex(v[2])).replaceAll("(?is)<(script|style|header)\\b[^>]*>.*?</\\1>","");Matcher m=Pattern.compile("(?is)<p\\b([^>]*)>(.*?)</p>").matcher(html);while(m.find())fixtures.put(new JSONObject().put("html",m.group(2)).put("decoded",HtmlText.decode(m.group(2)).trim()));}Files.writeString(Path.of(args[1]),fixtures.toString());System.out.println("Generated "+fixtures.length()+" native paragraph fixtures from "+chapters+" encrypted chapters");}
}
