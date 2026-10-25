package com.example.yuejie;
import java.nio.file.*;import java.nio.charset.*;import java.security.*;
public class ProtocolCheck {
 public static void main(String[] args)throws Exception{int n=0;for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)){String[] v=line.split("\t");String actual,expected;switch(v[0]){
 case "argus":actual=ProtocolCrypto.argus(new String(ProtocolCrypto.un64(v[1]),StandardCharsets.UTF_8),Long.parseLong(v[2]),Integer.parseInt(v[3]));expected=v[4];break;
 case "ladon":actual=ProtocolCrypto.ladon(Long.parseLong(v[1]),ProtocolCrypto.hex(v[2]));expected=v[3];break;
 case "sm3":actual=ProtocolCrypto.hex(ProtocolCrypto.sm3(ProtocolCrypto.utf(v[1])));expected=v[2];break;
 case "register":actual=ProtocolCrypto.registerContent(v[1],ProtocolCrypto.hex(v[2]));expected=v[3];break;
 case "key":actual=ProtocolCrypto.hex(ProtocolCrypto.unwrap(v[1],ProtocolCrypto.REGISTER_KEY));expected=v[2];break;
 case "chapter":actual=ProtocolCrypto.hex(MessageDigest.getInstance("SHA-256").digest(ProtocolCrypto.utf(ProtocolCrypto.chapter(v[1],ProtocolCrypto.hex(v[2])))));expected=v[3];break;
 default:throw new AssertionError(v[0]);}if(!actual.equals(expected))throw new AssertionError("Mismatch: "+v[0]+" vector "+n);n++;}System.out.println("PASS: "+n+" native/Python protocol vectors");}
}
