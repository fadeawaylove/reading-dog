package com.example.yuejie;
import java.nio.file.*;import java.util.*;import org.json.*;
/** Migrated Android protocol recovery checks; runs against the actual backend implementation. */
public final class BackendRecoveryChecks {
 int checks;
 void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);checks++;}
 void record(String label,Object value){}
 public static void main(String[] args)throws Exception{BackendRecoveryChecks c=new BackendRecoveryChecks();c.contentRecovery();System.out.println("PASS "+c.checks+" backend upstream recovery checks");}
 JSONObject fakeProfile(NovelApi client,String iid)throws Exception{JSONObject p=client.asset("content-defaults.json");JSONArray a=new JSONArray().put(new JSONArray().put("iid").put(iid)).put(new JSONArray().put("device_id").put("987654321001"));JSONArray original=p.getJSONArray("query_defaults");for(int i=0;i<original.length();i++)a.put(original.get(i));p.put("query_defaults",a);return p;}
 void contentRecovery()throws Exception{
 BackendContext ctx=new BackendContext(Files.createTempDirectory("reading-dog-recovery-"),Path.of("backend/assets"));BackendPrefs profiles=ctx.getBackendPrefs("api_profiles",0);LocalLibrary db=new LocalLibrary();NovelApi helper=new NovelApi(ctx,db);JSONObject original=fakeProfile(helper,"1111");String savedSearch="search-kept";profiles.edit().putString("content",original.toString()).putString("search",savedSearch).commit();
 final int[] registrations={0},keys={0},batches={0};final boolean[] alwaysEmpty={false},registerEmpty={false};byte[] key=ProtocolCrypto.hex("000102030405060708090a0b0c0d0e0f"),iv=new byte[16];
 NovelApi.Transport wire=(url,headers,body)->{
 if(url.contains("device_register")){registrations[0]++;return new JSONObject().put("install_id_str",Integer.toString(2222+registrations[0])).put("device_id_str","987654321001").toString();}
 if(url.contains("registerkey")){keys[0]++;if(registerEmpty[0]){registerEmpty[0]=false;throw new NovelApi.EmptyResponse();}return new JSONObject().put("code",0).put("data",new JSONObject().put("key",ProtocolCrypto.b64(ProtocolCrypto.cat(iv,ProtocolCrypto.aes(key,ProtocolCrypto.REGISTER_KEY,iv,false))))).toString();}
 if(url.contains("batch_full")){batches[0]++;if(alwaysEmpty[0]||url.contains("iid=1111&"))throw new NovelApi.EmptyResponse();String cid=url.substring(url.indexOf("item_ids=")+9).split("&")[0];String content=ProtocolCrypto.b64(ProtocolCrypto.cat(iv,ProtocolCrypto.aes(ProtocolCrypto.utf("<p>恢复后的正文。</p>"),key,iv,false)));return new JSONObject().put("code",0).put("data",new JSONObject().put(cid,new JSONObject().put("title","测试章节").put("content",content))).toString();}
 throw new AssertionError("Unexpected transport path");};
 NovelApi client=new NovelApi(ctx,db,wire);JSONObject result=client.chapter("123","111");ok(result.getString("text").equals("恢复后的正文。"),"empty content recovered");ok(registrations[0]==1&&keys[0]==2&&batches[0]==2,"one initialization and one retry");ok(profiles.getString("search","").equals(savedSearch),"content refresh leaves search intact");record("content_empty_response_recovered_once",true);
 int before=registrations[0];ok(client.refreshContentProfile(original),"concurrent old profile reuses newer identity");ok(registrations[0]==before,"no duplicate initialization");
 NovelApi stale=new NovelApi(ctx,db,wire);stale.sessionKey=key;stale.keyIdentity="1111";int oldKeys=keys[0];stale.chapter("123","222");ok(keys[0]==oldKeys+1&&!stale.keyIdentity.equals("1111"),"stale client rebinds key to new identity");record("content_identity_key_binding",true);
 profiles.edit().putString("content",original.toString()).remove("content_reinitialized_at").commit();alwaysEmpty[0]=true;registrations[0]=0;keys[0]=0;batches[0]=0;try{new NovelApi(ctx,db,wire).chapter("123","333");throw new AssertionError("expected persistent empty failure");}catch(NovelApi.EmptyResponse expected){}ok(registrations[0]==1&&batches[0]==2,"persistent failure bounded to one retry");int oldBatches=batches[0];try{new NovelApi(ctx,db,wire).chapter("123","444");throw new AssertionError("expected cooldown failure");}catch(NovelApi.EmptyResponse expected){}ok(registrations[0]==1&&batches[0]==oldBatches+1,"cooldown prevents repeated initialization");record("content_recovery_bounded_and_rate_limited",true);
 alwaysEmpty[0]=false;registerEmpty[0]=true;profiles.edit().putString("content",original.toString()).remove("content_reinitialized_at").commit();registrations[0]=0;new NovelApi(ctx,db,wire).chapter("123","555");ok(registrations[0]==1,"registerkey empty recovers");record("registerkey_empty_response_recovered",true);
 }
}
