package com.example.yuejie;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import static com.example.yuejie.PaperUi.*;

/** One local-library home. Search is an inline state, never a second home tab. */
public class MainActivity extends Activity {
  static final int HOME_PAPER=0xfff5f1e8,HOME_INK=0xff252820,HOME_MUTED=0xff626557,HOME_RULE=0xffdedbd1;
  LocalLibrary library;NovelApi api;LinearLayout root,list;ScrollView scroll;EditText input;ImageView clear;
  ShelfRefresher shelfRefresher;TextView refreshStatus;boolean shelfRefreshing;String shelfRefreshMessage="";int refreshGeneration;
  final List<Book> results=new ArrayList<>();
  int generation=0,offset=0;
  String query="",searchId="",passback="0",error="";
  boolean searching=false,more=false,loading=false;
  int d(float value){return dp(this,value);}

  @Override public void onCreate(Bundle state){
    super.onCreate(state);library=new LocalLibrary(this);api=new NovelApi(this,library);
    getWindow().setStatusBarColor(HOME_PAPER);getWindow().setNavigationBarColor(HOME_PAPER);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    shelfRefresher=new ShelfRefresher(library,book->{Exception problem=null;try{api.directory(book.id,true);}catch(Exception e){problem=e;}try{api.detail(book.id);}catch(Exception e){if(problem==null)problem=e;}if(problem!=null)throw problem;});buildHome();
    if(state!=null){String saved=state.getString("query","");input.setText(saved);if(state.getBoolean("searching",false)&&!saved.isEmpty())submit();}
  }
  final Runnable syncChanged=()->{if(list!=null&&!isFinishing()&&!isDestroyed())render();};
  @Override protected void onPause(){SyncEngine.listeners.remove(syncChanged);super.onPause();}
  @Override protected void onResume(){super.onResume();SyncEngine.listeners.add(syncChanged);if(list!=null){render();refreshShelf(false);}}
  @Override protected void onDestroy(){generation++;if(shelfRefresher!=null)shelfRefresher.close();super.onDestroy();}
  @Override protected void onSaveInstanceState(Bundle state){state.putString("query",input.getText().toString());state.putBoolean("searching",searching);super.onSaveInstanceState(state);}

