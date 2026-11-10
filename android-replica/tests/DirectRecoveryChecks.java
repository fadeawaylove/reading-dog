package com.example.yuejie;
import android.content.*;import java.util.*;import org.json.*;
/** Exercises restored Android protocol identity and bounded recovery. */
public final class DirectRecoveryChecks {
 int checks;
 void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);checks++;}
 void record(String label,Object value){}
 final Context ctx;DirectRecoveryChecks(Context c){ctx=c;}
 JSONObject fakeProfile(NovelApi client,String iid)throws Exception{JSONObject p=client.asset("content-defaults.json");JSONArray a=new JSONArray().put(new JSONArray().put("iid").put(iid)).put(new JSONArray().put("device_id").put("987654321001"));JSONArray original=p.getJSONArray("query_defaults");for(int i=0;i<original.length();i++)a.put(original.get(i));p.put("query_defaults",a);return p;}
 void contentRecovery()throws Exception{
 // Each run must reach the transport, even after a previous successful run.
 String book="99"+Long.toString(android.os.SystemClock.elapsedRealtimeNanos());
 SharedPreferences profiles=ctx.getSharedPreferences("api_profiles",0);LocalLibrary db=new LocalLibrary(ctx);NovelApi helper=new NovelApi(ctx,db);JSONObject original=fakeProfile(helper,"1111");String savedSearch="search-kept";profiles.edit().remove("content_reinitialized_at").putString("content",original.toString()).putString("search",savedSearch).commit();
 final int[] registrations={0},keys={0},batches={0};final boolean[] alwaysEmpty={false},registerEmpty={false};byte[] key=ProtocolCrypto.hex("000102030405060708090a0b0c0d0e0f"),iv=new byte[16];
 NovelApi.Transport wire=(url,headers,body)->{
 if(url.contains("device_register")){registrations[0]++;return new JSONObject().put("install_id_str",Integer.toString(2222+registrations[0])).put("device_id_str","987654321001").toString();}
 if(url.contains("registerkey")){keys[0]++;if(registerEmpty[0]){registerEmpty[0]=false;throw new NovelApi.EmptyResponse();}return new JSONObject().put("code",0).put("data",new JSONObject().put("key",ProtocolCrypto.b64(ProtocolCrypto.cat(iv,ProtocolCrypto.aes(key,ProtocolCrypto.REGISTER_KEY,iv,false))))).toString();}
 if(url.contains("batch_full")){batches[0]++;if(alwaysEmpty[0]||url.contains("iid=1111&"))throw new NovelApi.EmptyResponse();String cid=url.substring(url.indexOf("item_ids=")+9).split("&")[0];String content=ProtocolCrypto.b64(ProtocolCrypto.cat(iv,ProtocolCrypto.aes(ProtocolCrypto.utf("<p>恢复后的正文。</p>"),key,iv,false)));return new JSONObject().put("code",0).put("data",new JSONObject().put(cid,new JSONObject().put("title","测试章节").put("content",content))).toString();}
 throw new AssertionError("Unexpected transport path");};
 NovelApi client=new NovelApi(ctx,db,wire);JSONObject result=client.chapter(book,"111");ok(result.getString("text").equals("恢复后的正文。"),"empty content recovered");ok(registrations[0]==1&&keys[0]==2&&batches[0]==2,"one initialization and one retry");ok(profiles.getString("search","").equals(savedSearch),"content refresh leaves search intact");record("content_empty_response_recovered_once",true);
 int before=registrations[0];ok(client.refreshContentProfile(original),"concurrent old profile reuses newer identity");ok(registrations[0]==before,"no duplicate initialization");
 NovelApi stale=new NovelApi(ctx,db,wire);stale.sessionKey=key;stale.keyIdentity="1111";int oldKeys=keys[0];stale.chapter(book,"222");ok(keys[0]==oldKeys+1&&!stale.keyIdentity.equals("1111"),"stale client rebinds key to new identity");record("content_identity_key_binding",true);
 profiles.edit().putString("content",original.toString()).remove("content_reinitialized_at").commit();alwaysEmpty[0]=true;registrations[0]=0;keys[0]=0;batches[0]=0;try{new NovelApi(ctx,db,wire).chapter(book,"333");throw new AssertionError("expected persistent empty failure");}catch(NovelApi.EmptyResponse expected){}ok(registrations[0]==1&&batches[0]==2,"persistent failure bounded to one retry");int oldBatches=batches[0];try{new NovelApi(ctx,db,wire).chapter(book,"444");throw new AssertionError("expected cooldown failure");}catch(NovelApi.EmptyResponse expected){}ok(registrations[0]==1&&batches[0]==oldBatches+1,"cooldown prevents repeated initialization");record("content_recovery_bounded_and_rate_limited",true);
 alwaysEmpty[0]=false;registerEmpty[0]=true;profiles.edit().putString("content",original.toString()).remove("content_reinitialized_at").commit();registrations[0]=0;new NovelApi(ctx,db,wire).chapter(book,"555");ok(registrations[0]==1,"registerkey empty recovers");record("registerkey_empty_response_recovered",true);db.getWritableDatabase().delete("chapters","book=?",new String[]{book});db.close();profiles.edit().clear().commit();
 }
}
