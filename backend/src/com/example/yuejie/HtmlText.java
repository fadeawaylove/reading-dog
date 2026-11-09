package com.example.yuejie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.json.*;
/** Match Android Html.fromHtml for paragraph inline text, including supplementary UTF-16 offsets. */
final class HtmlText {
  static final class Decoded {final StringBuilder text=new StringBuilder();final JSONArray images=new JSONArray();}
  static String decode(String html){return decodeImages(html).text.toString();}
  static Decoded decodeImages(String html){Decoded out=new Decoded();for(Node n:Jsoup.parseBodyFragment(html).body().childNodes())append(n,out);return out;}
  static int dimension(String value){try{int n=Integer.parseInt(value.trim().replaceFirst("(?i)px$",""));return n>0&&n<=100000?n:0;}catch(NumberFormatException e){return 0;}}
  static void append(Node n,Decoded result){
    StringBuilder out=result.text;
    if(n instanceof TextNode){String s=((TextNode)n).getWholeText();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c==' '||c=='\n'||c=='\r'||c=='\t'||c=='\f'){if(out.length()>0&&out.charAt(out.length()-1)!=' '&&out.charAt(out.length()-1)!='\n')out.append(' ');}else out.append(c);}return;}
    if(n instanceof Element&&((Element)n).normalName().equals("br")){out.append('\n');return;}
    if(n instanceof Element&&((Element)n).normalName().equals("img")){Element image=(Element)n;String url=image.attr("src");if(url.isBlank())url=image.attr("data-src");if(url.startsWith("//"))url="https:"+url;result.images.put(new JSONObject().put("offset",out.length()).put("url",url).put("width",dimension(image.attr("width"))).put("height",dimension(image.attr("height"))));out.append('\ufffc');return;}
    for(Node child:n.childNodes())append(child,result);
    if(n instanceof Element&&java.util.Set.of("p","div","blockquote").contains(((Element)n).normalName())){while(out.length()>0&&out.charAt(out.length()-1)==' ')out.setLength(out.length()-1);if(out.length()>0&&!out.toString().endsWith("\n\n"))out.append(out.charAt(out.length()-1)=='\n'?"\n":"\n\n");}
  }
}
