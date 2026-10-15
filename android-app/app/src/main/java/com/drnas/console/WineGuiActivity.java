package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import au.com.darkside.xserver.XServer;

/**
 * Clean Windows desktop surface. Keep full RAW diagnostics privately for
 * troubleshooting, but never put text or session logs over the framebuffer.
 * The v0.5.16 working Winecfg path remains available with an explicit intent.
 */
public final class WineGuiActivity extends Activity {
    private XServer xserver;
    private WineGuiSession session;
    private RawWineTrace raw;
    private volatile boolean started;
    private volatile boolean destroyed;
    private TextView startupStatus;
    private boolean cpuCanvasFallback;

    private int dp(int value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        FrameLayout layout=new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);
        xserver=new XServer(this,6012,null);
        xserver.getScreen().setGuestResolution(
                WineGuiSession.DESKTOP_WIDTH,WineGuiSession.DESKTOP_HEIGHT);
        xserver.getScreen().setRelativeTouchMode(true);
        // GPU-backed Android HWUI compositing of Wine's CPU X11 bitmaps.
        // This does NOT implement X11 DRI3/Present or Wine Vulkan WSI.
        xserver.getScreen().setLayerType(View.LAYER_TYPE_NONE,null);
        logEarlyRendererIntent();
        xserver.getScreen().setFocusableInTouchMode(true);
        layout.addView(xserver.getScreen(),new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Cursor is a separate accelerated Android layer. Dragging the pointer
        // no longer requests a redraw of every mapped Wine window bitmap.
        CursorOverlayView cursorOverlay=new CursorOverlayView(this,
                WineGuiSession.DESKTOP_WIDTH,WineGuiSession.DESKTOP_HEIGHT);
        xserver.getScreen().setCursorOverlayListener(cursorOverlay::setCursor);
        cursorOverlay.setClickable(false);
        cursorOverlay.setFocusable(false);
        layout.addView(cursorOverlay,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Quiet startup notice, not diagnostics. An offline Mono installation
        // may need time before the Wine desktop maps its first X11 window.
        startupStatus=new TextView(this);
        startupStatus.setText("Preparing Windows desktop…");
        startupStatus.setTextColor(0xFFE0EFF8);
        startupStatus.setTextSize(16);
        startupStatus.setGravity(Gravity.CENTER);
        startupStatus.setBackgroundColor(0xA9000000);
        layout.addView(startupStatus,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(56),Gravity.CENTER));

        // The sole on-screen control is a small, translucent menu.
        // Long-press to save a log only if debugging is necessary.
        Button menu=new Button(this);
        menu.setText("⋮");
        menu.setTextColor(Color.WHITE);
        menu.setTextSize(19);
        menu.setAllCaps(false);
        menu.setMinWidth(0);
        menu.setMinHeight(0);
        menu.setPadding(0,0,0,0);
        menu.setAlpha(0.60f);
        FrameLayout.LayoutParams ml=new FrameLayout.LayoutParams(
                dp(46),dp(46),Gravity.TOP|Gravity.END);
        ml.topMargin=dp(5);ml.rightMargin=dp(5);
        layout.addView(menu,ml);
        menu.setOnClickListener(v->new AlertDialog.Builder(this)
                .setItems(new String[]{"Keyboard","Toggle CPU/GPU desktop Canvas","Reset Wine","Exit desktop"},(dialog,which)->{
                    if(which==0)showKeyboard();
                    else if(which==1) {
                        cpuCanvasFallback=!cpuCanvasFallback;
                        xserver.getScreen().setLayerType(cpuCanvasFallback?
                                View.LAYER_TYPE_SOFTWARE:View.LAYER_TYPE_NONE,null);
                        xserver.getScreen().postInvalidateOnAnimation();
                        log("Android X11 desktop Canvas mode: "+(cpuCanvasFallback?
                                "SOFTWARE fallback":"HWUI hardware-accelerated candidate")+
                                " (Wine Vulkan DRI3/Present still unimplemented)");
                        Toast.makeText(this,cpuCanvasFallback?
                                "CPU Canvas fallback":"GPU Canvas enabled",Toast.LENGTH_SHORT).show();
                    }
                    else if(which==2) {
                        Intent restart=new Intent(this,WineGuiActivity.class);
                        new Thread(()->{
                            try {
                                WineGuiSession.stopActive();
                                if(xserver!=null)xserver.stop();
                                runOnUiThread(()->{if(!destroyed){finish();startActivity(restart);}});
                            } catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Wine is still stopping",Toast.LENGTH_LONG).show());}
                        },"drnas-desktop-reset").start();
                    } else finish();
                }).show());
        menu.setOnLongClickListener(v->{saveGuiLog();return true;});

        setContentView(layout);
        try {
            raw=new RawWineTrace(new File(getFilesDir(),"logs"),"latest-wine-gui-raw.txt");
        } catch(IOException e) {
            Toast.makeText(this,"Wine desktop log unavailable: "+e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        session=new WineGuiSession();
        String requested=getIntent().getStringExtra("DRNAS_GUI_APP");
        // Default always starts Explorer, never Wine configuration.
        final String launchApp="files".equals(requested) ? "files":
                "config".equals(requested) ? "config":"desktop";
        xserver.setDiagnosticListener(this::log);
        xserver.setOnStartListener(new XServer.OnXSeverStartListener(){
            @Override public void onStart() {
                if(started || destroyed)return;
                started=true;
                log("X11 live; guest resolution "+
                        xserver.getScreen().getGuestWidth()+"x"+
                        xserver.getScreen().getGuestHeight());
                new Thread(()->{
                    try {session.run(WineGuiActivity.this,WineGuiActivity.this::log,raw,launchApp);}
                    catch(Throwable e) {
                        log("Desktop stopped: "+e.getClass().getSimpleName()+": "+e.getMessage());
                        runOnUiThread(()->{
                            if(!destroyed)Toast.makeText(WineGuiActivity.this,
                                    "Windows desktop could not start. Long-press ⋮ to save log.",
                                    Toast.LENGTH_LONG).show();
                        });
                    } finally {
                        session.close();
                        try {raw.close();}catch(IOException ignored){}
                    }
                },"drnas-desktop").start();
            }
        });
        // Hide the status as soon as real Wine windows are mapped.
        xserver.getScreen().postDelayed(new Runnable() {
            @Override public void run() {
                if(destroyed || startupStatus==null)return;
                if(xserver.mappedWindowCount()>0)startupStatus.setVisibility(View.GONE);
                else xserver.getScreen().postDelayed(this,700);
            }
        },700);
        // Report absence of a mapped window only to the private session log.
        xserver.getScreen().postDelayed(()->{
            if(!destroyed && xserver.mappedWindowCount()==0)
                log("No Windows window mapped after 20 seconds; use hidden log export if needed.");
        },20000);
    }

    private void logEarlyRendererIntent() {
        android.util.Log.i("DRNAS-HWUI","Trying Android GPU Canvas + split cursor; Wine Vulkan WSI is not yet supported");
    }

    private synchronized void log(String line) {
        if(raw!=null)raw.write("GUI",line);
        if(line.startsWith("Installing Wine Mono") ||
                line.startsWith("Installing Microsoft VC++") ||
                line.startsWith("Launching Wine explorer")) {
            runOnUiThread(()->{
                if(startupStatus!=null && !destroyed)
                    startupStatus.setText(line.startsWith("Launching ")?
                            "Opening Windows desktop…":line);
            });
        }
    }

    private void showKeyboard() {
        xserver.getScreen().requestFocus();
        InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(imm!=null)imm.showSoftInput(xserver.getScreen(),InputMethodManager.SHOW_IMPLICIT);
    }

    private void saveGuiLog() {
        if(raw==null){Toast.makeText(this,"No GUI log yet",Toast.LENGTH_SHORT).show();return;}
        if(android.os.Build.VERSION.SDK_INT<29){openSaveAs();return;}
        Toast.makeText(this,"Saving GUI TXT to Downloads/DRNAS…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try {
                String name=GuiLogExport.save(this,raw::copyTo);
                runOnUiThread(()->{
                    if(!destroyed)Toast.makeText(this,"Saved: Downloads/DRNAS/"+name,
                            Toast.LENGTH_LONG).show();
                });
            } catch(Exception e) {
                log("GUI save error: "+e.getClass().getSimpleName()+": "+e.getMessage());
                runOnUiThread(()->{
                    if(!destroyed)openSaveAs();
                });
            }
        },"drnas-desktop-log-export").start();
    }

    private void openSaveAs() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("text/plain");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"DRNAS-Wine-GUI.txt");
        try {startActivityForResult(i,601);}
        catch(Exception e){Toast.makeText(this,
                "Could not export session log.",Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=601 || result!=RESULT_OK || data==null || data.getData()==null)return;
        final android.net.Uri uri=data.getData();
        new Thread(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"w")) {
                if(out==null)throw new IOException("No document output");
                raw.copyTo(out);
            } catch(IOException e){log("GUI export failed: "+e.getMessage());}
        },"drnas-desktop-save-as").start();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused)immersive();
    }

    @Override protected void onDestroy() {
        destroyed=true;
        if(session!=null)new Thread(()->{session.close();if(xserver!=null)xserver.stop();},"drnas-desktop-stop").start();
        else if(xserver!=null)xserver.stop();
        super.onDestroy();
    }
}