  void buildHome(){
    root=col(this);root.setBackgroundColor(HOME_PAPER);root.setFitsSystemWindows(true);setContentView(root);
    LinearLayout searchBlock=col(this);searchBlock.setPadding(d(16),d(8),d(16),0);
    LinearLayout bar=row(this);bar.setContentDescription("搜索区域");
    ImageView search=icon(this,"搜索",0xff929287,30,()->submit());search.setPadding(0,d(7),d(10),d(7));search.setContentDescription("提交搜索");bar.addView(search);
    input=new EditText(this);input.setSingleLine();input.setTextSize(16);input.setTextColor(HOME_INK);input.setHintTextColor(HOME_MUTED);input.setHint("搜索书名、作者");input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(0,0,0,0);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.setContentDescription("搜索书名或作者");bar.addView(input,new LinearLayout.LayoutParams(0,d(44),1));
    clear=icon(this,"关闭",0xff929287,30,()->clearSearch());clear.setContentDescription("清空搜索");clear.setVisibility(View.GONE);bar.addView(clear);
    ImageView about=icon(this,"关于阅读狗",0xff536951,44,()->{hideKeyboard();startActivity(new Intent(this,AboutActivity.class));});bar.addView(about);
    input.setOnEditorActionListener((view,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_UP)){submit();return true;}return false;});
    input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void afterTextChanged(Editable text){}public void onTextChanged(CharSequence text,int start,int before,int count){clear.setVisibility(text.length()==0?View.GONE:View.VISIBLE);if(searching&&!text.toString().trim().equals(query)){generation++;loading=false;searching=false;more=false;results.clear();error="";query="";render();}}});
    searchBlock.addView(bar);View rule=new View(this);rule.setBackgroundColor(HOME_RULE);searchBlock.addView(rule,new LinearLayout.LayoutParams(-1,d(1)));root.addView(searchBlock);
    scroll=new RefreshScrollView(this,new RefreshScrollView.Host(){
      public boolean enabled(){return !searching&&!shelfRefreshing&&!library.books(true).isEmpty();}
      public void distance(float distance){if(refreshStatus!=null)refreshStatus.setText(distance>=72?"松开刷新":"下拉刷新");}
      public void released(boolean refresh){if(refresh)refreshShelf(true);else updateRefreshStatus();}
    });
    scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
    list=col(this);list.setPadding(d(16),d(12),d(16),d(16));scroll.addView(list);
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));render();
  }
  void updateRefreshStatus(){
    if(refreshStatus==null)return;
    refreshStatus.setText(shelfRefreshing?"更新中…":shelfRefreshMessage.isEmpty()?"刷新 ↻":shelfRefreshMessage);
    refreshStatus.setEnabled(!shelfRefreshing);
    refreshStatus.setContentDescription(shelfRefreshing?"正在更新书架":shelfRefreshMessage.isEmpty()?"刷新书架":shelfRefreshMessage+"，点击重新刷新");
  }
  void refreshShelf(boolean force){if(shelfRefresher==null||shelfRefreshing)return;shelfRefreshMessage="";final int refreshToken=++refreshGeneration;boolean started=shelfRefresher.refresh(force,new ShelfRefresher.Listener(){public void changed(){if(!isDestroyed()&&!isFinishing())render();}public void complete(int updated,int failed){if(isDestroyed()||isFinishing())return;shelfRefreshing=false;shelfRefreshMessage=failed>0?failed+" 本失败 · 重试":"已更新";render();if(failed==0)list.postDelayed(()->{if(!isDestroyed()&&!shelfRefreshing&&refreshToken==refreshGeneration){shelfRefreshMessage="";updateRefreshStatus();}},2500);}});shelfRefreshing=started;updateRefreshStatus();}
  void addProgress(LinearLayout parent,Book book,JSONObject position){LinearLayout line=row(this);TextView progress=bodyLabel(progressLabel(book,position),14,HOME_MUTED);line.addView(progress,new LinearLayout.LayoutParams(0,-2,1));parent.addView(line);LinearLayout updates=row(this);addUpdateBadge(updates,book,position);if(updates.getChildCount()>0){gap(parent,6);parent.addView(updates);}}
  void addUpdateBadge(LinearLayout parent,Book book,JSONObject position){int count=library.catalogStatus(book,position).newChapters;if(count<=0)return;TextView badge=bodyLabel("有新章 · "+count,14,HOME_INK);badge.setPadding(d(7),d(3),d(7),d(3));badge.setBackground(bg(this,0xffe4e9dd,5));badge.setContentDescription(book.title+"，新增 "+count+" 章");parent.addView(badge);}
  void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();}
  void clearSearch(){generation++;loading=false;searching=false;more=false;query="";error="";searchId="";passback="0";results.clear();input.setText("");hideKeyboard();render();scroll.scrollTo(0,0);}
  void submit(){String keyword=input.getText().toString().trim();hideKeyboard();if(keyword.isEmpty()){clearSearch();return;}query=keyword;searching=true;generation++;offset=0;searchId="";passback="0";results.clear();more=false;loading=false;error="";scroll.scrollTo(0,0);fetch(false);}
  void fetch(boolean next){
    if(loading)return;final int token=generation;final String keyword=query;final int requestedOffset=next?offset:0;final String requestedId=next?searchId:null,requestedPassback=next?passback:"0";
    loading=true;error="";render();NovelApi.async(()->api.search(keyword,requestedOffset,requestedId,requestedPassback),(page,err)->{
      if(isFinishing()||isDestroyed()||token!=generation||!searching||!keyword.equals(query))return;
      loading=false;if(err!=null)error=err;else{Set<String> ids=new HashSet<>();for(Book book:results)ids.add(book.id);for(Book book:page.books)if(ids.add(book.id))results.add(book);offset=page.next;searchId=page.searchId;passback=page.passback;more=page.more;}render();
    });
  }
  void divider(LinearLayout parent){View line=new View(this);line.setBackgroundColor(HOME_RULE);parent.addView(line,new LinearLayout.LayoutParams(-1,d(1)));}
  TextView bodyLabel(String text,float size,int color){return PaperUi.text(this,text,size,color);}
  TextView homeButton(String label,Runnable action){TextView t=button(this,label,action);t.setTextSize(16);return t;}
  void render(){if(list==null)return;refreshStatus=null;int y=scroll.getScrollY();list.removeAllViews();if(searching)renderResults();else renderLibrary();updateRefreshStatus();scroll.post(()->{if(!isDestroyed())scroll.scrollTo(0,y);});}

  void renderLibrary(){
    List<Book> books=library.books(true);
    Book last=null;for(Book book:books)if(library.progress(book.id)!=null){last=book;break;}
    if(last!=null){
      Book book=last;JSONObject position=library.progress(book.id);
      LinearLayout resume=row(this);resume.setPadding(d(12),d(10),d(12),d(10));resume.setMinimumHeight(d(64));
      resume.setBackground(bg(this,0xffe8ebdf,6));
      resume.addView(icon(this,"书签",0xff445e3c,24,null));
      LinearLayout copy=col(this);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=d(10);resume.addView(copy,cp);
      LinearLayout titleLine=row(this);TextView label=bodyLabel("继续读",12,0xff445e3c);titleLine.addView(label);
      TextView title=compactTitle(book.title);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=d(8);titleLine.addView(title,tp);copy.addView(titleLine);
      gap(copy,3);copy.addView(compactMetadata(position.optString("title")+" · "+compactProgress(book,position)));
      resume.addView(icon(this,"下一页",0xff445e3c,24,null));
      resume.setOnClickListener(v->read(book));resume.setContentDescription("继续阅读："+book.title+"，"+position.optString("title"));
      list.addView(resume,new LinearLayout.LayoutParams(-1,-2));gap(list,4);
    }
    LinearLayout shelfHeading=row(this);TextView title=bodyLabel("书架",18,HOME_INK);title.setTypeface(null,Typeface.BOLD);shelfHeading.addView(title);
    TextView count=bodyLabel(books.size()+" 本",12,HOME_MUTED);LinearLayout.LayoutParams countParams=new LinearLayout.LayoutParams(0,-2,1);countParams.leftMargin=d(8);shelfHeading.addView(count,countParams);
    refreshStatus=bodyLabel("",12,HOME_MUTED);refreshStatus.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);refreshStatus.setSingleLine();refreshStatus.setEllipsize(TextUtils.TruncateAt.END);
    refreshStatus.setOnClickListener(v->refreshShelf(true));refreshStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    shelfHeading.addView(refreshStatus,new LinearLayout.LayoutParams(d(128),d(44)));list.addView(shelfHeading);
    if(books.isEmpty()){
      TextView empty=bodyLabel("书架还是空的，搜索一本喜欢的书。",14,HOME_MUTED);empty.setPadding(0,d(16),0,d(20));
      empty.setOnClickListener(v->{input.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);});list.addView(empty);
    }
    for(Book book:books)list.addView(libraryRow(book));
  }
  TextView compactTitle(String value){TextView title=bodyLabel(value,16,HOME_INK);title.setTypeface(null,Typeface.BOLD);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);return title;}
  TextView compactMetadata(String value){TextView text=bodyLabel(value,12,HOME_MUTED);text.setSingleLine();text.setEllipsize(TextUtils.TruncateAt.END);return text;}
  String compactProgress(Book book,JSONObject position){
    LocalLibrary.CatalogStatus status=library.catalogStatus(book,position);
    if(position==null)return status.total>0?"未读 · 共 "+status.total+" 章":"未读";
    int current=Math.max(1,status.current);
    return status.total>0?Math.min(current,status.total)+" / "+status.total+" 章":"第 "+current+" 章";
  }
  View libraryRow(Book book){
    LinearLayout wrapper=col(this);divider(wrapper);
    LinearLayout row=row(this);row.setPadding(0,d(8),0,d(8));row.setMinimumHeight(d(63));row.addView(cover(this,book,32));
    LinearLayout copy=col(this);LinearLayout.LayoutParams info=new LinearLayout.LayoutParams(0,-2,1);info.leftMargin=d(12);row.addView(copy,info);
    LinearLayout titleLine=row(this);titleLine.addView(compactTitle(book.title),new LinearLayout.LayoutParams(0,-2,1));
    JSONObject position=library.progress(book.id);int added=library.catalogStatus(book,position).newChapters;
    if(added>0){TextView badge=bodyLabel("新 "+added+" 章",11,0xff445e3c);badge.setSingleLine();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.leftMargin=d(8);titleLine.addView(badge,bp);}
    copy.addView(titleLine);gap(copy,3);
    String author=book.author.isEmpty()?"作者未知":book.author;
    String chapter=position==null?"":position.optString("title");
    copy.addView(compactMetadata(author+" · "+compactProgress(book,position)+(chapter.isEmpty()?"":" · "+chapter)));
    row.setOnClickListener(v->read(book));row.setContentDescription(book.title+"，"+author+"，"+compactProgress(book,position)+"，"+chapter+(added>0?"，新增 "+added+" 章":""));
    row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(book.title).setItems(new String[]{"书籍详情","移出书架"},(dialog,n)->{if(n==0)details(book);else{library.remove(book.id);render();}}).show();return true;});
    wrapper.addView(row);return wrapper;
  }
  void renderResults(){
    LinearLayout heading=row(this);TextView title=bodyLabel("搜索结果",20,HOME_INK);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));heading.addView(bodyLabel(results.size()+" 本",14,HOME_MUTED));list.addView(heading);gap(list,15);
    for(Book book:results){LinearLayout row=row(this);row.setPadding(0,d(12),0,d(12));row.addView(cover(this,book,35));LinearLayout info=col(this);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.leftMargin=d(13);params.rightMargin=d(10);row.addView(info,params);TextView name=bodyLabel(book.title,18,HOME_INK);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);info.addView(name);gap(info,7);TextView author=bodyLabel(book.author,14,HOME_MUTED);author.setSingleLine();author.setEllipsize(TextUtils.TruncateAt.END);info.addView(author);row.addView(icon(this,"下一页",0xff929287,18,null));row.setOnClickListener(v->details(book));row.setContentDescription(book.title+"，"+book.author);list.addView(row);divider(list);}
    if(loading){gap(list,18);list.addView(bodyLabel("正在搜索…",14,HOME_MUTED));}else if(!error.isEmpty()){gap(list,18);TextView message=bodyLabel(error,14,HOME_MUTED);message.setLineSpacing(d(4),1);list.addView(message);gap(list,12);list.addView(homeButton("重试",()->fetch(!results.isEmpty())));}else if(results.isEmpty()){gap(list,18);list.addView(bodyLabel("没有找到相关书籍，换个词试试。",16,HOME_MUTED));}
    if(more&&!loading){gap(list,16);list.addView(homeButton("加载更多",()->fetch(true)));}
  }
  TextView authorLabel(Book book,int size){TextView author=bodyLabel(book.author.isEmpty()?"作者未知":book.author,size,HOME_MUTED);author.setSingleLine();author.setEllipsize(TextUtils.TruncateAt.END);author.setContentDescription("作者："+(book.author.isEmpty()?"未知":book.author));return author;}
  String progressLabel(Book book,JSONObject position){LocalLibrary.CatalogStatus status=library.catalogStatus(book,position);int total=status.total;if(position==null)return total>0?"尚未阅读 · 共 "+total+" 章":"尚未阅读";int current=Math.max(1,status.current);if(total>0)return "目录 "+Math.min(current,total)+" / "+total+" 章";return "目录第 "+current+" 章 · 总数待更新";}
  double progressFraction(String book,JSONObject position){double partial=0;JSONObject cached=library.chapter(book,position.optString("chapter"));if(cached!=null)partial=(double)position.optInt("offset")/Math.max(1,cached.optString("text").length());return Math.max(0,Math.min(1,(position.optInt("index")+partial)/Math.max(1,library.catalogStatus(library.book(book),position).total)));}
  void details(Book book){startActivity(new Intent(this,DetailActivity.class).putExtra("book",book.raw.toString()));}
  void read(Book book){startActivity(new Intent(this,ReaderActivity.class).putExtra("book_id",book.id).putExtra("book_name",book.title));}
  @Override public void onBackPressed(){if(searching||input.length()>0)clearSearch();else super.onBackPressed();}
}
