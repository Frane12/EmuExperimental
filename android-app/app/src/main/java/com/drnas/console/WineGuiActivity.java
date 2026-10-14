package com.drnas.console;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import au.com.darkside.xserver.XServer;

/** Embedded X11 diagnostic screen. No external X server app or root required. */
public final class WineGuiActivity extends Activity {
    private XServer xserver;
    private WineGuiSession session;
    private RawWineTrace raw;
    private TextView status;
    private StableLogView logView;
    private final StringBuilder visibleLog=new StringBuilder();
    private boolean started;
    private volatile boolean destroyed;
    private int notices;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff0a101b);
        SystemBarInsets.apply(layout);
        LinearLayout bar=new LinearLayout(this);
        Button keyboard=new Button(this);keyboard.setText("Keyboard");
        Button export=new Button(this);export.setText("Export GUI TXT");
        Button back=new Button(this);back.setText("Stop / Back");
        bar.addView(keyboard);bar.addView(export);bar.addView(back);
        HorizontalScrollView toolbar=new HorizontalScrollView(this);
        toolbar.setHorizontalScrollBarEnabled(false);toolbar.addView(bar);layout.addView(toolbar);
        status=new TextView(this);status.setTextColor(Color.WHITE);
        status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setText("DRNAS 0.5.8 · Starting X11 / Wine GUI test");
        layout.addView(status,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(24)));
        logView=new StableLogView(this,0xff7be4c3);
        layout.addView(logView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(88)));
        Button latest=new Button(this);latest.setText("Latest log");bar.addView(latest);
        latest.setOnClickListener(v->logView.showLatest());
        try {raw=new RawWineTrace(new File(getFilesDir(),"logs"),"latest-wine-gui-raw.txt");}
        catch(IOException e){status.setText("GUI logging unavailable: "+e.getMessage());setContentView(layout);return;}
        session=new WineGuiSession();
        xserver=new XServer(this,6012,null);
        xserver.setDiagnosticListener(this::log);
        xserver.setOnStartListener(new XServer.OnXSeverStartListener(){
            @Override public void onStart() {
                if(started || destroyed)return;started=true;
                log("Embedded X11 server listening only on 127.0.0.1:6012; screen="+
                        xserver.getScreen().getWidth()+"x"+xserver.getScreen().getHeight());
                new Thread(()->{
                    try {session.run(WineGuiActivity.this, WineGuiActivity.this::log,raw);}
                    catch(Throwable e){log("GUI BLOCKED: "+e.getClass().getSimpleName()+": "+e.getMessage());}
                    finally {session.close();try{raw.close();}catch(IOException ignored){}}
                },"drnas-wine-gui").start();
            }
        });
        // Software View also renders the X11 root and Wine GDI bitmap updates.
        xserver.getScreen().setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        xserver.getScreen().setFocusableInTouchMode(true);
        layout.addView(xserver.getScreen(),new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(layout);
        keyboard.setOnClickListener(v->{
            xserver.getScreen().requestFocus();
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(
                    xserver.getScreen(),InputMethodManager.SHOW_IMPLICIT);
        });
        export.setText("Save GUI TXT");
        export.setOnClickListener(v->saveGuiLog(export));
        export.setOnLongClickListener(v->{openSaveAs();return true;});
        back.setOnClickListener(v->finish());
        status.postDelayed(()->{
            if(!destroyed && xserver.mappedWindowCount()==0)
                log("No Wine window mapped after 20s. Export GUI TXT for X11 driver/dependency errors.");
        },20000);
    }
    private synchronized void log(String line) {
        if(raw!=null)raw.write("GUI",line);
        runOnUiThread(()->{
            if(destroyed)return;
            if(visibleLog.length()>30000)visibleLog.delete(0,Math.min(12000,visibleLog.length()));
            visibleLog.append(line).append('\n');logView.setSnapshot(visibleLog.toString());
        });
        // Full trace stays on disk; update status only for milestones/errors.
        if(line.contains("mapped window") || line.contains("Canvas drew") || line.contains("BLOCKED") || line.contains("VERIFIED") ||
                line.contains("ended") || line.contains("No Wine window") || notices++<3)
            runOnUiThread(()->{if(!destroyed)status.setText(line);});
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void saveGuiLog(Button button) {
        if(raw==null){Toast.makeText(this,"GUI log is unavailable",Toast.LENGTH_LONG).show();return;}
        if(android.os.Build.VERSION.SDK_INT<29){openSaveAs();return;}
        button.setEnabled(false);
        Toast.makeText(this,"Saving to Downloads/DRNAS…",Toast.LENGTH_SHORT).show();
        log("GUI TXT export clicked: direct Downloads save requested.");
        new Thread(()->{
            try {
                String name=GuiLogExport.save(this,raw::copyTo);
                log("Saved Downloads/DRNAS/"+name);
                runOnUiThread(()->{if(!destroyed)Toast.makeText(this,
                        "Saved: Downloads/DRNAS/"+name,Toast.LENGTH_LONG).show();});
            } catch(Exception e) {
                log("GUI export FAILED: "+e.getClass().getSimpleName()+": "+e.getMessage());
                runOnUiThread(()->{if(!destroyed)Toast.makeText(this,
                        "Save failed: "+e.getMessage()+". Long-press Save GUI TXT for Save as.",Toast.LENGTH_LONG).show();});
            } finally {runOnUiThread(()->{if(!destroyed)button.setEnabled(true);});}
        },"drnas-gui-downloads").start();
    }
    private void openSaveAs() {
        log("Opening Android Save as dialog for GUI TXT.");
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");
        i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"DRNAS-Wine-GUI.txt");
        try {startActivityForResult(i,601);}
        catch(Exception e){log("Android document picker failed: "+e.getMessage());
            Toast.makeText(this,"Save as unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=601 || result!=RESULT_OK || data==null || data.getData()==null)return;
        final android.net.Uri uri=data.getData();
        new Thread(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"w")) {
                if(out==null)throw new IOException("No document output");raw.copyTo(out);
                log("GUI TXT exported.");
            }catch(IOException e){log("GUI export failed: "+e.getMessage());}
        },"drnas-gui-export").start();
    }
    @Override protected void onDestroy() {
        destroyed=true;
        if(xserver!=null)xserver.stop();
        if(session!=null)new Thread(session::close,"drnas-gui-stop").start();
        super.onDestroy();
    }
}
