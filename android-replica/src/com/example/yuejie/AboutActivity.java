package com.example.yuejie;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import static com.example.yuejie.PaperUi.*;

/** Approved warm-paper About and explicit, non-interrupting update flow. */
public class AboutActivity extends Activity implements AppUpdater.Listener {
    static final int PAPER=0xfff5f1e8,INK=0xff000000,MUTED=0xff000000,GREEN=0xff536951,RULE=0xffdedbd1;
    AppUpdater updater;
    TextView action,progressText;
    ProgressBar progress;
    Dialog dialog;
    AppUpdater.State rendered;
    boolean details,permissionPrompt;
    int d(float value){return dp(this,value);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);updater=AppUpdater.get(this);details=state!=null&&state.getBoolean("details");permissionPrompt=state!=null&&state.getBoolean("permission");
        getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        LinearLayout root=col(this);root.setBackgroundColor(PAPER);root.setFitsSystemWindows(true);setContentView(root);
        LinearLayout nav=row(this);nav.setPadding(d(8),d(6),d(22),d(6));nav.addView(icon(this,"返回",INK,48,this::finish));TextView title=text(this,"关于阅读狗",16,INK);nav.addView(title);root.addView(nav);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=col(this);body.setPadding(d(22),0,d(22),d(30));scroll.addView(body);
        LinearLayout brand=col(this);brand.setGravity(Gravity.CENTER);brand.setPadding(0,d(30),0,d(30));
        ImageView logo=new ImageView(this);logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));logo.setContentDescription("阅读狗图标");brand.addView(logo,new LinearLayout.LayoutParams(d(70),d(70)));gap(brand,16);
        TextView name=PaperUi.heading(this,"阅读狗",24);name.setTextColor(INK);name.setGravity(Gravity.CENTER);brand.addView(name);gap(brand,10);TextView slogan=text(this,"把时间留给阅读",11,MUTED);slogan.setGravity(Gravity.CENTER);brand.addView(slogan);body.addView(brand);
        addRow(body,"当前版本","v"+updater.currentVersion,null);
        addRow(body,"项目主页","GitHub　›",()->openPage());gap(body,28);
        action=primary("检查更新",()->{details=true;if(updater.state==AppUpdater.State.AVAILABLE||updater.state==AppUpdater.State.READY||updater.state==AppUpdater.State.DOWNLOADING||updater.state==AppUpdater.State.VERIFYING||updater.state==AppUpdater.State.DOWNLOAD_FAILED){changed();}else updater.check();});body.addView(action);
        TextView foot=text(this,"仅在你点击时检查新版本\n更新提示不会打断阅读",11,MUTED);foot.setGravity(Gravity.CENTER);foot.setLineSpacing(d(6),1);foot.setPadding(0,d(24),0,0);body.addView(foot);
    }
    void addRow(LinearLayout parent,String label,String value,Runnable click){LinearLayout line=row(this);line.setPadding(0,d(20),0,d(20));line.addView(text(this,label,14,INK),new LinearLayout.LayoutParams(0,-2,1));line.addView(text(this,value,11,MUTED));if(click!=null){line.setOnClickListener(v->click.run());line.setContentDescription(label);line.setFocusable(true);}parent.addView(line);View rule=new View(this);rule.setBackgroundColor(RULE);parent.addView(rule,new LinearLayout.LayoutParams(-1,d(1)));}
    TextView primary(String label,Runnable click){TextView button=PaperUi.button(this,label,click);button.setTextColor(0xffffffff);button.setBackground(bg(this,GREEN,10));button.setMinHeight(d(48));return button;}
    TextView secondary(String label,Runnable click){TextView button=PaperUi.button(this,label,click);button.setTextColor(MUTED);button.setBackgroundColor(android.graphics.Color.TRANSPARENT);return button;}
    @Override protected void onResume(){super.onResume();updater.observe(this);}
    @Override protected void onPause(){updater.remove(this);closeDialog();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("details",details);state.putBoolean("permission",permissionPrompt);super.onSaveInstanceState(state);}
    void closeDialog(){if(dialog!=null){dialog.dismiss();dialog=null;}rendered=null;progress=null;progressText=null;}
    void hideDetails(){details=false;permissionPrompt=false;closeDialog();}
    @Override public void changed(){
        if(isFinishing()||isDestroyed())return;
        String label="检查更新";
        switch(updater.state){case CHECKING:label="正在检查更新…";break;case AVAILABLE:label="发现新版本 · 查看更新";break;case DOWNLOADING:label="正在下载 · 查看进度";break;case VERIFYING:label="正在校验安装包…";break;case READY:label="更新已就绪 · 立即安装";break;case DOWNLOAD_FAILED:label="下载未完成 · 重试";break;default:break;}
        action.setText(label);action.setEnabled(updater.state!=AppUpdater.State.CHECKING);
        if(details&&updater.state!=AppUpdater.State.IDLE){
            if(dialog==null||rendered!=updater.state)showState();
            updateProgress();
        }
    }
    void updateProgress(){if(progress!=null&&updater.release!=null){progress.setProgress((int)Math.min(100,updater.received*100/updater.release.size));progressText.setText(UpdateRelease.bytes(updater.received)+" / "+UpdateRelease.bytes(updater.release.size)+"　　"+progress.getProgress()+"%");}}
    void showState(){
        closeDialog();rendered=updater.state;
        dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=col(this);box.setPadding(d(24),d(26),d(24),d(12));box.setBackground(bg(this,PAPER,18));
        ScrollView outer=new ScrollView(this);outer.setFillViewport(false);outer.addView(box);
        dialog.setContentView(outer);dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->{details=false;permissionPrompt=false;});
        UpdateRelease r=updater.release;
        switch(updater.state){
            case CHECKING:
                heading(box,"正在检查更新");description(box,"正在获取最新版本信息，请稍候。");ProgressBar spinner=new ProgressBar(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(d(32),d(32));sp.gravity=Gravity.CENTER;sp.bottomMargin=d(18);box.addView(spinner,sp);box.addView(secondary("取消",()->{updater.cancel();hideDetails();}));break;
            case LATEST:
                heading(box,"已是最新版本");description(box,"当前版本 v"+updater.currentVersion+"，安心继续阅读。");box.addView(primary("知道了",this::hideDetails));break;
            case AVAILABLE:
                box.addView(text(this,"有新版本可用",11,INK));gap(box,12);heading(box,"阅读狗 v"+r.version);
                description(box,"v"+updater.currentVersion+" → v"+r.version+" · "+UpdateRelease.bytes(r.size)+(r.date.length()>=10?"\n"+r.date.substring(0,10):""));
                View rule=new View(this);rule.setBackgroundColor(RULE);box.addView(rule,new LinearLayout.LayoutParams(-1,d(1)));gap(box,18);TextView sub=text(this,"这次更新",13,INK);sub.setTypeface(null,Typeface.BOLD);box.addView(sub);gap(box,10);
                ScrollView notes=new ScrollView(this);TextView copy=text(this,r.notes.isEmpty()?"此版本暂无更新说明。":r.notes,13,INK);copy.setLineSpacing(d(5),1);copy.setTextIsSelectable(true);notes.addView(copy);box.addView(notes,new LinearLayout.LayoutParams(-1,d(160)));gap(box,18);
                box.addView(text(this,"覆盖安装将保留书架与阅读进度。",11,MUTED));gap(box,16);box.addView(primary("下载更新",()->updater.download()));box.addView(secondary("稍后再说",this::hideDetails));break;
            case DOWNLOADING:case VERIFYING:
                box.addView(text(this,updater.state==AppUpdater.State.DOWNLOADING?"正在下载更新":"正在校验安装包",11,INK));gap(box,12);heading(box,"阅读狗 v"+r.version);description(box,"下载完成后，由你确认安装。");
                progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));box.addView(progress,new LinearLayout.LayoutParams(-1,d(6)));gap(box,10);progressText=text(this,"",11,MUTED);box.addView(progressText);gap(box,24);
                description(box,"可继续阅读，返回关于页查看进度。\n若应用被系统关闭，可重新下载。");box.addView(primary("继续阅读",()->{hideDetails();finish();}));box.addView(secondary("取消下载",()->{updater.cancel();hideDetails();}));break;
            case CHECK_FAILED:
                heading(box,"暂时无法检查");description(box,updater.error);box.addView(primary("重新检查",()->updater.check()));box.addView(secondary("稍后再说",this::hideDetails));break;
            case DOWNLOAD_FAILED:
                heading(box,"下载未完成");description(box,updater.error);box.addView(primary("重新下载",()->updater.download()));box.addView(secondary("稍后再说",this::hideDetails));break;
            case READY:
                boolean permission=permissionPrompt&&Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls();
                heading(box,permission?"允许安装应用":"已准备好更新");description(box,permission?"首次安装更新，需要允许阅读狗安装应用。\n开启后返回此页继续安装。":"阅读狗 v"+r.version+" 已下载并校验完成。\n接下来将打开系统安装界面。");
                box.addView(primary(permission?"去设置":"立即安装",permission?this::requestInstallPermission:this::install));box.addView(secondary("稍后再说",this::hideDetails));break;
            default:break;
        }
        dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));window.setDimAmount(.32f);int width=Math.min(getResources().getDisplayMetrics().widthPixels-d(40),d(380));window.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);outer.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int max=(int)(getResources().getDisplayMetrics().heightPixels*.8);if(outer.getMeasuredHeight()>max)window.setLayout(width,max);}
    }
    void heading(LinearLayout box,String value){TextView t=PaperUi.heading(this,value,23);t.setTextColor(INK);box.addView(t);gap(box,12);}
    void description(LinearLayout box,String value){TextView t=text(this,value,12,MUTED);t.setLineSpacing(d(6),1);box.addView(t);gap(box,22);}
    void openPage(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateRelease.PROJECT)));}catch(ActivityNotFoundException e){Toast.makeText(this,"未找到浏览器",Toast.LENGTH_SHORT).show();}}
    void requestInstallPermission(){try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){Toast.makeText(this,"请在系统设置中允许阅读狗安装应用",Toast.LENGTH_LONG).show();}}
    void install(){
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){permissionPrompt=true;showState();return;}
        if(!AppUpdater.apk(this).isFile()){updater.state=AppUpdater.State.DOWNLOAD_FAILED;updater.error="安装包已失效，请重新下载";changed();return;}
        try{startActivity(installIntent(this));}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,"无法打开安装界面，请检查系统安装权限后重试",Toast.LENGTH_LONG).show();}
    }
    static Intent installIntent(Context context){Uri uri=UpdateApkProvider.uri(context);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("阅读狗更新",uri));return intent;}
}
