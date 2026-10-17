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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import au.com.darkside.xserver.Drawable;
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
    private LinearLayout dependencyPanel;
    private TextView dependencyProgressText;
    private ProgressBar dependencyProgressBar;
    private boolean cpuCanvasFallback;
    private volatile boolean verboseLogs; // OFF by default, live menu toggle
    private VirtualStickView virtualStick;
    private XInputSharedState xinputState;
    private XInputControlsView xinputLeft,xinputRight;
    private RelativeLookOverlay relativeLookOverlay;
    private void syncVirtualPadConnection() {
        if(xinputState==null)return;
        boolean visiblePad=xinputLeft!=null && xinputLeft.getVisibility()==View.VISIBLE;
        xinputState.setVirtualEnabled(visiblePad || relativeCameraEnabled);
    }
    private volatile boolean desktopLaunchRequested;
    private volatile boolean desktopFailed;
    private Button shareLog;
    private Button toolbarKeyboard,toolbarTasks,toolbarOptions;
    private boolean toolbarExpanded;
    private void setToolbarExpanded(boolean expanded) {
        toolbarExpanded=expanded;
        int visibility=expanded?View.VISIBLE:View.GONE;
        if(shareLog!=null)shareLog.setVisibility(visibility);
        if(toolbarKeyboard!=null)toolbarKeyboard.setVisibility(visibility);
        if(toolbarTasks!=null)toolbarTasks.setVisibility(visibility);
        if(toolbarOptions!=null)toolbarOptions.setVisibility(visibility);
    }
    private boolean keyboardPending;
    private int keyboardAttempts;
    private DrnasPerformanceHud performanceHud;
    private final CountDownLatch frameCapReady=new CountDownLatch(1);
    private int chosenFrameCap;
    private boolean renderCaptureRunning;
    private boolean relativeCameraEnabled;
    private final Runnable hudTicker=new Runnable() {
        @Override public void run() {
            if(destroyed||performanceHud==null||!performanceHud.isActive())return;
            performanceHud.sample(xserver.getScreen().getDisplayedFps());
            performanceHud.postDelayed(this,650);
        }
    };

    private android.graphics.drawable.GradientDrawable toolbarGlass(boolean primary) {
        android.graphics.drawable.GradientDrawable d=
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{primary?0xe12d5564:0xbd21394d,0xd2152b40});
        d.setCornerRadius(dp(17));
        d.setStroke(dp(1),primary?0xa284dec6:0x758bbfd4);
        return d;
    }
    private void styleToolbar(Button button,boolean primary) {
        button.setAllCaps(false);
        button.setTextColor(primary?0xff94f1d1:0xffe6f5f8);
        button.setBackground(toolbarGlass(primary));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setElevation(dp(3));
    }

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
        // Keep toggle through Reset Wine (which recreates this Activity).
        verboseLogs=getPreferences(MODE_PRIVATE).getBoolean("verbose_diagnostics",false);
        chosenFrameCap=DxvkFrameCap.clamp(getPreferences(MODE_PRIVATE).getInt("dxvk_fps_cap",0));
        relativeCameraEnabled=getPreferences(MODE_PRIVATE).getBoolean("relative_camera",false);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        FrameLayout layout=new FrameLayout(this);
        layout.setMotionEventSplittingEnabled(true);
        layout.setBackgroundColor(Color.BLACK);
        // Native Wine PE DLL and Android producer share this app-private file.
        // The 64-byte shared mapping is updated only by actual input changes.
        try {
            File prefix=new File(getFilesDir(),
                    "containers/default/wineboot-probe/template/.wine");
            xinputState=new XInputSharedState(DrnasXInputRuntime.stateFile(prefix));
        } catch(IOException error) {
            android.util.Log.e("DRNAS-XInput","State mapping unavailable",error);
        }
        xserver=new XServer(this,6012,null);
        xserver.setPresentationMaskEnabled(getPreferences(MODE_PRIVATE)
                .getBoolean("x11_presentation_mask",true));
        xserver.setFrameDiagnosticsEnabled(verboseLogs);
        xserver.getScreen().setGuestResolution(
                WineGuiSession.DESKTOP_WIDTH,WineGuiSession.DESKTOP_HEIGHT);
        xserver.getScreen().setRelativeTouchMode(true);
        xserver.getScreen().setCameraTouchMode(false);
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

        // Overlay BELOW toolbar and controller buttons but ABOVE X11 screen.
        // Late-added full-screen views would swallow the top-right menu touch.
        // This view is below XInput buttons and toolbar; any free screen touch
        // becomes a movable right thumbstick. Existing XInput buttons win hits.
        if(xinputState!=null) {
            relativeLookOverlay=new RelativeLookOverlay(this,xinputState);
            relativeLookOverlay.setVisibility(relativeCameraEnabled?View.VISIBLE:View.GONE);
            layout.addView(relativeLookOverlay,new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        }

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

        // Separate factual progress bar for first-time offline redistributable installs.
        // Shown only when a Dependencies.zip has been imported; percentage means
        // completed installer attempts, NOT a guessed time estimate.
        dependencyPanel=new LinearLayout(this);
        dependencyPanel.setOrientation(LinearLayout.VERTICAL);
        dependencyPanel.setBackgroundColor(0xDF102235);
        dependencyPanel.setPadding(dp(14),dp(11),dp(14),dp(13));
        dependencyProgressText=new TextView(this);
        dependencyProgressText.setTextColor(0xFFE0EFF8);
        dependencyProgressText.setTextSize(14);
        dependencyProgressText.setText("Instalacija Windows dodataka");
        dependencyPanel.addView(dependencyProgressText);
        dependencyProgressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        dependencyProgressBar.setMax(100);
        dependencyProgressBar.setIndeterminate(false);
        dependencyPanel.addView(dependencyProgressBar,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(7)));
        dependencyPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams depParams=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        depParams.leftMargin=dp(22);depParams.rightMargin=dp(22);
        depParams.bottomMargin=dp(22);
        layout.addView(dependencyPanel,depParams);

        // Android controls remain reachable even if Wine has no mapped window.
        shareLog=new Button(this);
        shareLog.setText("Pošalji log");
        shareLog.setAllCaps(false);
        shareLog.setTextColor(Color.WHITE);
        shareLog.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xE025465A));
        shareLog.setContentDescription("Pošalji dijagnostički TXT log");
        FrameLayout.LayoutParams sl=new FrameLayout.LayoutParams(dp(132),dp(42),Gravity.TOP|Gravity.END);
        // Collapse all buttons by default; the glass HUD owns the top center.
        // Expanded toolbar sits on a second row, not on top of performance HUD.
        sl.topMargin=dp(61);sl.rightMargin=dp(160);
        layout.addView(shareLog,sl);
        styleToolbar(shareLog,false);
        shareLog.setOnClickListener(v->shareGuiLog());
        Button menu=new Button(this);
        menu.setText("⋮");
        menu.setTextColor(Color.WHITE);
        menu.setTextSize(19);
        menu.setAllCaps(false);
        menu.setMinWidth(0);
        menu.setMinHeight(0);
        menu.setPadding(0,0,0,0);
        styleToolbar(menu,true);
        menu.setAlpha(.93f);
        FrameLayout.LayoutParams ml=new FrameLayout.LayoutParams(
                dp(46),dp(46),Gravity.TOP|Gravity.END);
        ml.topMargin=dp(5);ml.rightMargin=dp(5);
        layout.addView(menu,ml);
        menu.setOnClickListener(v->setToolbarExpanded(!toolbarExpanded));
        // A dedicated Options button opens the existing experimental menu
        // when expanded. The top-right dot itself only folds/unfolds toolbar.
        toolbarOptions=new Button(this);
        toolbarOptions.setText("Opcije");
        toolbarOptions.setAllCaps(false);
        toolbarOptions.setTextColor(Color.WHITE);
        toolbarOptions.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xCD294254));
        FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(dp(100),dp(42),Gravity.TOP|Gravity.END);
        op.topMargin=dp(61);op.rightMargin=dp(56);
        layout.addView(toolbarOptions,op);
        styleToolbar(toolbarOptions,false);
        toolbarOptions.setOnClickListener(v->showGlassControls());
        menu.setOnLongClickListener(v->{saveGuiLog();return true;});

        toolbarTasks=new Button(this);
        toolbarTasks.setText("Task Manager");toolbarTasks.setAllCaps(false);
        FrameLayout.LayoutParams tl=new FrameLayout.LayoutParams(dp(140),dp(42),Gravity.TOP|Gravity.END);
        tl.topMargin=dp(61);tl.rightMargin=dp(296);
        layout.addView(toolbarTasks,tl);styleToolbar(toolbarTasks,false);
        toolbarTasks.setOnClickListener(v->openTaskManager());
        // An always-visible keyboard button remains usable if a Wine game changes focus.
        toolbarKeyboard=new Button(this);
        toolbarKeyboard.setText("⌨");
        toolbarKeyboard.setContentDescription("Prikaži Android tipkovnicu za Wine");
        toolbarKeyboard.setTextSize(20);
        toolbarKeyboard.setAllCaps(false);
        FrameLayout.LayoutParams kl=new FrameLayout.LayoutParams(dp(60),dp(42),Gravity.TOP|Gravity.END);
        kl.topMargin=dp(61);kl.rightMargin=dp(440);
        layout.addView(toolbarKeyboard,kl);
        styleToolbar(toolbarKeyboard,false);
        toolbarKeyboard.setOnClickListener(v->{
            keyboardPending=true;
            keyboardAttempts=0;
            showKeyboard();
        });
        // Opt-in: leaves Wine desktop unobstructed until a game is running.
        // Sends real X11 keyboard events; no fake XInput claims or uinput permissions.
        virtualStick=new VirtualStickView(this,(key,pressed)->{
            if(destroyed || xserver==null)return;
            KeyEvent event=new KeyEvent(pressed?KeyEvent.ACTION_DOWN:KeyEvent.ACTION_UP,key);
            if(pressed)xserver.getScreen().onKeyDown(key,event);
            else xserver.getScreen().onKeyUp(key,event);
        });
        FrameLayout.LayoutParams stickParams=new FrameLayout.LayoutParams(
                dp(182),dp(182),Gravity.BOTTOM|Gravity.START);
        stickParams.leftMargin=dp(18);
        stickParams.bottomMargin=dp(40);
        virtualStick.setVisibility(View.GONE);
        layout.addView(virtualStick,stickParams);
        // Xbox-style on-screen controller is OFF by default, so Explorer and
        // touchscreen X11 pointer have exactly the same behavior as v0.5.47.
        if(xinputState!=null) {
            xinputLeft=new XInputControlsView(this,xinputState,true);
            FrameLayout.LayoutParams l=new FrameLayout.LayoutParams(
                    dp(214),dp(214),Gravity.BOTTOM|Gravity.START);
            l.leftMargin=dp(3);l.bottomMargin=dp(31);
            xinputLeft.setVisibility(View.GONE);
            layout.addView(xinputLeft,l);
            xinputRight=new XInputControlsView(this,xinputState,false);
            FrameLayout.LayoutParams rr=new FrameLayout.LayoutParams(
                    dp(272),dp(214),Gravity.BOTTOM|Gravity.END);
            rr.rightMargin=dp(5);rr.bottomMargin=dp(31);
            xinputRight.setVisibility(View.GONE);
            layout.addView(xinputRight,rr);
        }
        syncVirtualPadConnection();
        performanceHud=new DrnasPerformanceHud(this);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(
                dp(374),dp(27),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        hp.topMargin=dp(5);
        layout.addView(performanceHud,hp);
        // A very thin, transparent gesture zone at the screen's top edge:
        // pull down to reveal toolbar, pull upward to collapse it. No gesture
        // interceptor covers the active Crysis framebuffer or virtual pads.
        View gestureEdge=new View(this);
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(10),Gravity.TOP);
        final float[] touchDown=new float[1];
        gestureEdge.setOnTouchListener((view,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                touchDown[0]=event.getRawY();
                return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE ||
                    event.getActionMasked()==MotionEvent.ACTION_UP){
                float delta=event.getRawY()-touchDown[0];
                if(delta>dp(25)){setToolbarExpanded(true);return true;}
                if(delta<-dp(25)){setToolbarExpanded(false);return true;}
            }
            return true;
        });
        layout.addView(gestureEdge,gp);
        setToolbarExpanded(false);
        performanceHud.setActive(getPreferences(MODE_PRIVATE).getBoolean("perf_hud",false));
        xserver.getScreen().setFpsCounterEnabled(performanceHud.isActive());
        setContentView(layout);
        if(performanceHud.isActive())performanceHud.postDelayed(hudTicker,650);
        try {
            AutomaticCrashLog.begin(this);
            raw=new RawWineTrace(new File(getFilesDir(),"logs"),"latest-wine-gui-raw.txt");
            raw.setVerbose(verboseLogs);
        } catch(IOException e) {
            Toast.makeText(this,"Wine desktop log unavailable: "+e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        session=new WineGuiSession();
        session.setDiagnosticsEnabled(verboseLogs);
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
                    try {
                        // Input FPS cap before any Wine process inherits DXVK environment.
                        frameCapReady.await();
                        if(destroyed)return;
                        session.setFrameCap(chosenFrameCap);
                        session.run(WineGuiActivity.this,WineGuiActivity.this::log,raw,launchApp);
                    }
                    catch(Throwable e) {
                        desktopFailed=true;
                        log("Desktop stopped: "+e.getClass().getSimpleName()+": "+e.getMessage());
                        runOnUiThread(()->{
                            if(!destroyed) {
                                startupStatus.setText("Windows se nije pokrenuo. Pritisni Pošalji log.\n"+e.getClass().getSimpleName()+": "+e.getMessage());
                                startupStatus.setVisibility(View.VISIBLE);
                            }
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
                if(!desktopFailed && desktopLaunchRequested && xserver.mappedWindowCount()>0)
                    startupStatus.setVisibility(View.GONE);
                else xserver.getScreen().postDelayed(this,700);
            }
        },700);
        // Report absence of a mapped window only to the private session log.
        xserver.getScreen().postDelayed(()->{
            if(!destroyed && !desktopFailed && (!desktopLaunchRequested || xserver.mappedWindowCount()==0)) {
                log("No Wine desktop confirmed after 20 seconds. On-screen log sharing remains available.");
                if(!startupStatus.getText().toString().startsWith("Pripremam disk R:"))
                    startupStatus.setText("Windows se još pokreće… Pošalji log ako ekran ostane siv.");
                startupStatus.setVisibility(View.VISIBLE);
            }
        },20000);
        // Shown before Wine Explorer starts: default 0 = no artificial cap.
        showFrameLimiterDialog(true);
    }

    private void setFrameCapAndContinue(int cap,boolean beforeStartup) {
        chosenFrameCap=DxvkFrameCap.clamp(cap);
        getPreferences(MODE_PRIVATE).edit().putInt("dxvk_fps_cap",chosenFrameCap).apply();
        if(beforeStartup)frameCapReady.countDown();
        Toast.makeText(this,"DXVK "+(chosenFrameCap==0?"bez limita":chosenFrameCap+" FPS")+
                (beforeStartup?"":" (primijenit će se nakon Reset Wine)"),
                Toast.LENGTH_SHORT).show();
    }

    private void showFrameLimiterDialog(boolean beforeStartup) {
        if(destroyed)return;
        // Never combine AlertDialog.setMessage with setItems: Android drops
        // the choices when both are set. Render explicit buttons + edit box.
        DrnasDxvkDialog.show(this,chosenFrameCap,beforeStartup,
                cap->setFrameCapAndContinue(cap,beforeStartup),
                ()->{if(beforeStartup)frameCapReady.countDown();});
    }

    private static final String[] GLASS_MENU_ITEMS={"Keyboard","Toggle CPU/GPU desktop Canvas","Reset Wine","Exit desktop","Pošalji log","Spremi log","Task Manager","Promijeni prozor (Alt+Tab)","Fullscreen / prozor (Alt+Enter)","Tekst → Wine (IME fallback)","Vulkan FIFO (izoliran)","Vulkan IMMEDIATE (izoliran)","WSI A: Create/Destroy bez Present","WSI B: FIFO + XSync","Eksperiment 1: X11 bez Vulkana","Eksperiment 2: Vulkan bez slike + provjera veze","Eksperiment 3: Prikaz slike + provjera veze","Virtual stick (WASD) ON/OFF","Dijagnostika / logovi: ON/OFF","Xbox XInput (virtual pad) ON/OFF","HUD FPS/RAM/BAT ON/OFF","Relativni miš / kamera ON/OFF","WSI prezentacijska maska ON/OFF","DXVK FPS limit (sljedeći start)","Snimanje rendera — 5 sekundi"};
    private boolean glassMenuActive(int which) {
        switch(which) {
            case 1:return cpuCanvasFallback;
            case 17:return virtualStick!=null && virtualStick.getVisibility()==View.VISIBLE;
            case 18:return verboseLogs;
            case 19:return xinputLeft!=null && xinputLeft.getVisibility()==View.VISIBLE;
            case 20:return performanceHud!=null && performanceHud.isActive();
            case 21:return relativeCameraEnabled;
            case 22:return getPreferences(MODE_PRIVATE).getBoolean("x11_presentation_mask",true);
            default:return false;
        }
    }
    private void showGlassControls() {
        DrnasGlassOptions.show(this,GLASS_MENU_ITEMS,this::glassMenuActive,(dialog,which)->{

                    if(which==0) {
                        keyboardPending=true;
                        keyboardAttempts=0;
                        xserver.getScreen().postDelayed(this::showKeyboard,120);
                    }
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
                    } else if(which==3)finish();
                    else if(which==4)shareGuiLog();
                    else if(which==5)saveGuiLog();
                    else if(which==6)openTaskManager();
                    else if(which==9)openTextFallback();
                    else if(which==20) {
                        boolean enable=!performanceHud.isActive();
                        getPreferences(MODE_PRIVATE).edit().putBoolean("perf_hud",enable).apply();
                        performanceHud.setActive(enable);
                        xserver.getScreen().setFpsCounterEnabled(enable);
                        performanceHud.removeCallbacks(hudTicker);
                        if(enable)performanceHud.post(hudTicker);
                        Toast.makeText(this,enable?"HUD ON":"HUD OFF",Toast.LENGTH_SHORT).show();
                    }
                    else if(which==21) {
                        relativeCameraEnabled=!relativeCameraEnabled;
                        getPreferences(MODE_PRIVATE).edit().putBoolean("relative_camera",relativeCameraEnabled).apply();
                        if(relativeLookOverlay!=null) {
                            if(!relativeCameraEnabled)relativeLookOverlay.release();
                            relativeLookOverlay.setVisibility(relativeCameraEnabled?View.VISIBLE:View.GONE);
                        }
                        syncVirtualPadConnection();
                        Toast.makeText(this,relativeCameraEnabled?
                                "Kamera bilo gdje: XInput analog ON":"Kamera bilo gdje OFF (normalni dodir)",
                                Toast.LENGTH_SHORT).show();
                    }
                    else if(which==22) {
                        boolean enabled=!getPreferences(MODE_PRIVATE).getBoolean("x11_presentation_mask",true);
                        getPreferences(MODE_PRIVATE).edit().putBoolean("x11_presentation_mask",enabled).apply();
                        if(xserver!=null)xserver.setPresentationMaskEnabled(enabled);
                        Toast.makeText(this,enabled?"Maska prezentacije ON":"Maska prezentacije OFF (A/B test)",
                                Toast.LENGTH_SHORT).show();
                    }
                    else if(which==23) {
                        showFrameLimiterDialog(false);
                    }
                    else if(which==24) {
                        captureRenderForFiveSeconds();
                    }
                    else if(which==19) {
                        if(xinputState==null) {
                            Toast.makeText(this,"XInput mapiranje nije dostupno.",Toast.LENGTH_LONG).show();
                        } else {
                            boolean show=xinputLeft.getVisibility()!=View.VISIBLE;
                            if(show && virtualStick.getVisibility()==View.VISIBLE) {
                                virtualStick.releaseAll();
                                virtualStick.setVisibility(View.GONE);
                            }
                            if(!show){xinputLeft.release();xinputRight.release();}
                            xinputLeft.setVisibility(show?View.VISIBLE:View.GONE);
                            xinputRight.setVisibility(show?View.VISIBLE:View.GONE);
                            syncVirtualPadConnection();
                            log("DRNAS_XINPUT "+(show?"VIRTUAL_CONNECTED":"VIRTUAL_DISCONNECTED"));
                            Toast.makeText(this,show?"XInput Xbox controller ON":"XInput Xbox controller OFF",Toast.LENGTH_SHORT).show();
                        }
                    }
                    else if(which==18) {
                        verboseLogs=!verboseLogs;
                        getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("verbose_diagnostics",verboseLogs).apply();
                        if(raw!=null)raw.setVerbose(verboseLogs);
                        if(session!=null)session.setDiagnosticsEnabled(verboseLogs);
                        if(xserver!=null)xserver.setFrameDiagnosticsEnabled(verboseLogs);
                        log("DRNAS_DIAGNOSTICS_TOGGLE "+(verboseLogs?"ON":"OFF")+
                                " | X11/raw logging changes live. Wine/DXVK verbose env applies after Reset Wine.");
                        Toast.makeText(this,verboseLogs?
                                "Logovi ON. Puni Wine/DXVK trace nakon Reset Wine.":
                                "Logovi OFF: prikaz bez dijagnostičkog opterećenja.",
                                Toast.LENGTH_LONG).show();
                    }
                    else if(which==17) {
                        boolean show=virtualStick.getVisibility()!=View.VISIBLE;
                        if(show && xinputState!=null && xinputLeft!=null) {
                            xinputLeft.release();xinputRight.release();
                            xinputLeft.setVisibility(View.GONE);
                            xinputRight.setVisibility(View.GONE);
                            syncVirtualPadConnection();
                        }
                        if(!show)virtualStick.releaseAll();
                        virtualStick.setVisibility(show?View.VISIBLE:View.GONE);
                        log("DRNAS_VIRTUAL_STICK "+(show?"ON":"OFF")+
                                " input=Wine X11 WASD (XInput controller NOT emulated)");
                        Toast.makeText(this,show?"WASD stick ON":"WASD stick OFF",Toast.LENGTH_SHORT).show();
                    }
                    else if(which>=14 && which<=16)
                        testVulkanPresentation(which==14?"x11-only":which==15?"create-checkpoints":"present-checkpoints");
                    else if(which>=10 && which<=13)
                        testVulkanPresentation(which==10?"fifo":which==11?"immediate":which==12?"create-only":"sync");
                    else sendAltKey(which==7?android.view.KeyEvent.KEYCODE_TAB:android.view.KeyEvent.KEYCODE_ENTER);
        });
    }

    private void captureRenderForFiveSeconds() {
        if(xserver==null || destroyed)return;
        if(renderCaptureRunning || !xserver.startRenderCapture()) {
            Toast.makeText(this,"Uzorak već traje",Toast.LENGTH_SHORT).show();
            return;
        }
        renderCaptureRunning=true;
        Toast.makeText(this,"Snimam 5 sekundi — bez kontinuiranog loga",
                Toast.LENGTH_SHORT).show();
        // X11 worker accumulates bounded in-RAM frame timings. No disk IO
        // or giant Strings in PutImage/Canvas hot paths.
        xserver.getScreen().postDelayed(()->{
            final String result=xserver.finishRenderCapture();
            renderCaptureRunning=false;
            new Thread(()->{
                try {
                    final String path=GuiLogExport.saveRenderSample(this,out->
                            out.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    runOnUiThread(()->{
                        if(!destroyed)Toast.makeText(this,
                                "Spremljeno: Downloads/DRNAS/"+path,
                                Toast.LENGTH_LONG).show();
                    });
                } catch(Exception error) {
                    log("Five-second render capture export failed: "+error.getMessage());
                    runOnUiThread(()->{
                        if(!destroyed)Toast.makeText(this,"Spremanje uzorka nije uspjelo",
                                Toast.LENGTH_LONG).show();
                    });
                }
            },"emudrnas-render-capture-export").start();
        },5000L);
    }

    private void logEarlyRendererIntent() {
        android.util.Log.i("DRNAS-HWUI","Trying Android GPU Canvas + split cursor; Wine Vulkan WSI is not yet supported");
    }

    private synchronized void log(String line) {
        // UI progress + fatal evidence are independent of optional verbose IO.
        // In quiet mode do not block the rendering or X11 event paths on disk.
        boolean critical=AutomaticLogTrigger.matches(line) ||
                line.contains(":err:") || line.contains("dlerror") ||
                line.contains("fatal IO error") || line.startsWith("Audio unavailable:");
        if(raw!=null) {
            if(verboseLogs)raw.write("GUI",line);
            else if(critical)raw.writeEssential("GUI",line);
        }
        if(!destroyed && AutomaticLogTrigger.matches(line)) {
            try {if(raw!=null)raw.checkpoint();}catch(IOException ignored){}
            AutomaticCrashLog.request(this,line);
        }
        if(line.startsWith("Launching Wine explorer"))desktopLaunchRequested=true;
        if(line.startsWith("[Dependencies] ")) {
            int separator=line.indexOf('|',15);
            if(separator>0) {
                try {
                    final int percent=Integer.parseInt(line.substring(15,separator).trim());
                    final String label=line.substring(separator+1);
                    runOnUiThread(()->{
                        if(destroyed || dependencyPanel==null)return;
                        dependencyProgressBar.setProgress(Math.max(0,Math.min(100,percent)));
                        dependencyProgressText.setText(label+"  •  "+percent+"%");
                        dependencyPanel.setVisibility(View.VISIBLE);
                        if(percent>=100)dependencyPanel.postDelayed(()->{
                            if(!destroyed && dependencyPanel!=null)
                                dependencyPanel.setVisibility(View.GONE);
                        },2100);
                    });
                } catch(NumberFormatException badProgress) {
                    android.util.Log.w("DRNAS-Dependencies","Invalid progress: "+line);
                }
            }
        }
        if(line.startsWith("Pripremam disk R:") || line.startsWith("Installing Wine Mono") ||
                line.startsWith("Installing Microsoft VC++") ||
                line.startsWith("Launching Wine explorer")) {
            runOnUiThread(()->{
                if(startupStatus!=null && !destroyed)
                    startupStatus.setText(line.startsWith("Launching ")?
                            "Opening Windows desktop…":line);
            });
        }
    }

    private void openTaskManager() {
        new Thread(()->{
            try {if(session==null)throw new IOException("Windows još nije spreman");session.openTaskManager();}
            catch(Exception e){log("Task Manager: "+e.getMessage());runOnUiThread(()->{
                if(!destroyed)Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
            });}
        },"drnas-task-manager").start();
    }

    private void testVulkanPresentation(String mode) {
        new Thread(()->{
            try {
                if(session==null)throw new IOException("Windows još nije spreman");
                session.testVulkanPresentation(WineGuiActivity.this,mode);
                runOnUiThread(()->{if(!destroyed)Toast.makeText(this,"Vulkan test završen — rezultat je u logu",Toast.LENGTH_LONG).show();});
            } catch(Exception e){log("Vulkan test: "+e.getMessage());runOnUiThread(()->{
                if(!destroyed)Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
            });}
        },"drnas-vulkan-present-"+mode).start();
    }

    private void sendAltKey(int key) {
        xserver.getScreen().requestFocus();
        int alt=android.view.KeyEvent.KEYCODE_ALT_LEFT;
        xserver.getScreen().onKeyDown(alt,new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,alt));
        xserver.getScreen().onKeyDown(key,new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,key));
        xserver.getScreen().onKeyUp(key,new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,key));
        xserver.getScreen().onKeyUp(alt,new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,alt));
        log("Sent Alt+"+(key==android.view.KeyEvent.KEYCODE_TAB?"Tab":"Enter")+" to Wine.");
    }

    private void showKeyboard() {
        if(destroyed || !keyboardPending || xserver==null)return;
        // Dialog dismissal and Android IME focus restoration are asynchronous.
        // Keep the request pending until the window regains focus.
        if(!getWindow().getDecorView().hasWindowFocus())return;
        xserver.getScreen().post(()->{
            if(destroyed || !keyboardPending)return;
            boolean focused=xserver.getScreen().requestFocus();
            if(!focused && !xserver.getScreen().hasFocus())
                focused=xserver.getScreen().requestFocusFromTouch();
            InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(imm==null) {
                keyboardPending=false;
                log("Android IME service unavailable; use the Text → Wine fallback.");
                return;
            }
            if(keyboardAttempts==0)imm.restartInput(xserver.getScreen());
            boolean accepted=imm.showSoftInput(xserver.getScreen(),
                    InputMethodManager.SHOW_IMPLICIT);
            log("Android IME request accepted="+accepted+" focus="+
                    xserver.getScreen().hasFocus()+" attempt="+(keyboardAttempts+1));
            if(accepted) {
                keyboardPending=false;
                keyboardAttempts=0;
            } else if(++keyboardAttempts<3) {
                xserver.getScreen().postDelayed(this::showKeyboard,240);
            } else {
                keyboardPending=false;
                keyboardAttempts=0;
                Toast.makeText(this,"Tipkovnica nije otvorena. Probaj ⋮ → Tekst → Wine.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * A text dialog uses a genuine Android EditText instead of relying on
     * the non-editable X11 framebuffer receiving IME composition events.
     * It submits only after the dialog closes and X11 has focus again.
     * Keystrokes are delivered to the guest; typed text is never logged.
     */
    private void openTextFallback() {
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("Upiši tekst za Wine prozor");
        new AlertDialog.Builder(this)
                .setTitle("Tekst → Wine")
                .setMessage("Unos za aktivni Wine prozor; dijakritici ovise o X11 tipkovnici.")
                .setView(input)
                .setNegativeButton("Odustani",null)
                .setPositiveButton("Pošalji",(d,which)->{
                    final String value=input.getText().toString();
                    xserver.getScreen().postDelayed(()->sendTextToWine(value),200);
                }).show();
    }

    private void sendTextToWine(String value) {
        if(destroyed || value.isEmpty() || xserver.mappedWindowCount()==0) {
            if(!value.isEmpty())Toast.makeText(this,"Wine prozor nije spreman.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        KeyEvent[] events=KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .getEvents(value.toCharArray());
        if(events==null) {
            Toast.makeText(this,"Neki znakovi nisu podržani u Android → X11 rasporedu.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        xserver.getScreen().requestFocus();
        int delivered=0;
        for(KeyEvent event:events) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                xserver.getScreen().onKeyDown(event.getKeyCode(),event);
                delivered++;
            } else if(event.getAction()==KeyEvent.ACTION_UP) {
                xserver.getScreen().onKeyUp(event.getKeyCode(),event);
            }
        }
        log("X11 manual-text fallback delivered "+delivered+" keystrokes; content not logged.");
    }

    private void shareGuiLog() {
        if(raw==null){Toast.makeText(this,"Log se još priprema",Toast.LENGTH_SHORT).show();return;}
        if(android.os.Build.VERSION.SDK_INT<29){openSaveAs();return;}
        if(!shareLog.isEnabled())return;
        shareLog.setEnabled(false);shareLog.setText("Pripremam…");
        new Thread(()->{
            try {
                Intent intent=GuiLogExport.share(this,this::writeDiagnosticLog);
                runOnUiThread(()->{
                    if(destroyed)return;
                    shareLog.setEnabled(true);shareLog.setText("Pošalji log");
                    try {startActivity(Intent.createChooser(intent,"Pošalji DRNAS log"));}
                    catch(Exception e){Toast.makeText(this,"Log je spremljen u Downloads/DRNAS. Dijeljenje nije dostupno.",Toast.LENGTH_LONG).show();}
                });
            } catch(Exception e) {
                log("Log share failed: "+e.getMessage());
                runOnUiThread(()->{
                    if(destroyed)return;
                    shareLog.setEnabled(true);shareLog.setText("Pošalji log");
                    openSaveAs();
                });
            }
        },"drnas-log-share").start();
    }

    private void writeDiagnosticLog(OutputStream out) throws IOException {
        String header="DRNAS desktop diagnostics\nApp: "+getPackageName()+
                "\nDevice: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+
                "\nAndroid API: "+android.os.Build.VERSION.SDK_INT+
                "\nDesktop launch requested: "+desktopLaunchRequested+
                "\nDesktop failed: "+desktopFailed+
                "\nVerbose logging: "+verboseLogs+" (Wine/DXVK native verbosity set at desktop launch)\n\n";
        out.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AndroidCrashDiagnostics.appendExitHistory(this,out);
        DiagnosticLogBundle.write(getFilesDir(),raw,out);
    }

    private void saveGuiLog() {
        if(raw==null){Toast.makeText(this,"No GUI log yet",Toast.LENGTH_SHORT).show();return;}
        if(android.os.Build.VERSION.SDK_INT<29){openSaveAs();return;}
        Toast.makeText(this,"Saving GUI TXT to Downloads/DRNAS…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try {
                String name=GuiLogExport.save(this,this::writeDiagnosticLog);
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
                writeDiagnosticLog(out);
            } catch(IOException e){log("GUI export failed: "+e.getMessage());}
        },"drnas-desktop-save-as").start();
    }

    // Android USB/Bluetooth pads use the same XInput state as the touch overlay.
    // Recognized physical gamepad buttons are consumed; keyboard remains X11.
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(xinputState!=null && isControllerSource(event.getSource())) {
            int bit=0;
            switch(event.getKeyCode()) {
                case KeyEvent.KEYCODE_BUTTON_A:bit=XInputSharedState.A;break;
                case KeyEvent.KEYCODE_BUTTON_B:bit=XInputSharedState.B;break;
                case KeyEvent.KEYCODE_BUTTON_X:bit=XInputSharedState.X;break;
                case KeyEvent.KEYCODE_BUTTON_Y:bit=XInputSharedState.Y;break;
                case KeyEvent.KEYCODE_BUTTON_L1:bit=XInputSharedState.LB;break;
                case KeyEvent.KEYCODE_BUTTON_R1:bit=XInputSharedState.RB;break;
                case KeyEvent.KEYCODE_BUTTON_START:bit=XInputSharedState.START;break;
                case KeyEvent.KEYCODE_BUTTON_SELECT:bit=XInputSharedState.BACK;break;
                case KeyEvent.KEYCODE_BUTTON_THUMBL:bit=XInputSharedState.LTHUMB;break;
                case KeyEvent.KEYCODE_BUTTON_THUMBR:bit=XInputSharedState.RTHUMB;break;
                case KeyEvent.KEYCODE_DPAD_UP:bit=XInputSharedState.DPAD_UP;break;
                case KeyEvent.KEYCODE_DPAD_DOWN:bit=XInputSharedState.DPAD_DOWN;break;
                case KeyEvent.KEYCODE_DPAD_LEFT:bit=XInputSharedState.DPAD_LEFT;break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:bit=XInputSharedState.DPAD_RIGHT;break;
                default:break;
            }
            boolean pressed=event.getAction()==KeyEvent.ACTION_DOWN;
            if(bit!=0) {
                if(event.getRepeatCount()==0 || !pressed)xinputState.setPhysicalButton(bit,pressed);
                return true;
            }
            if(event.getKeyCode()==KeyEvent.KEYCODE_BUTTON_L2) {
                xinputState.setPhysicalTrigger(true,pressed?255:0);return true;
            }
            if(event.getKeyCode()==KeyEvent.KEYCODE_BUTTON_R2) {
                xinputState.setPhysicalTrigger(false,pressed?255:0);return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private boolean isControllerSource(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD ||
               (source & InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK;
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if(xinputState!=null &&
                (event.getSource() & InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK &&
                event.getAction()==MotionEvent.ACTION_MOVE) {
            float rx=event.getAxisValue(MotionEvent.AXIS_RX);
            float ry=event.getAxisValue(MotionEvent.AXIS_RY);
            if(rx==0f && ry==0f) {
                rx=event.getAxisValue(MotionEvent.AXIS_Z);
                ry=event.getAxisValue(MotionEvent.AXIS_RZ);
            }
            float lt=Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                              event.getAxisValue(MotionEvent.AXIS_BRAKE));
            float rt=Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                              event.getAxisValue(MotionEvent.AXIS_GAS));
            xinputState.setPhysicalAxis(event.getAxisValue(MotionEvent.AXIS_X),
                    event.getAxisValue(MotionEvent.AXIS_Y),rx,ry,lt,rt);
            float hatX=event.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hatY=event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            xinputState.setPhysicalButton(XInputSharedState.DPAD_LEFT,hatX<-.6f);
            xinputState.setPhysicalButton(XInputSharedState.DPAD_RIGHT,hatX>.6f);
            xinputState.setPhysicalButton(XInputSharedState.DPAD_UP,hatY<-.6f);
            xinputState.setPhysicalButton(XInputSharedState.DPAD_DOWN,hatY>.6f);
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused) {immersive();if(keyboardPending)xserver.getScreen().post(this::showKeyboard);}
    }

    @Override protected void onPause() {
        if(virtualStick!=null)virtualStick.releaseAll();
        if(xinputLeft!=null)xinputLeft.release();
        if(xinputRight!=null)xinputRight.release();
        if(relativeLookOverlay!=null)relativeLookOverlay.release();
        if(xinputState!=null){xinputState.releaseTouch();xinputState.disconnectPhysical();}
        log("DRNAS_X11_LIFECYCLE onPause finishing="+isFinishing()+
                " changingConfigurations="+isChangingConfigurations());
        super.onPause();
    }
    @Override protected void onStop() {
        log("DRNAS_X11_LIFECYCLE onStop finishing="+isFinishing()+
                " changingConfigurations="+isChangingConfigurations());
        super.onStop();
    }
    @Override protected void onDestroy() {
        log("DRNAS_X11_LIFECYCLE onDestroy finishing="+isFinishing()+
                " changingConfigurations="+isChangingConfigurations()+
                " session="+(session!=null)+" xserver="+(xserver!=null));
        destroyed=true;
        frameCapReady.countDown();
        if(performanceHud!=null)performanceHud.removeCallbacks(hudTicker);
        if(xinputState!=null)xinputState.close();
        AutomaticCrashLog.end(this);
        if(session!=null)new Thread(()->{session.close();if(xserver!=null)xserver.stop();},"drnas-desktop-stop").start();
        else if(xserver!=null)xserver.stop();
        super.onDestroy();
    }
}


