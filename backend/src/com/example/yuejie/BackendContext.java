package com.example.yuejie;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** File-backed upstream identities; Android configuration never enters this service. */
final class BackendContext {
  final Path data,assets;
  final Map<String,BackendPrefs> prefs=new HashMap<>();
  BackendContext(Path data,Path assets)throws IOException{this.data=data;this.assets=assets;Files.createDirectories(data);}
  BackendContext getApplicationBackendContext(){return this;}
  BackendContext getAssets(){return this;}
  InputStream open(String name)throws IOException{return Files.newInputStream(assets.resolve(name));}
  synchronized BackendPrefs getBackendPrefs(String name,int mode){return prefs.computeIfAbsent(name,k->new BackendPrefs(data.resolve(k+".properties")));}
  static void log(String message){/* Never log upstream identities, signed URLs or user content. */}
}
final class BackendPrefs {
  final Path path;final Properties values=new Properties();
  BackendPrefs(Path p){path=p;try{if(Files.exists(p))try(InputStream in=Files.newInputStream(p)){values.load(in);}}catch(IOException e){throw new UncheckedIOException(e);}}
  synchronized String getString(String key,String fallback){return values.getProperty(key,fallback);}
  synchronized long getLong(String key,long fallback){return Long.parseLong(values.getProperty(key,Long.toString(fallback)));}
  Editor edit(){return new Editor();}
  final class Editor {
    final Map<String,String> changes=new HashMap<>();
    Editor putString(String k,String v){changes.put(k,v);return this;}
    Editor putLong(String k,long v){return putString(k,Long.toString(v));}
    Editor remove(String k){changes.put(k,null);return this;}
    boolean commit(){synchronized(BackendPrefs.this){Properties next=new Properties();next.putAll(values);changes.forEach((k,v)->{if(v==null)next.remove(k);else next.setProperty(k,v);});try{Path tmp=path.resolveSibling(path.getFileName()+".tmp");try(OutputStream out=Files.newOutputStream(tmp)){next.store(out,"Reading Dog backend");}Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);values.clear();values.putAll(next);return true;}catch(IOException e){return false;}}}
    void apply(){if(!commit())throw new IllegalStateException("Cannot save upstream configuration");}
  }
}
