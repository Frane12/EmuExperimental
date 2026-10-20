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
    private StartupAnimationView startupAnimation;
    private LinearLayout blenderStartupPanel;
    private View blenderStartupBrand;
    private int blenderRenderExitSequence;
    private ProgressBar blenderStartupBar;
    private TextView blenderStartupPercent;
    private int blenderBootMilestone;
    private final ExitConfirmation exitConfirmation=new ExitConfirmation();
    private LinearLayout dependencyPanel;
    private TextView dependencyProgressText;
    private ProgressBar dependencyProgressBar;
    private boolean cpuCanvasFallback;
    private volatile boolean verboseLogs; // OFF by default, live menu toggle
    private VirtualStickView virtualStick;
    private XInputSharedState xinputState;
    private DrnasPadLayoutView gamepadLayer;
    private boolean virtualPadEnabled;
    private float cameraSensitivity=7.5f;
    private CursorOverlayView desktopCursorOverlay;
    private boolean stretchFullscreen=true;
    private void syncVirtualPadConnection() {
        if(gamepadLayer!=null) {
            gamepadLayer.setButtonsVisible(virtualPadEnabled);
            gamepadLayer.setCameraEnabled(relativeCameraEnabled);
            gamepadLayer.setCameraGain(cameraSensitivity);
            gamepadLayer.setVisibility(virtualPadEnabled || relativeCameraEnabled ?
                    View.VISIBLE:View.GONE);
        }
        if(xinputState!=null)xinputState.setVirtualEnabled(
                virtualPadEnabled || relativeCameraEnabled);
    }
    private volatile boolean desktopLaunchRequested;
    private volatile long desktopLaunchStarted;
    private boolean desktopWindowWarning;
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
    private int chosenFrameCap;
    private String launchProfileId;
    private boolean renderCaptureRunning;
    private boolean blenderShell;
    private static final int BLENDER_HOST_TITLE_CROP=23;
    private boolean blenderHostFullscreen=true;
    private int guestWidth=WineGuiSession.DESKTOP_WIDTH,guestHeight=WineGuiSession.DESKTOP_HEIGHT;
    private BlenderKeyPanel blenderKeys;
    private BlenderZoomPanel blenderZoom;
    private BlenderSmoothZoomBridge blenderSmoothZoom;
    private BlenderAndroidExport.Project pendingBlenderAndroidProject;
    private static final int BLENDER_ANDROID_EXPORT_REQUEST=602;
    private boolean relativeCameraEnabled;
    private final Runnable hudTicker=new Runnable() {
        @Override public void run() {
            if(destroyed||performanceHud==null||!performanceHud.isActive())return;
            performanceHud.sample(xserver.getScreen().getDisplayedFps(),
                    xserver.getScreen().getDisplayedFrameMs());
            performanceHud.postDelayed(this,650);
        }
    };

    private android.os.HandlerThread diagnosticThread;
    private android.os.HandlerThread blenderFullscreenBridgeThread;
    private volatile long diagnosticUiBeat;
    private int diagnosticSamples, diagnosticStackSamples;
    private void startInputDiagnostics() {
        diagnosticUiBeat=android.os.SystemClock.uptimeMillis();
        if(diagnosticThread!=null)return;
        diagnosticThread=new android.os.HandlerThread("drnas-input-watchdog");
        diagnosticThread.start();
        android.os.Handler worker=new android.os.Handler(diagnosticThread.getLooper());
        android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
        java.util.concurrent.atomic.AtomicBoolean uiPingPending=new java.util.concurrent.atomic.AtomicBoolean();
        log("DRNAS_X11_CRITICAL_DIAGNOSTICS r57 enabled=true verboseIndependent=true intervalMs=2000 maxSamples=180");
        worker.post(new Runnable() {
            @Override public void run() {
                if(destroyed || diagnosticSamples++>=180)return;
                long now=android.os.SystemClock.uptimeMillis();
                long uiAge=now-diagnosticUiBeat;
                if(uiPingPending.compareAndSet(false,true))ui.post(()->{
                    diagnosticUiBeat=android.os.SystemClock.uptimeMillis();
                    uiPingPending.set(false);
                });
                log("DRNAS_X11_CRITICAL_PROGRESS timeMs="+now+" uiAgeMs="+uiAge+" "+
                        xserver.getScreen().diagnosticInputSnapshot()+" "+xserver.diagnosticProgressSnapshot());
                if(uiAge>5000 && diagnosticStackSamples++<3) {
                    StringBuilder stack=new StringBuilder();
                    for(StackTraceElement e:android.os.Looper.getMainLooper().getThread().getStackTrace()) {
                        if(stack.length()>2200)break;
                        stack.append(" | ").append(e);
                    }
                    log("DRNAS_X11_CRITICAL_UI_STALL stack="+stack);
                }
                worker.postDelayed(this,2000);
            }
        });
    }

    // Poll a tiny app-private command file off Android's UI and X11 threads.
    // Native Blender/Wine fullscreen remains untouched; this toggles only
    // Android's existing compositor crop and its matched cursor/input map.
    private void startBlenderFullscreenBridge() {
        final java.nio.file.Path signal=BlenderFullscreenSignal.path(
                WineShortcuts.prefix(this));
        try {
            BlenderFullscreenSignal.clearStale(signal);
        } catch(java.io.IOException error) {
            log("DRNAS_BLENDER_FULLSCREEN_BRIDGE_CLEAR_FAILED "+error.getMessage());
        }
        blenderFullscreenBridgeThread=new android.os.HandlerThread("drnas-blender-fullscreen-bridge");
        blenderFullscreenBridgeThread.start();
        android.os.Handler worker=new android.os.Handler(blenderFullscreenBridgeThread.getLooper());
        worker.postDelayed(new Runnable() {
            int lastSequence=0;
            int failures=0;
            @Override public void run() {
                if(destroyed)return;
                try {
                    int sequence=BlenderFullscreenSignal.readSequence(signal);
                    if(sequence>lastSequence) {
                        int delta=sequence-lastSequence;
                        lastSequence=sequence;
                        log("DRNAS_BLENDER_FULLSCREEN_BRIDGE r57 seq="+sequence+
                                " delta="+delta+" change="+((delta&1)!=0));
                        if((delta&1)!=0)runOnUiThread(()->{
                            if(!destroyed && blenderShell)toggleBlenderHostFullscreen();
                        });
                    }
                } catch(java.io.IOException | SecurityException error) {
                    if(failures++<4)log("DRNAS_BLENDER_FULLSCREEN_BRIDGE_ERROR "+error.getMessage());
                }
                if(!destroyed)worker.postDelayed(this,350);
            }
        },350);
    }

    // The percentage is a conservative, monotonic *boot milestone*, not a
    // guessed countdown or timed animation. 100% means Blender itself mapped.
    private void updateBlenderBootProgress(int milestone) {
        if(!blenderShell || blenderStartupBar==null || desktopFailed)return;
        int p=Math.max(blenderBootMilestone,Math.min(100,Math.max(0,milestone)));
        if(p==blenderBootMilestone && blenderStartupPercent!=null)return;
        blenderBootMilestone=p;
        blenderStartupBar.setProgress(p);
        if(blenderStartupPercent!=null)blenderStartupPercent.setText(p+"%");
    }
    private int blenderBootStage(String message) {
        if(message.startsWith("[Startup] Blender unpacking ")) {
            try {
                String part=message.substring("[Startup] Blender unpacking ".length());
                int end=part.indexOf('%');
                return 5+Math.max(0,Math.min(100,Integer.parseInt(part.substring(0,end))))*35/100;
            }catch(RuntimeException ignore){return 6;}
        }
        if(message.startsWith("[Startup] Priprema Windows okruženja"))return 3;
        if(message.startsWith("Pripremam disk R:"))return 45;
        if(message.startsWith("[Startup] Pokretanje Wine servisa"))return 55;
        if(message.startsWith("[Startup] Provjera Wine dodataka"))return 62;
        if(message.startsWith("[Startup] Provjera offline DirectX / VC++ paketa"))return 69;
        if(message.startsWith("[Startup] Priprema Windows pozadine"))return 85;
        if(message.startsWith("[Startup] Pokretanje Explorera") ||
                message.startsWith("[Startup] Pokretanje Blendera") ||
                message.startsWith("Launching Wine explorer"))return 92;
        if(message.startsWith("[Dependencies] ")) {
            int separator=message.indexOf('|',15);
            if(separator>0) {
                try {return 69+Math.max(0,Math.min(100,
                        Integer.parseInt(message.substring(15,separator).trim())))*15/100;}
                catch(NumberFormatException ignore) {return 69;}
            }
        }
        return -1;
    }

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
        // Include camera-hole/notch safe inset in landscape. The X11 desktop
        // remains 1280x720; ScreenView scales to the *actual* full View area.
        if(android.os.Build.VERSION.SDK_INT>=28) {
            WindowManager.LayoutParams attributes=getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode=
                    android.os.Build.VERSION.SDK_INT>=30?
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS:
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        if(android.os.Build.VERSION.SDK_INT>=30)
            getWindow().setDecorFitsSystemWindows(false);
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
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->exitConfirmation.back(this));
        // Keep toggle through Reset Wine (which recreates this Activity).
        verboseLogs=getPreferences(MODE_PRIVATE).getBoolean("verbose_diagnostics",false);
        String profileRequest=getIntent().getStringExtra("DRNAS_GUI_APP");
        blenderShell=BlenderShellLaunch.ID.equals(profileRequest);
        if(blenderShell) {
            BlenderDisplayProfile.Resolution resolution=BlenderDisplayProfile.choose(
                    NativeHost.vulkanDeviceName(),getSharedPreferences("blender-display",MODE_PRIVATE)
                            .getString("resolution","auto"));
            guestWidth=resolution.width;guestHeight=resolution.height;
        }
        // A saved debugging session must not add verbose Wine/X11 I/O to the
        // app preview or the performance baseline. Essential crash logs stay on.
        if(blenderShell)verboseLogs=false;
        launchProfileId=WineShortcuts.isLaunchRequest(profileRequest)?profileRequest:null;
        AppLaunchSettings.Profile appProfile=AppLaunchSettings.load(this,launchProfileId);
        chosenFrameCap=appProfile.fps(savedFrameCap(this));
        final int startupFrameCap=chosenFrameCap;
        relativeCameraEnabled=getPreferences(MODE_PRIVATE).getBoolean("relative_camera",false);
        String controlMode=getPreferences(MODE_PRIVATE).getString("control_mode","off");
        virtualPadEnabled="xinput".equals(controlMode);
        cameraSensitivity=appProfile.sensitivity(getPreferences(MODE_PRIVATE).getFloat("relative_sensitivity",7.5f));
        stretchFullscreen=appProfile.stretch(getPreferences(MODE_PRIVATE).getBoolean("full_screen_stretch",true));
        if(blenderShell){stretchFullscreen=true;relativeCameraEnabled=false;virtualPadEnabled=false;controlMode="off";}

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
        // A full 1280x720 PutImage was observed in the last render sample.
        // The prior 707-row mask did not fix the artifact, so opt in only.
        xserver.setPresentationMaskEnabled(getPreferences(MODE_PRIVATE)
                .getBoolean("x11_presentation_mask",false));
        // Keep cheap PutImage frame accounting available even in quiet Blender
        // sessions. A mapped HWND by itself does not prove a Vulkan frame.
        xserver.setFrameDiagnosticsEnabled(verboseLogs);
        xserver.setExperimentalBulkDecode(getPreferences(MODE_PRIVATE)
                .getBoolean("x11_bulk_decoder",false));
        xserver.getScreen().setGuestResolution(
                guestWidth,guestHeight);
        xserver.getScreen().setStretchFullscreen(stretchFullscreen);
        if(blenderShell)xserver.getScreen().setHostCropTopGuestPx(
                blenderHostFullscreen?BLENDER_HOST_TITLE_CROP:0);
        xserver.getScreen().setRelativeTouchMode(true);
        // r52 Blender input hotfix: Android must never wait for Wine/X11
        // under the global XServer lock when a touch, click or key arrives.
        xserver.getScreen().setNonBlockingInputEnabled(blenderShell);
        xserver.getScreen().setInputDebugEnabled(verboseLogs);
        xserver.getScreen().setCameraTouchMode(false);
        // GPU-backed Android HWUI compositing of Wine's CPU X11 bitmaps.
        // This does NOT implement X11 DRI3/Present or Wine Vulkan WSI.
        xserver.getScreen().setLayerType(View.LAYER_TYPE_NONE,null);
        logEarlyRendererIntent();
        xserver.getScreen().setFocusableInTouchMode(true);
        xserver.getScreen().setPointerIcon(android.view.PointerIcon.getSystemIcon(
                this,android.view.PointerIcon.TYPE_NULL));
        layout.addView(xserver.getScreen(),new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Cursor is a separate accelerated Android layer. Dragging the pointer
        // no longer requests a redraw of every mapped Wine window bitmap.
        desktopCursorOverlay=new CursorOverlayView(this,
                guestWidth,guestHeight);
        desktopCursorOverlay.setStretchFullscreen(stretchFullscreen);
        if(blenderShell)desktopCursorOverlay.setHostCropTopGuestPx(
                blenderHostFullscreen?BLENDER_HOST_TITLE_CROP:0);
        desktopCursorOverlay.setBlenderVisibleCursor(blenderShell);
        desktopCursorOverlay.setDiagnosticSink(verboseLogs?this::log:null);
        xserver.getScreen().setCursorOverlayListener(desktopCursorOverlay::setCursor);
        desktopCursorOverlay.setClickable(false);
        desktopCursorOverlay.setFocusable(false);
        layout.addView(desktopCursorOverlay,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // The same full-screen controller view is used by the editor and by
        // runtime. All 18 controls can be repositioned in XML. It is below
        // the top-right menu and HUD, so these controls remain clickable.
        if(xinputState!=null) {
            gamepadLayer=new DrnasPadLayoutView(this,xinputState,false);
            gamepadLayer.setPreset(XInputLayoutPreset.load(this,
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels));
            layout.addView(gamepadLayer,new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Quiet startup notice, not diagnostics. An offline Mono installation
        // may need time before the Wine desktop maps its first X11 window.
        startupAnimation=new StartupAnimationView(this,blenderShell);
        layout.addView(startupAnimation,new FrameLayout.LayoutParams(-1,-1));
        startupStatus=new TextView(this);
        startupStatus.setText(blenderShell?"Starting Blender":"Priprema Windows desktopa…");
        startupStatus.setTextColor(0xFFE0EFF8);
        startupStatus.setTextSize(blenderShell?19:14);
        startupStatus.setPadding(dp(24),dp(8),dp(24),dp(8));
        startupStatus.setGravity(Gravity.CENTER);
        startupStatus.setBackgroundColor(0x00000000);
        if(blenderShell) {
            blenderStartupPanel=new LinearLayout(this);
            blenderStartupPanel.setOrientation(LinearLayout.VERTICAL);
            blenderStartupPanel.setGravity(Gravity.CENTER);
            blenderStartupPanel.setPadding(dp(18),dp(12),dp(18),dp(12));
            blenderStartupPanel.addView(startupStatus);
            blenderStartupBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            blenderStartupBar.setIndeterminate(false);
            blenderStartupBar.setMax(100);
            blenderStartupBar.setProgress(0);
            blenderStartupBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff8cf5d0));
            LinearLayout.LayoutParams barParams=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,dp(8));
            barParams.topMargin=dp(10);
            blenderStartupPanel.addView(blenderStartupBar,barParams);
            blenderStartupPercent=new TextView(this);
            blenderStartupPercent.setText("0%");
            blenderStartupPercent.setTextColor(0xffc7dedf);
            blenderStartupPercent.setTextSize(13);
            blenderStartupPercent.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams percentParams=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            percentParams.topMargin=dp(9);
            blenderStartupPanel.addView(blenderStartupPercent,percentParams);
            layout.addView(blenderStartupPanel,new FrameLayout.LayoutParams(
                    Math.min(dp(420),getResources().getDisplayMetrics().widthPixels-dp(36)),
                    ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.CENTER));
            LinearLayout brand=new LinearLayout(this);
            brand.setGravity(Gravity.CENTER_VERTICAL);brand.setAlpha(0.72f);
            android.widget.ImageView badge=new android.widget.ImageView(this);
            badge.setImageResource(R.drawable.ic_drnas_launcher);
            brand.addView(badge,new LinearLayout.LayoutParams(dp(18),dp(18)));
            TextView wordmark=new TextView(this);
            wordmark.setText("EMUDRNAS");wordmark.setTextSize(11);
            wordmark.setTextColor(0xffc9d3df);wordmark.setPadding(dp(5),0,0,0);
            brand.addView(wordmark);
            FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.BOTTOM);
            corner.rightMargin=dp(12);corner.bottomMargin=dp(12);
            layout.addView(brand,corner);
            blenderStartupBrand=brand;
        } else {
            layout.addView(startupStatus,new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.CENTER));
        }

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
        if(blenderShell)menu.setVisibility(View.GONE);
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
        virtualStick.setVisibility("wasd".equals(controlMode)?View.VISIBLE:View.GONE);
        layout.addView(virtualStick,stickParams);
        syncVirtualPadConnection();
        performanceHud=new DrnasPerformanceHud(this);
        performanceHud.setCompactFps(blenderShell);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(
                blenderShell?dp(150):Math.min(dp(620),getResources().getDisplayMetrics().widthPixels-dp(100)),
                dp(blenderShell?26:32),Gravity.TOP|(blenderShell?Gravity.END:Gravity.CENTER_HORIZONTAL));
        hp.topMargin=dp(blenderShell?38:5);
        if(blenderShell)hp.rightMargin=dp(10);
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
        if(!blenderShell)layout.addView(gestureEdge,gp);
        setToolbarExpanded(false);
        performanceHud.setActive(getPreferences(MODE_PRIVATE).getBoolean(
                blenderShell?"blender_perf_hud":"perf_hud",blenderShell));
        xserver.getScreen().setFpsCounterEnabled(performanceHud.isActive()&&!blenderShell);
        if(blenderShell)performanceHud.setVisibility(View.GONE);
        if(blenderShell) {
            log("DRNAS_BLENDER_HOST_FULLSCREEN r57 enabled="+blenderHostFullscreen+
                    " cropGuestTop="+(blenderHostFullscreen?BLENDER_HOST_TITLE_CROP:0)+
                    " nativeWineFullscreen=false");
            blenderKeys=new BlenderKeyPanel(this,(key,pressed)->{
                if(destroyed||xserver==null)return;
                xserver.getScreen().requestFocus();
                KeyEvent event=new KeyEvent(pressed?KeyEvent.ACTION_DOWN:KeyEvent.ACTION_UP,key);
                if(pressed)xserver.getScreen().onKeyDown(key,event);
                else xserver.getScreen().onKeyUp(key,event);
            },(button,pressed)->{
                if(destroyed||xserver==null)return;
                // Send a regular X11 click at the current pointer coordinates;
                // ScreenView's independent touchscreen left-click stays active.
                xserver.getScreen().setOverlayPointerButton(button,pressed);
            },()->{keyboardPending=true;keyboardAttempts=0;showKeyboard();},
              this::showBlenderMenu,this::showBlenderProjectFolder,
              this::returnFromBlenderRender);
            layout.addView(blenderKeys,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START));
            // No extra controls over the simple "Starting Blender" boot screen.
            // Full keyboard + hotkey panel become available once Blender maps.
            blenderKeys.setVisibility(View.GONE);
            // Independent, collapsible viewport zoom control; does not alter
            // keyboard size or force native Wine fullscreen/3D window reflow.
            blenderSmoothZoom=new BlenderSmoothZoomBridge(
                    BlenderSmoothZoomSignal.path(WineShortcuts.prefix(this)),this::log);
            blenderSmoothZoom.start();
            blenderZoom=new BlenderZoomPanel(this,(button,pressed)->{
                if(destroyed||xserver==null)return;
                xserver.getScreen().setOverlayPointerButton(button,pressed);
            },delta->{
                if(destroyed||blenderSmoothZoom==null)return;
                blenderSmoothZoom.offerDelta(delta);
            });
            FrameLayout.LayoutParams zoomLayout=new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END|Gravity.CENTER_VERTICAL);
            zoomLayout.rightMargin=dp(8);
            layout.addView(blenderZoom,zoomLayout);
            blenderZoom.setVisibility(View.GONE);
        } else if(getPreferences(MODE_PRIVATE).getBoolean("desktop_keys",true))addDesktopKeys(layout);
        AppLanguage.tree(layout);
        setContentView(layout);
        if(performanceHud.isActive()&&!blenderShell)performanceHud.postDelayed(hudTicker,650);
        try {
            AutomaticCrashLog.begin(this);
            raw=new RawWineTrace(new File(getFilesDir(),"logs"),"latest-wine-gui-raw.txt");
            raw.setVerbose(verboseLogs);
        } catch(IOException e) {
            AppLanguage.toast(this,"Wine desktop log unavailable: "+e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        session=new WineGuiSession();
        session.setDiagnosticsEnabled(verboseLogs);
        session.setDxvkVariant(appProfile.dxvk(DxvkVariants.selected(this)));
        String requested=getIntent().getStringExtra("DRNAS_GUI_APP");
        // Default always starts Explorer, never Wine configuration.
        final String launchApp="files".equals(requested) ? "files":
                "config".equals(requested) ? "config":
                WineShortcuts.isLaunchRequest(requested) ? requested:"desktop";
        xserver.setDiagnosticListener(this::log);
        if(blenderShell) {
            // Keep the low-frequency fullscreen bridge, but NEVER launch
            // the 2s stack/watchdog diagnostics in normal usage.
            if(verboseLogs)startInputDiagnostics();
            startBlenderFullscreenBridge();
        }
        xserver.setOnStartListener(new XServer.OnXSeverStartListener(){
            @Override public void onStart() {
                if(started || destroyed)return;
                started=true;
                log("X11 live; guest resolution "+
                        xserver.getScreen().getGuestWidth()+"x"+
                        xserver.getScreen().getGuestHeight());
                new Thread(()->{
                    try {
                        // Snapshot the saved preference for this display session; edits apply on reset.
                        if(destroyed)return;
                        session.setFrameCap(startupFrameCap);
                        session.run(WineGuiActivity.this,WineGuiActivity.this::log,raw,launchApp);
                    }
                    catch(Throwable e) {
                        desktopFailed=true;
                        log("Desktop stopped: "+e.getClass().getSimpleName()+": "+e.getMessage());
                        runOnUiThread(()->{
                            if(!destroyed) {
                                startupStatus.setText((blenderShell?"Blender startup failed. ":"Windows se nije pokrenuo. ")+
                                        e.getClass().getSimpleName()+": "+e.getMessage());
                                startupAnimation.stop();
                                startupStatus.setVisibility(View.VISIBLE);
                                // On failure the diagnostic export must be accessible.
                                if(blenderShell && shareLog!=null)shareLog.setVisibility(View.VISIBLE);
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
                if(destroyed || startupStatus==null || desktopFailed)return;
                if(!desktopFailed && desktopLaunchRequested &&
                        (blenderShell?xserver.hasMappedBlenderPrimary():xserver.mappedWindowCount()>0)) {
                    if(blenderShell)updateBlenderBootProgress(100);
                    else startupStatus.setVisibility(View.GONE);
                    dependencyPanel.setVisibility(View.GONE);
                    if(blenderShell) {
                        xserver.getScreen().postDelayed(()->{
                            if(destroyed)return;
                            if(blenderStartupPanel!=null)blenderStartupPanel.setVisibility(View.GONE);
                            if(blenderStartupBrand!=null)blenderStartupBrand.setVisibility(View.GONE);
                            startupAnimation.stop();startupAnimation.setVisibility(View.GONE);
                            if(blenderKeys!=null)blenderKeys.setVisibility(View.VISIBLE);
                            if(blenderZoom!=null)blenderZoom.setVisibility(View.VISIBLE);
                            if(performanceHud.isActive()) {
                                performanceHud.setVisibility(View.VISIBLE);
                                xserver.getScreen().setFpsCounterEnabled(true);
                                performanceHud.post(hudTicker);
                            }
                        },250);
                    } else {
                        startupAnimation.stop();startupAnimation.setVisibility(View.GONE);
                    }
                    log("DRNAS_DESKTOP_CONFIRMED: "+(blenderShell?"Blender primary mapped; Android immersive active; r50 guest window policy restored; Win32 fullscreen timer disabled":"mapped X11 window after Explorer launch"));
                }
                else {
                    if(desktopLaunchRequested && desktopLaunchStarted>0 && !desktopWindowWarning &&
                            android.os.SystemClock.uptimeMillis()-desktopLaunchStarted>=90000){
                        desktopWindowWarning=true;startupAnimation.stop();
                        startupStatus.setText(blenderShell?"Blender startup timed out. Open log to inspect.":
                                "Windows prozor nije potvrđen nakon 90 s.\n"+
                                "⋮ → Pošalji log ili Reset Wine. Provjera dodataka nije potvrda desktopa.");
                        if(blenderShell && shareLog!=null)shareLog.setVisibility(View.VISIBLE);
                        log("DRNAS_DESKTOP_NOT_CONFIRMED: 90s since Explorer request; X11 mapped window absent. Session kept for log/reset or late window mapping.");
                    }
                    xserver.getScreen().postDelayed(this,700);
                }
            }
        },700);
        // Report absence of a mapped window only to the private session log.
        xserver.getScreen().postDelayed(()->{
            if(!destroyed && !desktopFailed && (!desktopLaunchRequested || xserver.mappedWindowCount()==0)) {
                log("No Wine desktop confirmed after 20 seconds. On-screen log sharing remains available.");
                // Preserve the actual startup phase, including long installer waits.
                startupStatus.setVisibility(View.VISIBLE);
            }
        },20000);
        // Start directly with the saved cap. The configuration menu is the only editor.
    }

    // Preserve the existing Activity preference file so updates keep the user's cap.
    static int savedFrameCap(android.content.Context context) {
        return DxvkFrameCap.clamp(context.getSharedPreferences("WineGuiActivity",MODE_PRIVATE)
                .getInt("dxvk_fps_cap",0));
    }
    static void saveFrameCap(android.content.Context context,int cap) {
        context.getSharedPreferences("WineGuiActivity",MODE_PRIVATE).edit()
                .putInt("dxvk_fps_cap",DxvkFrameCap.clamp(cap)).apply();
    }

    private void setFrameCapAndContinue(int cap,boolean beforeStartup) {
        chosenFrameCap=DxvkFrameCap.clamp(cap);
        if(launchProfileId==null)saveFrameCap(this,chosenFrameCap);
        else saveLaunchProfile(AppLaunchSettings.load(this,launchProfileId).withFps(chosenFrameCap));
        AppLanguage.toast(this,"DXVK "+(chosenFrameCap==0?"bez limita":chosenFrameCap+" FPS")+
                (beforeStartup?"":" (primijenit će se nakon Reset Wine)"),
                Toast.LENGTH_SHORT).show();
    }

    private void showFrameLimiterDialog(boolean beforeStartup) {
        if(destroyed)return;
        // Never combine AlertDialog.setMessage with setItems: Android drops
        // the choices when both are set. Render explicit buttons + edit box.
        DrnasDxvkDialog.show(this,chosenFrameCap,beforeStartup,
                cap->setFrameCapAndContinue(cap,beforeStartup),
                ()->{});
    }

    private static final String[] GLASS_MENU_ITEMS={"Keyboard","Toggle CPU/GPU desktop Canvas","Reset Wine","Exit desktop","Pošalji log","Spremi log","Task Manager","Promijeni prozor (Alt+Tab)","Fullscreen / prozor (Alt+Enter)","Tekst → Wine (IME fallback)","Vulkan FIFO (izoliran)","Vulkan IMMEDIATE (izoliran)","WSI A: Create/Destroy bez Present","WSI B: FIFO + XSync","Eksperiment 1: X11 bez Vulkana","Eksperiment 2: Vulkan bez slike + provjera veze","Eksperiment 3: Prikaz slike + provjera veze","Virtual stick (WASD) ON/OFF","Dijagnostika / logovi: ON/OFF","Xbox XInput (virtual pad) ON/OFF","HUD FPS/RAM/BAT ON/OFF","Relativni miš / kamera ON/OFF","WSI prezentacijska maska ON/OFF","DXVK FPS · idući reset prikaza","Snimanje rendera — 5 sekundi","Osjetljivost kamere","Uredi XInput raspored / XML",
            "Eksperiment: brzo dekodiranje (A/B) ON/OFF",
            "Cijeli zaslon / Stretch ON/OFF",
            "DXVK 1.10.3 Async / Standard (idući start)",
            "Uredi 5 desktop tipki i položaj (spremi)"};
    private boolean glassMenuActive(int which) {
        switch(which) {
            case 1:return cpuCanvasFallback;
            case 17:return virtualStick!=null && virtualStick.getVisibility()==View.VISIBLE;
            case 18:return verboseLogs;
            case 19:return virtualPadEnabled;
            case 20:return performanceHud!=null && performanceHud.isActive();
            case 21:return relativeCameraEnabled;
            case 22:return getPreferences(MODE_PRIVATE).getBoolean("x11_presentation_mask",false);
            case 27:return xserver!=null && xserver.isExperimentalBulkDecode();
            case 28:return stretchFullscreen;
            case 29:return DxvkVariants.ASYNC.equals(AppLaunchSettings.load(this,launchProfileId).dxvk(DxvkVariants.selected(this)));
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
                        AppLanguage.toast(this,cpuCanvasFallback?
                                "CPU Canvas fallback":"GPU Canvas enabled",Toast.LENGTH_SHORT).show();
                    }
                    else if(which==2) {
                        Intent restart=new Intent(this,WineGuiActivity.class)
                                .putExtra("DRNAS_GUI_APP",getIntent().getStringExtra("DRNAS_GUI_APP"));
                        new Thread(()->{
                            try {
                                WineGuiSession.stopActive();
                                if(xserver!=null)xserver.stop();
                                runOnUiThread(()->{if(!destroyed){finish();startActivity(restart);}});
                            } catch(Exception e){runOnUiThread(()->AppLanguage.toast(this,"Wine is still stopping",Toast.LENGTH_LONG).show());}
                        },"drnas-desktop-reset").start();
                    } else if(which==3)exitConfirmation.confirm(this);
                    else if(which==4)shareGuiLog();
                    else if(which==5)saveGuiLog();
                    else if(which==6)openTaskManager();
                    else if(which==9)openTextFallback();
                    else if(which==20) {
                        boolean enable=!performanceHud.isActive();
                        getPreferences(MODE_PRIVATE).edit().putBoolean(
                                blenderShell?"blender_perf_hud":"perf_hud",enable).apply();
                        performanceHud.setActive(enable);
                        xserver.getScreen().setFpsCounterEnabled(enable);
                        performanceHud.removeCallbacks(hudTicker);
                        if(enable)performanceHud.post(hudTicker);
                        AppLanguage.toast(this,enable?"HUD ON":"HUD OFF",Toast.LENGTH_SHORT).show();
                    }
                    else if(which==21) {
                        relativeCameraEnabled=!relativeCameraEnabled;
                        getPreferences(MODE_PRIVATE).edit().putBoolean("relative_camera",relativeCameraEnabled).apply();
                        syncVirtualPadConnection();
                        AppLanguage.toast(this,relativeCameraEnabled?
                                "Kamera bilo gdje: XInput analog ON":"Kamera bilo gdje OFF (normalni dodir)",
                                Toast.LENGTH_SHORT).show();
                    }
                    else if(which==22) {
                        boolean enabled=!getPreferences(MODE_PRIVATE).getBoolean("x11_presentation_mask",false);
                        getPreferences(MODE_PRIVATE).edit().putBoolean("x11_presentation_mask",enabled).apply();
                        if(xserver!=null)xserver.setPresentationMaskEnabled(enabled);
                        AppLanguage.toast(this,enabled?"Maska prezentacije ON":"Maska prezentacije OFF (A/B test)",
                                Toast.LENGTH_SHORT).show();
                    }
                    else if(which==23) {
                        showFrameLimiterDialog(false);
                    }
                    else if(which==24) {
                        captureRenderForFiveSeconds();
                    }
                    else if(which==25) {
                        editCameraSensitivity();
                    }
                    else if(which==26) {
                        startActivity(new Intent(this,XInputLayoutEditorActivity.class));
                    }
                    else if(which==27) {
                        boolean fast=!xserver.isExperimentalBulkDecode();
                        xserver.setExperimentalBulkDecode(fast);
                        getPreferences(MODE_PRIVATE).edit()
                            .putBoolean("x11_bulk_decoder",fast).apply();
                        AppLanguage.toast(this,fast?
                                "Brzi BGRX dekoder ON — usporedi 5s uzorke":
                                "Brzi BGRX dekoder OFF — stabilni baseline",
                                Toast.LENGTH_LONG).show();
                    }
                    else if(which==29) {
                        String variant;
                        if(launchProfileId==null)variant=DxvkVariants.toggle(this);
                        else {
                            AppLaunchSettings.Profile profile=AppLaunchSettings.load(this,launchProfileId);
                            variant=DxvkVariants.ASYNC.equals(profile.dxvk(DxvkVariants.selected(this)))?
                                    DxvkVariants.STANDARD:DxvkVariants.ASYNC;
                            saveLaunchProfile(profile.withDxvk(variant));
                        }
                        log("DRNAS_DXVK_SELECTED_FOR_NEXT_START="+variant);
                        AppLanguage.toast(this,"DXVK 1.10.3 "+variant+
                                " — za promjenu u igri koristi Reset Wine",
                                Toast.LENGTH_LONG).show();
                    }
                    else if(which==30) {
                        editDesktopKeys();
                    }
                    else if(which==28) {
                        stretchFullscreen=!stretchFullscreen;
                        if(launchProfileId==null)getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("full_screen_stretch",stretchFullscreen).apply();
                        else saveLaunchProfile(AppLaunchSettings.load(this,launchProfileId).withStretch(stretchFullscreen));
                        xserver.getScreen().setStretchFullscreen(stretchFullscreen);
                        positionDesktopKeys();
                        if(desktopCursorOverlay!=null)
                            desktopCursorOverlay.setStretchFullscreen(stretchFullscreen);
                        log("DRNAS_DISPLAY_MODE "+(stretchFullscreen?"FILL":"FIT")+
                                " fixed guest "+guestWidth+"x"+guestHeight+", Android-only transform");
                        AppLanguage.toast(this,stretchFullscreen?
                                "Fullscreen ON — "+guestWidth+"×"+guestHeight+" stretched to screen edges":
                                "Puni zaslon OFF — izvorni omjer slike",
                                Toast.LENGTH_SHORT).show();
                    }
                    else if(which==19) {
                        if(xinputState==null) {
                            AppLanguage.toast(this,"XInput mapiranje nije dostupno.",Toast.LENGTH_LONG).show();
                        } else {
                            virtualPadEnabled=!virtualPadEnabled;
                            getPreferences(MODE_PRIVATE).edit().putString("control_mode",virtualPadEnabled?"xinput":"off").apply();
                            if(virtualPadEnabled && virtualStick.getVisibility()==View.VISIBLE) {
                                virtualStick.releaseAll();
                                virtualStick.setVisibility(View.GONE);
                            }
                            if(!virtualPadEnabled && gamepadLayer!=null)gamepadLayer.release();
                            syncVirtualPadConnection();
                            log("DRNAS_XINPUT "+(virtualPadEnabled?"VIRTUAL_CONNECTED":"VIRTUAL_DISCONNECTED"));
                            AppLanguage.toast(this,virtualPadEnabled?"XInput Xbox controller ON":"XInput Xbox controller OFF",Toast.LENGTH_SHORT).show();
                        }
                    }
                    else if(which==18) {
                        verboseLogs=!verboseLogs;
                        getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("verbose_diagnostics",verboseLogs).apply();
                        if(raw!=null)raw.setVerbose(verboseLogs);
                        if(session!=null)session.setDiagnosticsEnabled(verboseLogs);
                        if(xserver!=null) {
                            xserver.setFrameDiagnosticsEnabled(verboseLogs);
                            xserver.getScreen().setInputDebugEnabled(verboseLogs);
                        }
                        if(desktopCursorOverlay!=null)
                            desktopCursorOverlay.setDiagnosticSink(verboseLogs?this::log:null);
                        if(blenderShell && verboseLogs)startInputDiagnostics();
                        else if(blenderShell && diagnosticThread!=null) {
                            diagnosticThread.quitSafely();
                            diagnosticThread=null;
                        }
                        log("DRNAS_DIAGNOSTICS_TOGGLE "+(verboseLogs?"ON":"OFF")+
                                " | X11/raw logging changes live. Wine/DXVK verbose env applies after Reset Wine.");
                        AppLanguage.toast(this,verboseLogs?
                                "Logovi ON. Puni Wine/DXVK trace nakon Reset Wine.":
                                "Logovi OFF: prikaz bez dijagnostičkog opterećenja.",
                                Toast.LENGTH_LONG).show();
                    }
                    else if(which==17) {
                        boolean show=virtualStick.getVisibility()!=View.VISIBLE;
                        if(show && gamepadLayer!=null) {
                            virtualPadEnabled=false;
                            gamepadLayer.release();
                            syncVirtualPadConnection();
                        }
                        if(!show)virtualStick.releaseAll();
                        virtualStick.setVisibility(show?View.VISIBLE:View.GONE);
                        getPreferences(MODE_PRIVATE).edit().putString("control_mode",show?"wasd":"off").apply();
                        log("DRNAS_VIRTUAL_STICK "+(show?"ON":"OFF")+
                                " input=Wine X11 WASD (XInput controller NOT emulated)");
                        AppLanguage.toast(this,show?"WASD stick ON":"WASD stick OFF",Toast.LENGTH_SHORT).show();
                    }
                    else if(which>=14 && which<=16)
                        testVulkanPresentation(which==14?"x11-only":which==15?"create-checkpoints":"present-checkpoints");
                    else if(which>=10 && which<=13)
                        testVulkanPresentation(which==10?"fifo":which==11?"immediate":which==12?"create-only":"sync");
                    else sendAltKey(which==7?android.view.KeyEvent.KEYCODE_TAB:android.view.KeyEvent.KEYCODE_ENTER);
        });
    }

    private void saveLaunchProfile(AppLaunchSettings.Profile profile){
        try{AppLaunchSettings.save(this,launchProfileId,profile);}
        catch(Exception error){AppLanguage.toast(this,"Postavke nisu spremljene: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void restoreCameraSensitivity(float value){
        cameraSensitivity=value;
        if(gamepadLayer!=null)gamepadLayer.setCameraGain(value);
    }

    private void editCameraSensitivity(){
        final float originalGain=cameraSensitivity;
        android.widget.LinearLayout box=new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(dp(28),dp(12),dp(28),dp(8));
        android.widget.TextView label=new android.widget.TextView(this);
        label.setTextColor(0xff91efcc);
        label.setTextSize(15);
        label.setText(String.format(java.util.Locale.ROOT,"Osjetljivost kamere: %.1fx",cameraSensitivity));
        box.addView(label);
        android.widget.SeekBar slider=new android.widget.SeekBar(this);
        slider.setMax(95);
        slider.setProgress(Math.min(95,Math.max(0,Math.round(cameraSensitivity*10)-5)));
        box.addView(slider);
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(android.widget.SeekBar bar,int value,boolean fromUser){
                float gain=(value+5)/10f;
                label.setText(String.format(java.util.Locale.ROOT,"Osjetljivost kamere: %.1fx",gain));
                cameraSensitivity=gain;
                if(gamepadLayer!=null)gamepadLayer.setCameraGain(gain);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar){}
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar){}
        });
        new AppDialogBuilder(this).setTitle("XInput / relativna kamera")
                .setMessage("Pomak prsta određuje brzinu kamere. 0.5x–10x.")
                .setView(box).setPositiveButton("Spremi",(d,w)->{
                    if(launchProfileId==null)getPreferences(MODE_PRIVATE).edit()
                        .putFloat("relative_sensitivity",cameraSensitivity).apply();
                    else saveLaunchProfile(AppLaunchSettings.load(this,launchProfileId).withSensitivity(cameraSensitivity));
                }).setNegativeButton("Odustani",(d,w)->restoreCameraSensitivity(originalGain))
                  .setOnCancelListener(d->restoreCameraSensitivity(originalGain)).show();
    }

    private void captureRenderForFiveSeconds() {
        captureRenderSample(5000L,false);
    }

    private void returnFromBlenderRender() {
        if(!blenderShell||destroyed)return;
        try {
            if(blenderRenderExitSequence>=9999998)blenderRenderExitSequence=0;
            BlenderRenderExitSignal.write(
                    BlenderRenderExitSignal.path(WineShortcuts.prefix(this)),
                    ++blenderRenderExitSequence);
            log("DRNAS_RENDER_EXIT_REQUEST r62 seq="+blenderRenderExitSequence);
        } catch(Exception failure) {
            log("DRNAS_RENDER_EXIT_FAILED "+failure.getMessage());
            Toast.makeText(this,"Render return unavailable; try F11: "+failure.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showBlenderMenu() {
        new AppDialogBuilder(this).setTitle("Blender")
                .setItems(new String[]{"Puna tipkovnica","Izvezi .blend iz Wine C:",
                        "Test performansi · 20 sekundi","Pošalji log",
                        "Postavke i dijagnostika","O ovom izdanju","Zatvori Blender","Android mapa · disk D:","FPS brojač ON/OFF","Language","Resolution","Android Fullscreen ON/OFF","Return from Render"},(d,which)->{
                    if(which==0){keyboardPending=true;keyboardAttempts=0;showKeyboard();}
                    else if(which==1)startBlenderAndroidExport();
                    else if(which==2)captureRenderSample(20000L,true);
                    else if(which==3)shareGuiLog();
                    else if(which==4)showGlassControls();
                    else if(which==5)new AppDialogBuilder(this).setTitle("Blender v1.1 · DRNAS preview")
                            .setMessage("Blender 4.5.14 Windows ARM64 preko DRNAS Wine okruženja.\nNeslužbeni Blender Android preview; kompatibilnost ovisi o GPU-u i Vulkan driveru.")
                            .setPositiveButton("OK",null).show();
                    else if(which==7)showBlenderProjectFolder();
                    else if(which==8) {
                        boolean enable=!performanceHud.isActive();
                        getPreferences(MODE_PRIVATE).edit().putBoolean("blender_perf_hud",enable).apply();
                        performanceHud.setActive(enable);
                        xserver.getScreen().setFpsCounterEnabled(enable);
                        performanceHud.removeCallbacks(hudTicker);
                        if(enable)performanceHud.post(hudTicker);
                    }
                    else if(which==9)new AppDialogBuilder(this).setTitle("Language")
                            .setItems(new String[]{"English","Hrvatski"},(languageDialog,index)->{
                                AppLanguage.set(this,index==0?"en":"hr");
                                new AppDialogBuilder(this).setTitle("Language")
                                        .setMessage("Language saved. The full interface updates the next time you open Blender.")
                                        .setPositiveButton("OK",null).show();
                            }).show();
                    else if(which==10)showBlenderResolution();
                    else if(which==11)toggleBlenderHostFullscreen();
                    else if(which==12)returnFromBlenderRender();
                    else exitConfirmation.confirm(this);
                }).show();
    }

    private void toggleBlenderHostFullscreen() {
        if(!blenderShell || xserver==null || desktopCursorOverlay==null)return;
        blenderHostFullscreen=!blenderHostFullscreen;
        int crop=blenderHostFullscreen?BLENDER_HOST_TITLE_CROP:0;
        xserver.getScreen().setHostCropTopGuestPx(crop);
        desktopCursorOverlay.setHostCropTopGuestPx(crop);
        log("DRNAS_BLENDER_HOST_FULLSCREEN r56 enabled="+blenderHostFullscreen+
                " cropGuestTop="+crop+" nativeWineFullscreen=false");
        AppLanguage.toast(this,blenderHostFullscreen?
                "Android fullscreen ON":"Android fullscreen OFF",Toast.LENGTH_LONG).show();
    }

    private void showBlenderResolution() {
        String[] ids={"auto","960x544","1280x720"};
        BlenderDisplayProfile.Resolution automatic=BlenderDisplayProfile.choose(NativeHost.vulkanDeviceName(),"auto");
        String[] labels={AppLanguage.t(this,"Automatic")+" · "+automatic.id(),"960 × 544","1280 × 720"};
        String selected=getSharedPreferences("blender-display",MODE_PRIVATE).getString("resolution","auto");
        for(int i=0;i<ids.length;i++)if(ids[i].equals(selected))labels[i]="✓ "+labels[i];
        new AppDialogBuilder(this).setTitle(AppLanguage.t(this,"Resolution")+" · "+guestWidth+"×"+guestHeight)
                .setItems(labels,(dialog,index)->{
                    getSharedPreferences("blender-display",MODE_PRIVATE).edit().putString("resolution",ids[index]).apply();
                    new AppDialogBuilder(this).setTitle("Resolution saved")
                            .setMessage("Save your project, close Blender and reopen it to apply the new display resolution. Your project's render output resolution is unchanged.")
                            .setPositiveButton("OK",null).show();
                }).setNegativeButton("Cancel",null).show();
    }

    private void showBlenderProjectFolder() {
        String path;
        try {path=BlenderProjectDrive.folder(this,BlenderProjectDrive.selected(this)).getAbsolutePath();}
        catch(Exception e){path="Mapa nije dostupna";}
        new AppDialogBuilder(this).setTitle("D:\\ · Android mapa")
                .setMessage(path+"\n\nIn Blender Save/Open, select D:\\. Files are saved directly to this Android folder.\n\nSave your project before changing the folder; Blender will close and restart.")
                .setPositiveButton("OK",null)
                .setNeutralButton("Promijeni mapu",(d,w)->{
                    if(session!=null)session.close();
                    startActivity(new Intent(this,BlenderLauncherActivity.class)
                            .putExtra("choose_project_folder",true));finish();
                }).show();
    }

    private void startBlenderAndroidExport() {
        if(!blenderShell||destroyed)return;
        // File system enumeration must never block Android drawing or X11.
        new Thread(()->{
            try {
                final java.util.List<BlenderAndroidExport.Project> projects=
                        BlenderAndroidExport.projects(WineShortcuts.prefix(this));
                runOnUiThread(()->{
                    if(destroyed)return;
                    if(projects.isEmpty()) {
                        new AppDialogBuilder(this).setTitle("Spremi Blender na Android")
                                .setMessage("First save your project in Blender (Save As) to:\n"+
                                        "C:\\users\\xuser\\Documents\\Blender\\\n\n"+
                                        "Then tap '⇩ Android' and choose an Android folder. "+
                                        "Wine C: and Android Documents are separate storage locations.")
                                .setPositiveButton("U redu",null).show();
                        return;
                    }
                    String[] choices=new String[projects.size()];
                    for(int i=0;i<projects.size();i++)
                        choices[i]=projects.get(i).title();
                    new AppDialogBuilder(this)
                            .setTitle("Odaberi .blend za spremanje na Android")
                            .setItems(choices,(dialog,which)->{
                                if(which>=0 && which<projects.size())
                                    openAndroidBlenderSavePicker(projects.get(which));
                            }).setNegativeButton("Odustani",null).show();
                });
            }catch(Exception e){
                log("DRNAS_BLENDER_ANDROID_SCAN_ERROR: "+e.getMessage());
                runOnUiThread(()->{
                    if(!destroyed)AppLanguage.toast(this,
                            "Ne mogu pronaći Wine Blender projekt: "+e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        },"drnas-blender-project-scan").start();
    }

    private void openAndroidBlenderSavePicker(BlenderAndroidExport.Project project) {
        pendingBlenderAndroidProject=project;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE,project.file.getName());
        try {
            startActivityForResult(intent,BLENDER_ANDROID_EXPORT_REQUEST);
        } catch(Exception error) {
            pendingBlenderAndroidProject=null;
            log("DRNAS_BLENDER_ANDROID_PICKER_ERROR: "+error.getMessage());
            AppLanguage.toast(this,"Android upravitelj datoteka nije dostupan",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void finishAndroidBlenderExport(android.net.Uri destination,
                                             BlenderAndroidExport.Project project) {
        new Thread(()->{
            try {
                long bytes=BlenderAndroidExport.export(this,project.file,destination);
                log("DRNAS_BLENDER_ANDROID_EXPORT_OK: source="+
                        project.guestRelativePath+" bytes="+bytes+" uri="+destination.getScheme());
                runOnUiThread(()->{
                    if(!destroyed)AppLanguage.toast(this,
                            "Saved to Android folder: "+project.file.getName(),
                            Toast.LENGTH_LONG).show();
                });
            } catch(Exception error) {
                log("DRNAS_BLENDER_ANDROID_EXPORT_ERROR: "+
                        error.getClass().getSimpleName()+": "+error.getMessage());
                runOnUiThread(()->{
                    if(!destroyed)new AppDialogBuilder(this)
                            .setTitle("Spremanje na Android nije uspjelo")
                            .setMessage(error.getMessage()+
                                    "\nYour original Blender project in Wine has not been deleted.")
                            .setPositiveButton("U redu",null).show();
                });
            }
        },"drnas-blender-android-export").start();
    }

    private void captureRenderSample(long durationMs,boolean blenderBenchmark) {
        if(xserver==null || destroyed)return;
        if(renderCaptureRunning || !xserver.startRenderCapture()) {
            AppLanguage.toast(this,"Uzorak već traje",Toast.LENGTH_SHORT).show();
            return;
        }
        renderCaptureRunning=true;
        final long javaHeapStart=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
        final String metadata="Device: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+
                "\nAndroid API: "+android.os.Build.VERSION.SDK_INT+
                "\nBlender shell: "+blenderShell+"; guest "+guestWidth+"x"+guestHeight+"; stretch="+stretchFullscreen+
                "; viewport="+xserver.getScreen().getWidth()+"x"+xserver.getScreen().getHeight()+
                "\nCursor: compact 16–28 physical pixels (large brush cursors preserved)\n";
        AppLanguage.toast(this,blenderBenchmark?"Snimam 20 s — okreći i pomiči isti model":"Snimam 5 sekundi — bez kontinuiranog loga",
                Toast.LENGTH_SHORT).show();
        // X11 worker accumulates bounded in-RAM frame timings. No disk IO
        // or giant Strings in PutImage/Canvas hot paths.
        xserver.getScreen().postDelayed(()->{
            final String result=(blenderBenchmark?"Blender · DRNAS performance test\n"+metadata:"")+
                    xserver.finishRenderCapture()+
                    (blenderBenchmark?"Java heap at start: "+javaHeapStart+
                            " bytes\nJava heap at end: "+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+" bytes\n":"");
            renderCaptureRunning=false;
            if(blenderBenchmark)log("DRNAS_BLENDER_PERF_RESULT\n"+result);
            new Thread(()->{
                try {
                    GuiLogExport.Writer writer=out->out.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    final String path=blenderBenchmark?GuiLogExport.saveBlenderSample(this,writer):
                            GuiLogExport.saveRenderSample(this,writer);
                    runOnUiThread(()->{
                        if(!destroyed)AppLanguage.toast(this,
                                "Saved: Downloads/DRNAS/"+path,
                                Toast.LENGTH_LONG).show();
                    });
                } catch(Exception error) {
                    log("Five-second render capture export failed: "+error.getMessage());
                    runOnUiThread(()->{
                        if(!destroyed)AppLanguage.toast(this,"Spremanje uzorka nije uspjelo",
                                Toast.LENGTH_LONG).show();
                    });
                }
            },"emudrnas-render-capture-export").start();
        },durationMs);
    }

    private void logEarlyRendererIntent() {
        android.util.Log.i("DRNAS-HWUI","Trying Android GPU Canvas + split cursor; Wine Vulkan WSI is not yet supported");
    }

    private synchronized void log(String line) {
        // UI progress + fatal evidence are independent of optional verbose IO.
        // In quiet mode do not block the rendering or X11 event paths on disk.
        boolean critical=InputDiagnosticPolicy.keep(line) || line.startsWith("DRNAS_BLENDER_WINDOW_") || line.startsWith("DRNAS_DRIVER_AUTO") ||
                line.startsWith("DRNAS_TURNIP_AUTOPICK_") ||
                line.startsWith("DRNAS_BLENDER_ANDROID_") ||
                line.startsWith("DRNAS_BLENDER_CURSOR") ||
                line.startsWith("DRNAS_ARM64_BLENDER_DXGI:") ||
                line.startsWith("DRNAS_BLENDER_ARM64_PE") ||
                line.startsWith("DRNAS_BLENDER_VULKAN_BRIDGE_MODE") ||
                line.contains("DRNAS_BLENDER_LIBGL_PROBE") ||
                line.startsWith("DRNAS_OPENGL_MESA_BUNDLE_VERIFIED") ||
                line.startsWith("DRNAS_BLENDER_LIBGL_LINKER_READY") ||
                line.contains("DRNAS_BLENDER_WINDOW_LAYOUT") ||
                line.startsWith("DRNAS_X11_CRITICAL_") ||
                line.contains("DRNAS_X11_CRITICAL_") || line.startsWith("DRNAS_BLENDER_PERF_") ||
                line.startsWith("DRNAS_X11_BLENDER_OWNER_") ||
                AutomaticLogTrigger.matches(line) ||
                line.contains(":err:") || line.contains("dlerror") ||
                line.contains("fatal IO error") || line.startsWith("Audio unavailable:") || line.startsWith("[Startup] ") ||
                line.startsWith("[Dependencies] ") || line.startsWith("DRNAS_DEPENDENCY_RESULT") ||
                line.startsWith("DRNAS_DESKTOP_CONFIRMED") || line.startsWith("DRNAS_DESKTOP_NOT_CONFIRMED") || line.startsWith("Desktop stopped:");
        if(raw!=null) {
            if(verboseLogs)raw.write("GUI",line);
            else if(critical)raw.writeEssential("GUI",line);
        }
        if(!destroyed && AutomaticLogTrigger.matches(line)) {
            try {if(raw!=null)raw.checkpoint();}catch(IOException ignored){}
            AutomaticCrashLog.request(this,line);
        }
        if(WineGuiSession.isLaunchMarker(line)){
            desktopLaunchStarted=android.os.SystemClock.uptimeMillis();desktopLaunchRequested=true;
        }
        if(blenderShell) {
            final int stage=blenderBootStage(line);
            if(stage>=0)runOnUiThread(()->{
                if(!destroyed)updateBlenderBootProgress(stage);
            });
        }
        if(line.startsWith("[Startup] "))runOnUiThread(()->{
            if(!destroyed && !blenderShell){
                startupStatus.setText(AppLanguage.t(this,line.substring(10)));
                dependencyPanel.setVisibility(View.GONE);
            }
        });
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
                        // Blender preview intentionally shows only its unified
                        // milestone bar; specific installer steps remain in TXT.
                        if(!blenderShell) {
                            startupStatus.setText(label);
                            dependencyPanel.setVisibility(View.VISIBLE);
                        }
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
                if(startupStatus!=null && !destroyed && !blenderShell)
                    startupStatus.setText(line.startsWith("Launching ")?
                            "Opening Windows desktop…":line);
            });
        }
    }

    private View desktopKeyRow;
    private void editDesktopKeys() {
        DesktopKeysSettings.editor(this,this::reloadDesktopKeys);
    }
    private void reloadDesktopKeys() {
        if(desktopKeyRow==null)return; // Saved for the next desktop launch.
        android.view.ViewParent parent=desktopKeyRow.getParent();
        if(!(parent instanceof FrameLayout))return;
        ((FrameLayout)parent).removeView(desktopKeyRow);
        desktopKeyRow=null;
        addDesktopKeys((FrameLayout)parent);
    }
    private void positionDesktopKeys(){
        if(desktopKeyRow==null)return;
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
        float scale=stretchFullscreen?metrics.heightPixels/(float)guestHeight:Math.min(metrics.widthPixels/(float)guestWidth,metrics.heightPixels/(float)guestHeight);
        float gap=stretchFullscreen?0:(metrics.heightPixels-guestHeight*scale)/2f;
        int bar=getPreferences(MODE_PRIVATE).getBoolean("desktop_taskbar_large",true)?44:24;
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)desktopKeyRow.getLayoutParams();
        p.bottomMargin=Math.round(gap+bar*scale)+dp(DesktopKeysSettings.lift(this));
        desktopKeyRow.setLayoutParams(p);
        // Keep the five-key bar centered at 50%, move it within safe bounds.
        float available=Math.max(0,metrics.widthPixels-p.width);
        float pct=DesktopKeysSettings.horizontal(this)/100f;
        desktopKeyRow.setTranslationX((pct-.5f)*available);
    }
    private void addDesktopKeys(FrameLayout layout) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<DesktopKeysSettings.SLOT_COUNT;i++){
            final int key=DesktopKeysSettings.key(this,i);
            Button button=new Button(this);
            button.setText(DesktopKeysSettings.label(this,i));button.setTextSize(11);
            button.setContentDescription("Tipka "+(i+1)+": "+DesktopKeysSettings.label(this,i));
            button.setPadding(dp(8),0,dp(8),0);styleToolbar(button,false);
            button.setOnTouchListener((v,e)->{
                int action=e.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                    boolean down=action==MotionEvent.ACTION_DOWN;v.setPressed(down);
                    if(xserver!=null&&!destroyed){
                        if(key<0)xserver.getScreen().updatePointerButtons(-key,down);
                        else {
                            KeyEvent ev=new KeyEvent(down?KeyEvent.ACTION_DOWN:KeyEvent.ACTION_UP,key);
                            if(down)xserver.getScreen().onKeyDown(key,ev);
                            else xserver.getScreen().onKeyUp(key,ev);
                        }
                    }
                    if(action==MotionEvent.ACTION_UP)v.performClick();
                }
                return true;
            });
            row.addView(button,new LinearLayout.LayoutParams(0,dp(42),1));
        }
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(
                Math.min(dp(550),getResources().getDisplayMetrics().widthPixels-dp(20)),
                dp(42),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        layout.addView(row,p);desktopKeyRow=row;positionDesktopKeys();
    }

    private void openTaskManager() {
        new Thread(()->{
            try {if(session==null)throw new IOException("Windows još nije spreman");session.openTaskManager();}
            catch(Exception e){log("Task Manager: "+e.getMessage());runOnUiThread(()->{
                if(!destroyed)AppLanguage.toast(this,e.getMessage(),Toast.LENGTH_LONG).show();
            });}
        },"drnas-task-manager").start();
    }

    private void testVulkanPresentation(String mode) {
        new Thread(()->{
            try {
                if(session==null)throw new IOException("Windows još nije spreman");
                session.testVulkanPresentation(WineGuiActivity.this,mode);
                runOnUiThread(()->{if(!destroyed)AppLanguage.toast(this,"Vulkan test završen — rezultat je u logu",Toast.LENGTH_LONG).show();});
            } catch(Exception e){log("Vulkan test: "+e.getMessage());runOnUiThread(()->{
                if(!destroyed)AppLanguage.toast(this,e.getMessage(),Toast.LENGTH_LONG).show();
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
                AppLanguage.toast(this,"Tipkovnica nije otvorena. Probaj ⋮ → Tekst → Wine.",
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
            if(!value.isEmpty())AppLanguage.toast(this,"Wine prozor nije spreman.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        KeyEvent[] events=KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .getEvents(value.toCharArray());
        if(events==null) {
            AppLanguage.toast(this,"Neki znakovi nisu podržani u Android → X11 rasporedu.",
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
        if(raw==null){AppLanguage.toast(this,"Log se još priprema",Toast.LENGTH_SHORT).show();return;}
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
                    catch(Exception e){AppLanguage.toast(this,"Log je spremljen u Downloads/DRNAS. Dijeljenje nije dostupno.",Toast.LENGTH_LONG).show();}
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
                "\nRelease candidate: Blender v1.1 (versionCode 129)"+
                "\nDevice: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+
                "\nAndroid API: "+android.os.Build.VERSION.SDK_INT+
                "\nDesktop launch requested: "+desktopLaunchRequested+
                "\nDesktop failed: "+desktopFailed+
                "\nVerbose logging: "+verboseLogs+" (Wine/DXVK native verbosity set at desktop launch)\n\n";
        out.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(blenderShell && xserver!=null) {
            String snapshot="\n===== EXPORT-TIME LOCK-FREE SNAPSHOT =====\n"+
                    xserver.getScreen().diagnosticInputSnapshot()+"\n"+
                    xserver.diagnosticProgressSnapshot()+"\n";
            out.write(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        AndroidCrashDiagnostics.appendExitHistory(this,out);
        DiagnosticLogBundle.write(getFilesDir(),raw,out);
    }

    private void saveGuiLog() {
        if(raw==null){AppLanguage.toast(this,"No GUI log yet",Toast.LENGTH_SHORT).show();return;}
        if(android.os.Build.VERSION.SDK_INT<29){openSaveAs();return;}
        AppLanguage.toast(this,"Saving GUI TXT to Downloads/DRNAS…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try {
                String name=GuiLogExport.save(this,this::writeDiagnosticLog);
                runOnUiThread(()->{
                    if(!destroyed)AppLanguage.toast(this,"Saved: Downloads/DRNAS/"+name,
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
        catch(Exception e){AppLanguage.toast(this,
                "Could not export session log.",Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==BLENDER_ANDROID_EXPORT_REQUEST) {
            BlenderAndroidExport.Project project=pendingBlenderAndroidProject;
            pendingBlenderAndroidProject=null;
            if(result==RESULT_OK && data!=null && data.getData()!=null) {
                if(project!=null)finishAndroidBlenderExport(data.getData(),project);
                else AppLanguage.toast(this,"Ponovi izvoz; projekt nije ostao odabran",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
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
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK && !isControllerSource(event.getSource())) {
            if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())exitConfirmation.back(this);
            return true;
        }
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
        if(blenderShell)log("DRNAS_X11_CRITICAL_ANDROID_FOCUS focused="+focused+
                " timeMs="+android.os.SystemClock.uptimeMillis());
        if(!focused && blenderKeys!=null)blenderKeys.releaseAll();
        if(!focused && xserver!=null)xserver.getScreen().cancelPointerGestures();
        if(focused) {immersive();if(keyboardPending)xserver.getScreen().post(this::showKeyboard);}
    }

    @Override protected void onResume() {
        super.onResume();
        // A custom preset can be edited before Explorer starts or from
        // the glass menu while the Wine session is temporarily paused.
        if(gamepadLayer!=null)gamepadLayer.setPreset(XInputLayoutPreset.load(this,
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels));
    }
    @Override protected void onPause() {
        if(blenderKeys!=null)blenderKeys.releaseAll();
        if(xserver!=null)xserver.getScreen().cancelPointerGestures();
        if(virtualStick!=null)virtualStick.releaseAll();
        if(gamepadLayer!=null)gamepadLayer.release();
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
    @Override public void onBackPressed(){exitConfirmation.back(this);}

    @Override protected void onDestroy() {
        log("DRNAS_X11_LIFECYCLE onDestroy finishing="+isFinishing()+
                " changingConfigurations="+isChangingConfigurations()+
                " session="+(session!=null)+" xserver="+(xserver!=null));
        destroyed=true;
        if(diagnosticThread!=null)diagnosticThread.quitSafely();
        if(blenderFullscreenBridgeThread!=null)blenderFullscreenBridgeThread.quitSafely();
        if(blenderSmoothZoom!=null)blenderSmoothZoom.stop();
        if(performanceHud!=null)performanceHud.removeCallbacks(hudTicker);
        if(xserver!=null)xserver.getScreen().setNonBlockingInputEnabled(false);
        if(xinputState!=null)xinputState.close();
        AutomaticCrashLog.end(this);
        if(session!=null)new Thread(()->{session.close();if(xserver!=null)xserver.stop();},"drnas-desktop-stop").start();
        else if(xserver!=null)xserver.stop();
        super.onDestroy();
    }
}






