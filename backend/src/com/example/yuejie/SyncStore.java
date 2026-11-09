package com.example.yuejie;

import java.nio.file.*;
import java.sql.*;
import org.json.*;

/** One private library. A transaction atomically deduplicates, merges and advances the cursor. */
final class SyncStore implements AutoCloseable {
  final Connection db;
  SyncStore(Path path)throws Exception{
    db=DriverManager.getConnection("jdbc:sqlite:"+path);
    try(Statement s=db.createStatement()){
      s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA busy_timeout=5000");
      s.execute("CREATE TABLE IF NOT EXISTS state(kind TEXT,book TEXT,value TEXT NOT NULL,at INTEGER NOT NULL,op TEXT NOT NULL,rev INTEGER NOT NULL,PRIMARY KEY(kind,book))");
      s.execute("CREATE TABLE IF NOT EXISTS seen(op TEXT PRIMARY KEY)");
      s.execute("CREATE TABLE IF NOT EXISTS metadata(id INTEGER PRIMARY KEY CHECK(id=1),revision INTEGER NOT NULL)");
      s.execute("INSERT OR IGNORE INTO metadata VALUES(1,0)");
    }
  }
  synchronized JSONObject exchange(JSONObject request)throws Exception{
    long cursor=request.optLong("cursor",0),now=System.currentTimeMillis();JSONArray ops=request.optJSONArray("operations");if(ops==null)ops=new JSONArray();
    if(cursor<0||ops.length()>200)throw new IllegalArgumentException("同步批次无效");
    JSONArray ack=new JSONArray();db.setAutoCommit(false);
    try{
      long rev;try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT revision FROM metadata WHERE id=1")){rev=r.getLong(1);}
      if(cursor>rev)cursor=0; // A restored server backup requires a complete state pull.
      for(int i=0;i<ops.length();i++){
        JSONObject o=ops.getJSONObject(i);String id=o.getString("id"),kind=o.getString("kind"),book=o.getString("book");
        if(!id.matches("[a-zA-Z0-9-]{16,80}")||!kind.matches("shelf|progress"))throw new IllegalArgumentException("同步操作无效");NovelApi.id(book);
        long at=o.getLong("at");if(at<0||at>now+300000)throw new IllegalArgumentException("设备时间异常，请重新同步");
        JSONObject value=o.getJSONObject("value");validate(kind,book,value);
        boolean fresh;try(PreparedStatement p=db.prepareStatement("INSERT OR IGNORE INTO seen VALUES(?)")){p.setString(1,id);fresh=p.executeUpdate()>0;}
        ack.put(id);if(!fresh)continue;
        long oldAt=-1;String oldOp="";try(PreparedStatement p=db.prepareStatement("SELECT at,op FROM state WHERE kind=? AND book=?")){p.setString(1,kind);p.setString(2,book);try(ResultSet r=p.executeQuery()){if(r.next()){oldAt=r.getLong(1);oldOp=r.getString(2);}}}
        if(at<oldAt||(at==oldAt&&id.compareTo(oldOp)<=0))continue;
        try(PreparedStatement p=db.prepareStatement("INSERT INTO state VALUES(?,?,?,?,?,?) ON CONFLICT(kind,book) DO UPDATE SET value=excluded.value,at=excluded.at,op=excluded.op,rev=excluded.rev")){
          p.setString(1,kind);p.setString(2,book);p.setString(3,value.toString());p.setLong(4,at);p.setString(5,id);p.setLong(6,++rev);p.executeUpdate();
        }
      }
      try(PreparedStatement p=db.prepareStatement("UPDATE metadata SET revision=? WHERE id=1")){p.setLong(1,rev);p.executeUpdate();}
      JSONArray changes=new JSONArray();long next=cursor;
      try(PreparedStatement p=db.prepareStatement("SELECT kind,book,value,at,op,rev FROM state WHERE rev>? ORDER BY rev LIMIT 200")){
        p.setLong(1,cursor);try(ResultSet r=p.executeQuery()){while(r.next()){next=r.getLong(6);changes.put(new JSONObject().put("kind",r.getString(1)).put("book",r.getString(2)).put("value",new JSONObject(r.getString(3))).put("at",r.getLong(4)).put("id",r.getString(5)));}}
      }
      db.commit();return new JSONObject().put("ack",ack).put("changes",changes).put("cursor",next).put("more",next<rev).put("server_time",now);
    }catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
  }
  static void validate(String kind,String book,JSONObject v){
    if(v.toString().length()>65536)throw new IllegalArgumentException("同步数据过大");
    if(kind.equals("shelf")){boolean present=v.getBoolean("present");if(present){JSONObject b=v.getJSONObject("book");if(!book.equals(b.getString("book_id"))||b.optString("book_name",b.optString("title")).isBlank())throw new IllegalArgumentException("书籍信息无效");}}
    else{NovelApi.id(v.getString("chapter"));if(v.getInt("offset")<0||v.getInt("index")<0||v.getInt("total")<1)throw new IllegalArgumentException("阅读位置无效");}
  }
  synchronized void backup(Path target)throws Exception{try(Statement s=db.createStatement()){s.execute("VACUUM INTO '"+target.toString().replace("'","''")+"'");}}
  public void close()throws SQLException{db.close();}
}
