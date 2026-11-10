package com.example.yuejie;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.util.concurrent.*;
public final class ChapterTrialChecks extends Instrumentation {
 ReaderActivity a;int checks;
 String labels(android.view.View v){String out=v instanceof android.widget.TextView?((android.widget.TextView)v).getText().toString()+"|":"";if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)out+=labels(g.getChildAt(i));}return out;}

 void ui(Runnable r){runOnMainSync(r);}void ok(boolean b,String label){if(!b)throw new AssertionError(label);checks++;Bundle x=new Bundle();x.putString("stream","PASS "+label+"\n");sendStatus(0,x);}
 JSONObject data(String id){try{return new JSONObject().put("id",id).put("title","fixture").put("text","测试正文。\n\n重复分页内容。\n\n");}catch(Exception e){throw new RuntimeException(e);}}
 void settle(){SystemClock.sleep(650);waitForIdleSync();}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
 a=(ReaderActivity)startActivitySync(new Intent(getTargetContext(),ReaderActivity.class).putExtra("book_id","isolated-fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
 ui(()->{try{a.directory=new JSONArray().put(new JSONObject().put("id","a")).put(new JSONObject().put("id","b"));}catch(Exception e){throw new RuntimeException(e);}a.stopEntryDirectoryRefresh();a.entryDirectoryRequested=true;a.settings.einkStable=true;a.settings.comments=false;a.width=600;a.height=900;a.source=(book,id)->data(id);a.directorySource=(book,force)->a.directory;a.prefetcher.cancel();a.finishOpen(0,0,data("a"),++a.loadToken);});settle();
 ok(a.surface.frame!=null&&a.index==0,"initial page commits");
 CountDownLatch gate=new CountDownLatch(1);a.typesetter.execute(()->{try{gate.await(5,TimeUnit.SECONDS);}catch(Exception e){}});
 ui(()->a.finishOpen(1,0,data("b"),++a.loadToken));
 ok(a.index==0&&a.chapter.optString("id").equals("a")&&a.surface.frame.id.equals("a"),"pending layout preserves displayed chapter and frame");
 gate.countDown();settle();ok(a.index==1&&a.surface.frame.id.equals("b")&&!a.busy,"layout commits target atomically");
 CountDownLatch stale=new CountDownLatch(1);a.typesetter.execute(()->{try{stale.await(5,TimeUnit.SECONDS);}catch(Exception e){}});
 ui(()->{a.finishOpen(0,0,data("a"),++a.loadToken);a.loadToken++;a.pendingChapter=null;a.busy=true;});stale.countDown();settle();
 ok(a.index==1&&a.surface.frame.id.equals("b")&&a.busy,"stale layout cannot commit or release newer busy");
 ui(()->{a.busy=false;a.source=(book,id)->{Thread.sleep(900);return data(id);};a.chapters.remove(0);a.layouts.remove(0);a.open(0,0);});SystemClock.sleep(100);ok(a.notice==null||a.notice.getVisibility()!=0,"short wait has no loading notice");SystemClock.sleep(400);waitForIdleSync();ok(a.notice.getVisibility()==0&&a.surface.frame.id.equals("b"),"long wait shows static notice over old page");settle();ok(a.index==0&&!a.busy&&!a.notice.getText().toString().contains("正在加载章节"),"slow source commits once and clears notice");
 ui(()->{a.chapters.remove(1);a.layouts.remove(1);a.source=(book,id)->{throw new java.io.IOException("controlled");};a.open(1,0);});settle();ok(a.index==0&&!a.busy&&a.surface.frame.id.equals("a")&&a.retry!=null,"source failure preserves displayed state and retry");
 ui(()->{a.source=(book,id)->data(id);a.retry.run();});settle();ok(a.index==1&&a.surface.frame.id.equals("b"),"retry commits correct chapter");
 ui(()->{a.pendingEntryDirectory=new JSONArray().put(a.directory.optJSONObject(0)).put(a.directory.optJSONObject(1)).put(data("c"));a.displayFrame(a.surface.frame,false);});ok(a.directory.length()==3&&a.surface.frame.totalChapters==3,"directory append merges into final frame");
 ui(()->{a.foreground=false;a.finishOpen(0,0,data("a"),++a.loadToken);});settle();ok(a.index==1&&a.deferredLayout,"background layout does not replace displayed page");ui(()->{a.foreground=true;a.reflow();});settle();ok(a.index==0&&!a.busy,"returning foreground commits pending page");
 ui(()->a.settings.prefs.edit().putBoolean("eink_stable_override",true).putBoolean("eink_delayed_loading",true).putInt("eink_loading_delay",500).putBoolean("eink_fast",true).putBoolean("eink_periodic",true).commit());
 ReaderSettings phone=new ReaderSettings(getTargetContext());ok(!phone.einkStable&&phone.loadingNoticeDelay()==0,"ordinary device ignores all legacy experiments");
 ok(a.settings.loadingNoticeDelay()==300,"eink delay fixed at 300 despite legacy 500 preference");
 String[] options=new String[2];ui(()->{a.panel=new android.widget.LinearLayout(a);a.settings.einkStable=true;a.pageTurnOptions();options[0]=labels(a.panel);a.panel.removeAllViews();a.settings.einkStable=false;a.pageTurnOptions();options[1]=labels(a.panel);});ok(options[0].equals(options[1])&&options[0].contains("翻页")&&!options[0].contains("稳定"),"identical page-turn settings on phone and eink");
 ui(()->{a.busy=false;a.chapters.remove(1);a.layouts.remove(1);a.source=(book,id)->{Thread.sleep(900);return data(id);};a.open(1,0);});ok(a.notice.getVisibility()==0&&a.notice.getText().toString().contains("正在加载章节"),"phone loading notice is immediate");settle();settle();
 out.putString("stream","PASS "+checks+" chapter transaction checks\n");finish(-1,out);
 }catch(Throwable e){out.putString("stream",android.util.Log.getStackTraceString(e));finish(0,out);}finally{if(a!=null)ui(()->a.finish());}}
}
