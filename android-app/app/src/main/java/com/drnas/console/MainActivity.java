package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.graphics.Color;
import android.graphics.ColorFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Real Android APK/UI. Only /system/bin/sh smoke test is executable.
 * Windows game / Proton buttons are deliberately non-functional.
 */
public final class MainActivity extends Activity {
    private static final int PICK_WCP = 301;
    private static final int EXPORT_SESSION_LOG = 302;
    private static final int EXPORT_RAW_WINE_LOG = 303;
    private static final int PICK_SHARED_FOLDER = 304;
    private static final int PICK_MONO = 305;
    private static final int PICK_VC = 306;
    private static final int PICK_DEPENDENCIES_ZIP = 307;
    private static final int PICK_SHORTCUT_ICON = 308;
    private ShortcutEditor shortcutEditor;
    // Compact Win11/Explorer-inspired dark glass theme, shared visually
    // with the emulator HUD. All transparency is drawn on Android, not Wine.
    private static final int BACK = 0xFF080F1C;
    private static final int PANEL = 0xDF142335;
    private static final int BORDER = 0xB037CDDD;
    private static final int TEXT = 0xFFEDF6FD;
    private static final int MUTED = 0xFFADC2D3;
    private static final int ACCENT = 0xFF79E4CB;
    private static final int WARM = 0xFFFFCF83;

    private LinearLayout content;
    private StableLogView liveLog;
    private ScrollView pageScroll;
    private String renderedSection;
    private int logPosition;
    private boolean logFollowing=true;
    private TextView stateBadge;
    private final StringBuilder log = new StringBuilder();
    private SessionLogStore completeLog;
    private String logWriteIssue;
    private SessionManager sessions;
    private ComponentStore vault;
    private ContainerBootstrap container;
    private String section = "Console";
    private String currentGame = "Far Cry 3";
    private boolean resumed;
    private boolean autoGuiPending;
    private boolean startupWaitingForFolder;
    private String wineInitializationError="";
    private final WineBootProgress bootProgress=new WineBootProgress();
    private boolean wineBooting;
    private boolean wineReady;
    private View winePowerLed;
    private Button windowsExplorerButton;
    private Button turnOnWindowsButton;
    private Button resetWineButton;
    private ProgressBar wineStartupBar;
    private TextView wineStartupText;
    private ProgressBar dependencyImportProgress;
    private TextView dependencyImportStatus;
    private volatile boolean dependencyImporting;
    private volatile int dependencyImportPercent;
    private volatile String dependencyImportLabel="Odaberi Dependencies.zip za offline instalaciju";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->exitConfirmation.back(this));
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        try {
            completeLog=new SessionLogStore(new java.io.File(getFilesDir(),"logs"));
        } catch(IOException e) {
            logWriteIssue=e.getMessage();
        }
        vault = new ComponentStore(this);
        container = new ContainerBootstrap(this,new ContainerBootstrap.Callback() {
            @Override public void onLog(String msg) {
                runOnUiThread(()->{
                    append(msg);
                    if(wineBooting && bootProgress.advance(msg))refreshPowerPanel();
                });
            }
            @Override public void onComplete(boolean guestStarted,String status) {
                runOnUiThread(()->{
                    append("Container status: "+status);
                    boolean verified=(status.startsWith("WINEBOOT_PREFIX_INITIALIZED") ||
                            status.startsWith("WINEBOOT_COMPLETED_GUEST_VERIFIED")) &&
                            readyForDesktop();
                    wineBooting=false;
                    wineReady=verified;
                    wineInitializationError=verified?"":status;
                    getSharedPreferences("drnas-boot",MODE_PRIVATE).edit().putString("last_error",wineInitializationError).apply();
                    bootProgress.finish(verified);
                    // The user turns Wine on first, then explicitly opens
                    // Windows Explorer. Never launch the GUI automatically.
                    autoGuiPending=false;
                    refreshPowerPanel();
                });
            }
        });
        sessions = new SessionManager(new SessionManager.Listener() {
            @Override public void onStateChanged(SessionManager.State status) {
                append("Session state: " + status);
                if(stateBadge!=null)stateBadge.setText("State: "+status);
            }
            @Override public void onLog(String line) { append(line); }
        });
        append("EmuDrnas preAlpha 0.1 · runtime bootstrap");
        wineReady=readyForDesktop();
        if(!wineReady)wineInitializationError=getSharedPreferences("drnas-boot",MODE_PRIVATE).getString("last_error","");
        if(wineReady)bootProgress.finish(true);
        append("Full session logs are retained privately; desktop UI has no diagnostics.");
        if(logWriteIssue!=null)append("WARNING: full TXT logging unavailable: "+logWriteIssue);
        append("ARM64 NDK native host + controlled Bionic Wine --version CLI probe.");
        render();
        // No automatic initialization on Activity launch: TURN ON WINDOWS is explicit.
        // SAF shared-folder selection remains available under Android files.
    }
    /** Android SAF cannot grant the Downloads ROOT; choose Download/DRNAS or Documents. */
    private void offerSharedFolderOnFirstLaunch() {
        if(SharedFolderDrive.selectedTree(this)!=null ||
                getPreferences(MODE_PRIVATE).getBoolean("shared_offer_done",false)) {
            autoInstallAndBoot();
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("shared_offer_done",true).apply();
        new AppDialogBuilder(this)
                .setTitle("Windows R: shared folder")
                .setMessage("Allow DRNAS Wine to see a chosen Android folder as R:?" +
                    "\n\nAndroid 11+ blocks selecting the Downloads root. Choose a " +
                    "subfolder like Download/DRNAS (create it first), or a Documents folder. " +
                    "Only selected files are copied into Wine's private R: mirror; " +
                    "Android files are not modified.")
                .setPositiveButton("Choose folder",(dialog,which)->{
                    startupWaitingForFolder=true;
                    requestSharedFolder();
                })
                .setNegativeButton("Not now",(dialog,which)->autoInstallAndBoot())
                .setOnCancelListener(dialog->autoInstallAndBoot())
                .show();
    }
    private void requestSharedFolder() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {startActivityForResult(i,PICK_SHARED_FOLDER);}
        catch(Exception e) {
            append("Android folder picker unavailable: "+e.getMessage());
            if(startupWaitingForFolder) {startupWaitingForFolder=false;autoInstallAndBoot();}
        }
    }
    private void synchronizeSharedFolder(boolean startup) {
        append("Importing selected Android folder into Wine R: mirror; original files unchanged.");
        new Thread(()->{
            try {
                SharedFolderDrive.importSelected(this,this::append);
            } catch(Exception failure) {
                append("R: folder sync warning: "+failure.getMessage());
            } finally {
                runOnUiThread(()->{
                    if("Components".equals(section)||"File Explorer".equals(section))render();
                    if(startup)autoInstallAndBoot();
                });
            }
        },"drnas-saf-mirror").start();
    }
    /** ContainerBootstrap verifies embedded archives on its own worker,
     * including when the user manually presses Init. Never race with it. */
    private void autoInstallAndBoot() {
        append("Starting self-contained Wine initialization (offline APK components).");
        startWineboot();
    }
    @Override public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        render();
    }
    @Override protected void onResume() {
        super.onResume();resumed=true;launchPendingGui();
    }
    @Override protected void onPause() {resumed=false;super.onPause();}
    private void launchPendingGui() {
        if(!resumed || !autoGuiPending || isFinishing())return;
        autoGuiPending=false;
        append("Initialization succeeded; opening Wine GUI automatically.");
        openWineGui();
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radius));
        if (stroke != 0) bg.setStroke(dp(1), stroke);
        return bg;
    }
    private GradientDrawable glass(int top, int bottom, int radius) {
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{top,bottom});
        bg.setCornerRadius(dp(radius));
        bg.setStroke(dp(1),0xB02BDAE4);
        return bg;
    }
    // Three-stage shading + lit rim gives buttons a gentle raised-glass depth.
    // Press feedback uses a darker inset gradient, not a distracting ripple.
    private GradientDrawable glassButtonLayer(boolean primary,boolean pressed) {
        GradientDrawable layer=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                primary
                ? (pressed?new int[]{0xff37bba5,0xff4dd1b5,0xff278b7d}
                          :new int[]{0xffa2f3dd,0xff74dbbe,0xff42b59b})
                : (pressed?new int[]{0xff18354c,0xff284963,0xff10263b}
                          :new int[]{0xff3b5974,0xff203c57,0xff142b45}));
        layer.setCornerRadius(dp(14));
        layer.setStroke(dp(1),pressed?0xff79e3e9:0xff3fd5df);
        return layer;
    }
    private StateListDrawable buttonSurface(boolean primary) {
        StateListDrawable layers=new StateListDrawable();
        layers.addState(new int[]{android.R.attr.state_pressed},glassButtonLayer(primary,true));
        layers.addState(new int[]{},glassButtonLayer(primary,false));
        return layers;
    }
    private StateListDrawable powerButtonSurface(boolean ready) {
        StateListDrawable layers=new StateListDrawable();
        GradientDrawable down=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                ready?new int[]{0xff1b473f,0xff0b2c29}:new int[]{0xff4d2637,0xff271320});
        down.setCornerRadius(dp(14));
        down.setStroke(dp(1),ready?0xff54e8b0:0xfff26976);
        GradientDrawable up=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                ready?new int[]{0xff205248,0xff113b35,0xff0a2522}
                     :new int[]{0xff522b3a,0xff351c2b,0xff22121d});
        up.setCornerRadius(dp(14));up.setStroke(dp(1),ready?0xff57e8b0:0xfff26976);
        layers.addState(new int[]{android.R.attr.state_pressed},down);
        layers.addState(new int[]{},up);
        return layers;
    }
    private void sectionEyebrow(LinearLayout root,String text) {
        TextView heading=label(text,10,ACCENT,true);
        heading.setLetterSpacing(.14f);
        root.addView(heading,pad(0,9));
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(AppLanguage.t(this,text));
        t.setTextSize(size >= 20 ? size * .78f : Math.max(10, size * .90f));
        t.setIncludeFontPadding(false);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private void gap(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1,dp(height)));
    }
    private LinearLayout.LayoutParams pad(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(Math.round(top * .45f));
        p.bottomMargin = dp(Math.round(bottom * .45f));
        return p;
    }
    private LinearLayout card() {
        LinearLayout panel = column();
        int vertical=compactHome()?6:8;
        panel.setPadding(dp(9),dp(vertical),dp(9),dp(vertical));
        panel.setBackground(glass(0xDF192D43,0xEA0D1C30,21));
        panel.setElevation(dp(3));
        content.addView(panel,pad(0,8));
        return panel;
    }
    private Button button(String text, boolean prominent, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(AppLanguage.t(this,text));
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setIncludeFontPadding(false);
        b.setMaxLines(2);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        b.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{0xFF829BAD,prominent?0xFF08202B:TEXT}));
        b.setPadding(dp(7),dp(2),dp(7),dp(2));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(dp(40));
        b.setMinHeight(dp(40));
        b.setBackground(buttonSurface(prominent));
        b.setStateListAnimator(null);
        b.setElevation(dp(3));
        b.setOnTouchListener((view,event)->{
            if(!view.isEnabled())return false;
            switch(event.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().cancel();
                    view.animate().scaleX(.977f).scaleY(.96f).setDuration(85).start();
                    break;
                case android.view.MotionEvent.ACTION_CANCEL:
                case android.view.MotionEvent.ACTION_UP:
                    view.animate().cancel();
                    view.animate().scaleX(1f).scaleY(1f).setDuration(155).start();
                    break;
            }
            return false; // Let the native Button handle clicks/accessibility.
        });
        b.setOnClickListener(action);
        return b;
    }
    private Button iconButton(String text,String icon,View.OnClickListener action) {
        Button b=button(text,false,action);
        b.setGravity(Gravity.CENTER);
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(null,
                new DashboardIconDrawable(icon,dp(29)),null,null);
        b.setCompoundDrawablePadding(dp(4));
        b.setPadding(dp(5),dp(6),dp(5),dp(5));
        return b;
    }
    private void addButton(LinearLayout root, String name, boolean main, View.OnClickListener action) {
        Button b=button(name,main,action);
        root.addView(b,pad(4,4));
    }
    private void heading(String text) {
        TextView t=label(text,26,TEXT,true);
        t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        content.addView(t,pad(0,12));
    }
    private void note(LinearLayout root,String message) {
        TextView t=label(message,12,WARM,false);
        t.setPadding(dp(12),dp(10),dp(12),dp(10));
        t.setBackground(glass(0x8A544125,0xB62C261D,13));
        root.addView(t,pad(12,0));
    }
    private void showSection(String s) { section=s; render(); }
    private void render() {
        boolean sameSection=section.equals(renderedSection);
        int pageY=sameSection && pageScroll!=null?pageScroll.getScrollY():0;
        if(liveLog!=null){logPosition=liveLog.getScrollY();logFollowing=liveLog.isFollowing();}
        liveLog=null;stateBadge=null;
        winePowerLed=null;windowsExplorerButton=null;turnOnWindowsButton=null;resetWineButton=null;
        wineStartupBar=null;wineStartupText=null;
        ScrollView scroll = new ScrollView(this);
        pageScroll=scroll;renderedSection=section;
        SystemBarInsets.apply(scroll);
        scroll.setFillViewport(true);
        // A dedicated Canvas paints moving pollen across the entire GUI
        // behind translucent cards. It cannot obscure icons, labels or touches.
        scroll.setBackgroundColor(Color.TRANSPARENT);
        content=column();
        content.setPadding(dp(10),dp(8),dp(10),dp(10));
        scroll.addView(content);
        android.widget.FrameLayout screen=new android.widget.FrameLayout(this);
        screen.addView(new BoraPollenBackground(this),
                new android.widget.FrameLayout.LayoutParams(-1,-1));
        screen.addView(scroll,new android.widget.FrameLayout.LayoutParams(-1,-1));
        setContentView(screen);
        LinearLayout header=horizontal();
        header.setPadding(dp(9),dp(6),dp(9),dp(6));
        header.setBackground(glass(0xD51C3749,0xD10D1D32,18));
        TextView glyph=label("◈",26,ACCENT,true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(box(0xAA214A58,0x6685E7D2,15));
        header.addView(glyph,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout names=column();
        names.setPadding(dp(8),0,0,0);
        TextView brand=new RetroWordmarkView(this);
        brand.setSingleLine(true);
        brand.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(brand);
        TextView subtitle=label("preAlpha 0.1   •   WINDOWS RUNTIME",10,ACCENT,true);
        subtitle.setLetterSpacing(.09f);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(subtitle);
        header.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView prealpha=label("EARLY ACCESS",9,0xFFE7D6A5,true);
        prealpha.setGravity(Gravity.CENTER);
        prealpha.setPadding(dp(7),dp(7),dp(7),dp(7));
        prealpha.setBackground(box(0x7757422B,0x99866F51,10));
        android.widget.TextClock clock=new android.widget.TextClock(this);
        clock.setFormat24Hour("HH:mm");clock.setFormat12Hour("HH:mm");
        clock.setTextSize(12);clock.setTextColor(0xff83ffd0);
        clock.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(dp(6),dp(5),dp(6),dp(5));
        clock.setShadowLayer(dp(3),0,0,0x8852ffb6);
        clock.setBackground(box(0xff092c2a,0xff327569,8));
        LinearLayout.LayoutParams clockParams=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        clockParams.leftMargin=dp(5);clockParams.rightMargin=dp(6);
        header.addView(clock,clockParams);
        header.addView(prealpha);
        content.addView(header,pad(0,8));

        
        LinearLayout buttons=horizontal();
        for(String name:new String[]{"Console","File Explorer","Components","Shortcuts","Options"}) {
            Button b=button(name,name.equals(section),v->showSection(name));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    0,dp(40),1f);
            lp.rightMargin=dp(4);
            buttons.addView(b,lp);
        }
        content.addView(buttons,pad(0,8));

        switch(section) {
            case "File Explorer":
                content.addView(new HostFileExplorer(this,this::requestSharedFolder,this::editShortcut));break;
            case "Components": showComponents(); break;
            case "Shortcuts": showShortcuts(false); break;
            case "Diagnostics": showDiagnostics(); break;
            case "Options": showOptions(); break;
            default: showConsole();
        }

        AppLanguage.tree(content);
        scroll.post(()->scroll.scrollTo(0,pageY));
    }

    /** Friendly launchpad; RAW boot and Wine logs stay on disk, not on screen. */
    private GradientDrawable powerLight(boolean ready) {
        GradientDrawable light=new GradientDrawable();
        light.setShape(GradientDrawable.OVAL);
        light.setColor(ready ? 0xff32db82 : 0xffee5555);
        light.setStroke(dp(2),ready?0xffa4ffcd:0xffffabab);
        return light;
    }
    private void refreshPowerPanel() {
        boolean ready=wineReady && !wineBooting;
        if(winePowerLed!=null) {
            winePowerLed.setBackground(powerLight(ready));
            winePowerLed.setContentDescription(ready?"Wine ready":"Wine not ready");
        }
        if(turnOnWindowsButton!=null) {
            turnOnWindowsButton.setEnabled(!wineBooting && !ready);
            int neon=ready?0xff72ffc2:0xffff727c;
            turnOnWindowsButton.setText(wineBooting?"POKRETANJE…":ready?"WINDOWS ON":"WINDOWS OFF");
            turnOnWindowsButton.setTextColor(neon);
            turnOnWindowsButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null,new DashboardIconDrawable(DashboardIconDrawable.POWER,dp(29)),null,null);
            turnOnWindowsButton.setCompoundDrawablePadding(dp(4));
            turnOnWindowsButton.setBackground(powerButtonSurface(ready));
            turnOnWindowsButton.setContentDescription(wineBooting?"Windows se pokreće":
                    ready?"Windows upaljen":"Windows ugašen. Pokreni Windows");
        }
        if(windowsExplorerButton!=null)windowsExplorerButton.setEnabled(ready);
        if(resetWineButton!=null)resetWineButton.setEnabled(!wineBooting && !container.isBusy() && readyForDesktop());
        if(wineStartupBar!=null) {
            wineStartupBar.setVisibility(wineBooting || bootProgress.percent()>0?
                    View.VISIBLE:View.GONE);
            wineStartupBar.setProgress(bootProgress.percent());
        }
        if(wineStartupText!=null) {
            wineStartupText.setText(AppLanguage.t(this,bootProgress.phase())+
                    (wineBooting ? "  •  "+bootProgress.percent()+"%" : "")+
                    (!wineBooting&&!ready&&!wineInitializationError.isEmpty()?"\n"+wineInitializationError:""));
            wineStartupText.setMaxLines(2);
            wineStartupText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            wineStartupText.setOnClickListener(v->{
                if(!wineInitializationError.isEmpty())new AppDialogBuilder(this)
                        .setTitle("Inicijalizacija nije uspjela").setMessage(wineInitializationError)
                        .setPositiveButton("PODIJELI LOG",(d,w)->shareCrashDiagnostics())
                        .setNegativeButton("Zatvori",null).show();
            });
            wineStartupText.setTextColor(ready?ACCENT:wineBooting?WARM:MUTED);
        }
    }

    /** Compact controls keep normal Android layout and touch coordinates. */
    private void actionRow(LinearLayout parent, View left, View right) {
        LinearLayout row=horizontal();
        row.setMinimumHeight(dp(66));
        row.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f));
        LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f);
        second.leftMargin=dp(6);
        row.addView(right,second);
        LinearLayout.LayoutParams rowParams=pad(3,3);
        rowParams.height=dp(66);
        rowParams.weight=1f;
        parent.addView(row,rowParams);
    }

    /** WRAP_CONTENT preserves minimum sizes in small/landscape windows.
     * ScrollView.fillViewport distributes only spare safe viewport height. */
    private void fillRemainingHeight(View panel,float weight) {
        LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)panel.getLayoutParams();
        lp.weight=weight;
        panel.setLayoutParams(lp);
    }

    private void editShortcut(String drive,String path) {
        shortcutEditor=new ShortcutEditor(this,drive,path,()->{
            Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("*/*");
            picker.putExtra(Intent.EXTRA_TITLE,"Odaberi .ico ikonu");
            try{startActivityForResult(picker,PICK_SHORTCUT_ICON);}
            catch(android.content.ActivityNotFoundException error){
                AppLanguage.toast(this,"Nema dostupnog odabira datoteka",Toast.LENGTH_LONG).show();
            }
        },()->{shortcutEditor=null;section="Console";render();});
        shortcutEditor.show();
    }

    private void showConfigurationMenu() {
        int cap=WineGuiActivity.savedFrameCap(this);
        new AlertDialog.Builder(this).setTitle("Konfiguracija")
                .setItems(new String[]{"DXVK FPS: "+(cap==0?"bez limita":cap)+
                        " · idući reset prikaza","Wine configuration"},(dialog,which)->{
                    if(which==0)DrnasDxvkDialog.show(this,cap,false,chosen->{
                        WineGuiActivity.saveFrameCap(this,chosen);
                        Toast.makeText(this,"DXVK FPS spremljen · primjena nakon Reset Wine",
                                Toast.LENGTH_SHORT).show();
                    },()->{});
                    else startConfig();
                }).show();
    }

    private void showConsole() {
        LinearLayout page=content;
        boolean wide=getResources().getConfiguration().screenWidthDp>=600;
        LinearLayout right=null;
        if(wide) {
            LinearLayout dashboard=horizontal();
            dashboard.setGravity(Gravity.TOP);
            LinearLayout left=column();
            right=column();
            dashboard.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1.2f);
            rp.leftMargin=dp(8);
            dashboard.addView(right,rp);
            page.addView(dashboard,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            content=left;
        }
        MeadowBannerView meadow=new MeadowBannerView(this);
        content.addView(meadow,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(compactHome()?Math.max(36,Math.min(86,(getResources().getConfiguration().screenHeightDp-500)/3)):86)));
        showShortcuts(true);
        if(wide)content=right;
        LinearLayout desktop=card();
        fillRemainingHeight(desktop,3f);
        LinearLayout title=horizontal();
        TextView header=label("◈  WINDOWS ENVIRONMENT",11,ACCENT,true);
        header.setLetterSpacing(.06f);
        title.addView(header,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        winePowerLed=new View(this);
        title.addView(winePowerLed,new LinearLayout.LayoutParams(dp(10),dp(10)));
        desktop.addView(title,pad(0,5));

        turnOnWindowsButton=iconButton("WINDOWS OFF",DashboardIconDrawable.POWER,v->startWineboot());
        resetWineButton=iconButton("RESET WINE",DashboardIconDrawable.RESET,v->resetWine());
        actionRow(desktop,turnOnWindowsButton,resetWineButton);
        windowsExplorerButton=iconButton("EXPLORER",DashboardIconDrawable.FILES,v->openWineGui());
        actionRow(desktop,windowsExplorerButton,
                iconButton("XINPUT EDITOR",DashboardIconDrawable.GAMEPAD,
                        v->startActivity(new Intent(this,XInputLayoutEditorActivity.class))));
        actionRow(desktop,
                iconButton("A840 H2.9 AUTO "+(TurnipRuntime.autoH29Enabled(this)?"ON ●":"OFF"),
                        DashboardIconDrawable.GPU,v->{
                            boolean on=!TurnipRuntime.autoH29Enabled(this);
                            TurnipRuntime.setAutoH29Enabled(this,on);
                            Toast.makeText(this,"A840 H2.9 "+(on?"ON":"OFF")+
                                    " · primjena pri idućem pokretanju",Toast.LENGTH_SHORT).show();
                            render();
                        }),
                iconButton("DXVK 1.10.3 · "+DxvkVariants.selected(this).toUpperCase(Locale.ROOT),
                        DashboardIconDrawable.DXVK,v->{
                            String choice=DxvkVariants.toggle(this);
                            Toast.makeText(this,"DXVK "+choice+
                                    " · primjena pri idućem pokretanju Wine",Toast.LENGTH_SHORT).show();
                            render();
                        }));
        actionRow(desktop,
                iconButton("PODIJELI LOG",DashboardIconDrawable.LOG,v->shareCrashDiagnostics()),
                iconButton("KONFIGURACIJA",DashboardIconDrawable.SETTINGS,
                        v->showSection("Options")));

        wineStartupBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        wineStartupBar.setMax(100);
        wineStartupBar.setIndeterminate(false);
        wineStartupBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        desktop.addView(wineStartupBar,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(4)));
        wineStartupText=label("",11,MUTED,false);
        desktop.addView(wineStartupText,pad(3,0));
        refreshPowerPanel();

        LinearLayout folder=card();
        fillRemainingHeight(folder,1f);
        sectionEyebrow(folder,"ANDROID FILES  /  R:");
        actionRow(folder,
                iconButton("ODABERI MAPU",DashboardIconDrawable.FOLDER,v->requestSharedFolder()),
                iconButton("OSVJEŽI R:",DashboardIconDrawable.RESET,
                        v->synchronizeSharedFolder(false)));
        content=page;
        TextView runtimeInfo=label("1280 × 720 · Proton 11 · FEX 2609 · DXVK",10,MUTED,false);
        runtimeInfo.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(runtimeInfo,pad(2,2));
    }


    /**
     * The home-page preview uses real discovered Wine links and real Wine tools.
     * A game never appears merely because its icon was featured in a mockup.
     */
    private void showShortcuts(boolean preview) {
        LinearLayout panel=card();
        LinearLayout headline=horizontal();
        TextView title=label("◈  GAME & APP SHORTCUTS",13,ACCENT,true);
        title.setLetterSpacing(.08f);
        headline.addView(title,new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView source=label("Added from Wine",10,MUTED,false);
        headline.addView(source);
        panel.addView(headline,pad(0,10));
        List<WineShortcuts.Entry> shortcuts=WineShortcuts.list(this);
        int count=preview?Math.min(shortcuts.size(),8):shortcuts.size();
        int available=getResources().getConfiguration().screenWidthDp;
        if(preview && available>=600)available=Math.round((available-28)/2.2f);
        int columns=Math.max(4,Math.min(8,(available-40)/74));
        LinearLayout row=null;
        for(int i=0;i<count;i++) {
            if(i%columns==0) {
                row=horizontal();row.setGravity(Gravity.CENTER_VERTICAL);
                panel.addView(row,pad(3,4));
            }
            WineShortcuts.Entry entry=shortcuts.get(i);
            LinearLayout tile=column();
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(2),dp(4),dp(2),dp(3));
            tile.setBackground(glass(0xC01B4355,0xC010293F,12));
            android.graphics.Bitmap customIcon=UserShortcuts.icon(this,entry.id);
            View icon;
            if(customIcon==null)icon=new RetroIconView(this,entry.id);
            else {
                android.widget.ImageView image=new android.widget.ImageView(this);
                image.setImageBitmap(customIcon);
                image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                icon=image;
            }
            LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(28),dp(28));
            tile.addView(icon,iconParams);
            String displayName=AppLaunchSettings.load(this,entry.id).title(entry.title);
            TextView name=label(displayName,11,TEXT,false);
            if(UserShortcuts.isId(entry.id)||!AppLaunchSettings.load(this,entry.id).title.isEmpty()){
                name.setTag("user-text");name.setText(displayName);
            }
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            tile.addView(name,pad(5,0));
            tile.setContentDescription(displayName+" · "+entry.status+" · Dugi dodir za postavke");
            tile.setOnClickListener(v->launchWineShortcut(entry));
            tile.setOnLongClickListener(v->{AppSettingsDialog.show(this,entry,this::render);return true;});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);
            if(i%columns!=0)lp.leftMargin=dp(5);
            row.addView(tile,lp);
        }
        if(preview) {
            source.setText("SVI  ›");
            source.setTextColor(ACCENT);
            headline.setMinimumHeight(dp(40));
            headline.setOnClickListener(v->showSection("Shortcuts"));
            headline.setContentDescription("Prikaži sve Wine prečace");
        } else {
            addButton(panel,"＋  Dodaj igru / program (.exe)",true,v->showSection("File Explorer"));
            addButton(panel,"↻  Osvježi Wine prečace",false,v->render());
            addButton(panel,"▣  Otvori Wine Explorer za instalaciju programa",false,
                    v->openWineGui());
            note(panel,"Za svoju igru otvori File Explorer, pronađi .exe na C: ili R: i odaberi Dodaj na početni ekran. Dugi dodir na ikonu otvara postavke. Prihvati sprema, Odbaci zanemaruje promjene. Postojeći Wine Desktop / Start Menu prečaci također se prikazuju.");
        }
        TextView footnote=label("ⓘ  Wine shortcuts appear here. "+
                (!wineReady?"Pokreni Windows za instalirane programe.":""),11,MUTED,false);
        if(!preview)panel.addView(footnote,pad(10,0));
    }
    private void launchWineShortcut(WineShortcuts.Entry entry) {
        if(wineBooting || container.isBusy() || !readyForDesktop()) {
            Toast.makeText(this,"Prvo pokreni Windows i pričekaj Wine inicijalizaciju.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if(!WineShortcuts.isLaunchRequest(entry.id)) {
            Toast.makeText(this,"Wine prečac nije dostupan.",Toast.LENGTH_LONG).show();
            return;
        }
        // FreeCAD needs a validated Windows PE executable and explicit user
        // confirmation. The home tile is not an auto-installer.
        if (FreeCadPreview.ID.equals(entry.id)) {
            startFreeCadPreview();
            return;
        }
        // A new Activity inherits the same persistent prefix and starts the
        // named guest app inside Wine's virtual desktop. No host shell parsing.
        startActivity(new Intent(this,WineGuiActivity.class)
                .putExtra("DRNAS_GUI_APP",entry.id));
    }

    /**
     * Only a private preview entry: discover a user-installed FreeCAD on C:.
     * Never download or automatically execute third-party binaries.
     */
    private void startFreeCadPreview() {
        if (wineBooting || container.isBusy() || !readyForDesktop()) {
            Toast.makeText(this,"Pokreni Windows prije FreeCAD testa.",Toast.LENGTH_LONG).show();
            return;
        }
        try {
            File driveC = new File(getFilesDir(),
                    "containers/default/wineboot-probe/template/.wine/drive_c");
            File exe = FreeCadPreview.findExecutable(driveC);
            if (exe == null) {
                new AppDialogBuilder(this).setTitle("FreeCAD · nije instaliran")
                        .setMessage("Za test stavi Windows x64 FreeCAD portable u "+
                                "C:\\FreeCAD\\bin\\FreeCAD.exe ili instaliraj u "+
                                "C:\\Program Files\\FreeCAD...\\bin\\FreeCAD.exe. "+
                                "Ovo ne preuzima FreeCAD i ne dira Blender.")
                        .setPositiveButton("OTVORI EXPLORER",(d,w)->openWineFiles())
                        .setNegativeButton("ZATVORI",null).show();
                return;
            }
            String architecture = FreeCadPreview.architecture(exe);
            if (!"x64".equals(architecture) && !"arm64".equals(architecture)) {
                new AppDialogBuilder(this).setTitle("FreeCAD · nepodržani EXE")
                        .setMessage("Prepoznati PE tip: "+architecture+". Očekujemo Windows x64 ili ARM64.")
                        .setNegativeButton("ZATVORI",null).show();
                return;
            }
            final String winePath = WineGuiSession.windowsPath(driveC,exe,'C');
            new AppDialogBuilder(this).setTitle("FreeCAD · Proton 11 LAB")
                    .setMessage("Pronađen Windows "+architecture+" FreeCAD na "+winePath+
                            ". Pokretanje nije potvrđeno. x64 zahtijeva ispravan FEX/WOW64, "+
                            "a CAD prikaz ispravan OpenGL. Test neće mijenjati Blender.")
                    .setPositiveButton("PROBAJ",(d,w)->startActivity(
                            new Intent(this,WineGuiActivity.class)
                                    .putExtra("DRNAS_GUI_APP",FreeCadPreview.ID)))
                    .setNegativeButton("ODUSTANI",null).show();
        } catch (IOException error) {
            new AppDialogBuilder(this).setTitle("FreeCAD · preflight error")
                    .setMessage(error.getMessage()).setNegativeButton("ZATVORI",null).show();
        }
    }

    private boolean compactHome(){return AppOptionsView.desktop(this).getBoolean("ui_compact",true);}
    private void showOptions(){
        LinearLayout panel=card();
        panel.addView(new AppOptionsView(this,this::render));
        addButton(panel,"Diagnostics / logs",false,v->showSection("Diagnostics"));
        addButton(panel,"Wine configuration",false,v->startConfig());
    }
    private void showComponents() {
        sectionEyebrow(content,"VERSIONED COMPONENT VAULT");
        heading("Your engine parts.");
        content.addView(label("Proton 11.0-2 ARM64EC, FEXCore 2609 and both DXVK 1.10.3 Standard + Async are included in the APK. Switch modes before Explorer; Reset Wine is needed to apply an in-session change.",13,MUTED,false),pad(0,18));
        LinearLayout panel=card();
        panel.addView(label("STAGE A COMPONENT ARCHIVE",12,ACCENT,true),pad(0,8));
        addButton(panel,"+ Import .wcp / Turnip .zip",true,v->selectWcp());
        addButton(panel,"Import Wine Mono (.msi)",false,v->selectPrerequisite(PICK_MONO));
        addButton(panel,"Import VC++ Redist x64 (.exe)",false,v->selectPrerequisite(PICK_VC));
        addButton(panel,"IMPORT DEPENDENCIES.ZIP (offline)",true,v->selectPrerequisite(PICK_DEPENDENCIES_ZIP));
        addButton(panel,"PONOVI NEUSPJELE DIRECTX / VC++ INSTALACIJE",false,v->{
            WineDependencyBundle.requestRetry(this);
            Toast.makeText(this,"Neuspjeli paketi pokušat će se ponovno pri idućem paljenju Windowsa.",Toast.LENGTH_LONG).show();
        });
        dependencyImportProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        dependencyImportProgress.setMax(100);
        dependencyImportProgress.setProgress(dependencyImportPercent);
        dependencyImportProgress.setIndeterminate(false);
        dependencyImportProgress.setVisibility(dependencyImporting?View.VISIBLE:View.GONE);
        panel.addView(dependencyImportProgress,pad(10,4));
        dependencyImportStatus=label(dependencyImportLabel+
                (dependencyImporting?"  •  "+dependencyImportPercent+"%":""),12,MUTED,false);
        panel.addView(dependencyImportStatus,pad(7,6));
        note(panel,"Dependencies.zip is imported once through Android Files, not downloaded. Failed or interrupted installers wait for the retry button; they are never marked installed. Offline first-time installation starts before Wine Explorer: DirectX June 2010, VC++ x86/x64, OpenAL, XNA and VulkanRT. .NET and websetup are skipped. Check the GUI install progress bar and private log for failures.");
        note(panel,"Imported prerequisite installers run automatically ONCE per SHA-256 in each persistent Wine prefix. No installer is marked successful until its Wine process exits 0 (or 3010). Supply a genuine Wine Mono MSI and Microsoft VC++ x64 EXE. An APK-bundled wine-mono.msi is used automatically if present.");
        LinearLayout sharing=card();
        sharing.addView(label("WINDOWS R: · ANDROID SHARED FILES",11,ACCENT,true),pad(0,9));
        sharing.addView(label("Android folder grant: "+
                (SharedFolderDrive.selectedTree(this)==null ? "not selected" : "granted")+
                ". SAF copies selected files into Wine R: (private mirror, import-only). "+
                "On Android 11+ choose a subfolder, not the root Downloads directory.",
                12,MUTED,false),pad(0,9));
        addButton(sharing,"Choose folder for Wine R:",true,v->requestSharedFolder());
        addButton(sharing,"Import / refresh R: from Android",false,v->synchronizeSharedFolder(false));
        addButton(sharing,"Open R: in Windows file manager",false,v->openWineFiles());


        LinearLayout installed=card();
        installed.addView(label("LOCAL VAULT",12,ACCENT,true),pad(0,8));
        List<String> items=vault.list();
        if(items.isEmpty()) installed.addView(label("No components staged on this phone.",13,MUTED,false),pad(0,6));
        for(String item:items) installed.addView(label("◉ "+item,12,TEXT,false),pad(3,12));
        installed.addView(label("Default APK bundle: Proton 11.0-2 ARM64EC • FEXCore 2609 • DXVK 1.10.3. Optional: your own Turnip ZIP",12,MUTED,false),pad(7,0));
    }

    private void showDiagnostics() {
        content.addView(label("PROCESS + COMPATIBILITY",11,ACCENT,true),pad(0,6));
        heading("Observe everything.");
        LinearLayout probe=card();
        probe.addView(label("DEVICE PROBE",12,ACCENT,true),pad(0,9));
        TextView info=label(RuntimeProbe.inspect(this),12,TEXT,false);
        info.setTypeface(Typeface.MONOSPACE);
        probe.addView(info);
        note(probe,RuntimeProbe.windowsRuntimeStatus());

        LinearLayout panel=card();
        panel.addView(label("SESSION EVENT LOG",12,ACCENT,true),pad(0,7));
        addButton(panel,"Pošalji zapis pada / zadnjeg desktopa",true,v->shareCrashDiagnostics());
        addButton(panel,"⬇ Export complete session log (.txt)",true,v->exportSessionLog());
        addButton(panel,"⬇ Export RAW Wine trace (.txt)",false,v->exportRawWineTrace());
        liveLog=new StableLogView(this,ACCENT);
        liveLog.setBackground(box(0xFF07101B,BORDER,11));
        panel.addView(liveLog,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(220)));
        liveLog.setSnapshot(log.toString());liveLog.restorePosition(logPosition,logFollowing);
        addButton(panel,"↓ Latest log / resume follow",false,v->{if(liveLog!=null)liveLog.showLatest();});
        note(panel,"Full session TXT includes all console messages. RAW Wine TXT separately captures unfiltered Wine/wineserver output (up to 24 MiB per wineboot test). Save it after the test finishes.");
        addButton(panel,"▶ Start container (Wine CLI)",false,v->startContainer());
        addButton(panel,"▶ Test wineboot --init (isolated)",false,v->startWineboot());
        addButton(panel,"▶ Open Wine GUI (embedded X11)",true,v->openWineGui());
        addButton(panel,"▶ Open Windows file manager (R:)",false,v->openWineFiles());
        addButton(panel,"▶ Start native smoke test",false,v->{
            if(!sessions.startAndroidSmokeTest()) append("Start rejected: another session active");
        });
        addButton(panel,"■ Stop process",false,v->sessions.stop());
        addButton(panel,"Clear visible log",false,v->{
            log.setLength(0);
            if(liveLog!=null)liveLog.setSnapshot("");
            append("Log reset");
        });
    }
    private void append(String message) {
        if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()) {
            runOnUiThread(()->append(message));
            return;
        }
        String time=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date());
        String msg="["+time+"] "+message+"\n";
        if(completeLog!=null) {
            try {
                completeLog.appendLine(msg);
            } catch(IOException e) {
                logWriteIssue=e.getMessage();
                try {completeLog.close();}catch(IOException ignored){}
                completeLog=null;
            }
        }
        if(log.length()>30000)log.delete(0,Math.min(12000,log.length()));
        log.append(msg);
        if(liveLog!=null)liveLog.setSnapshot(log.toString());
    }

    private void shareCrashDiagnostics() {
        new Thread(()->{
            try {
                Intent share=GuiLogExport.share(this,out->AndroidCrashDiagnostics.export(this,out));
                runOnUiThread(()->startActivity(Intent.createChooser(share,"Pošalji zapis pada")));
            } catch(Exception error) {
                runOnUiThread(()->Toast.makeText(this,"Zapis pada: "+error.getMessage(),Toast.LENGTH_LONG).show());
            }
        },"drnas-crash-export").start();
    }

    private void exportSessionLog() {
        if(completeLog==null) {
            append("Export unavailable: full session log file could not be written.");
            Toast.makeText(this,"Full log unavailable; not exporting incomplete text.",Toast.LENGTH_LONG).show();
            return;
        }
        // Android's Storage Access Framework lets the user choose Downloads,
        // Documents or another provider. No broad storage permission needed.
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        String name="DRNAS-Console-"+new SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date())+".txt";
        i.putExtra(Intent.EXTRA_TITLE,name);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(i,EXPORT_SESSION_LOG);
        } catch(android.content.ActivityNotFoundException e) {
            append("TXT export unavailable: no document-saving provider installed.");
            Toast.makeText(this,"No Android document picker found.",Toast.LENGTH_LONG).show();
        }
    }

    private void completeSessionLogExport(Uri uri) {
        if(completeLog==null) {
            append("Export failed: full session logging is unavailable.");
            return;
        }
        try(OutputStream destination=getContentResolver().openOutputStream(uri,"w")) {
            if(destination==null)throw new IOException("Document provider returned no output stream");
            long bytes=completeLog.copyTo(destination);
            append("Exported session event log: "+bytes+" UTF-8 bytes to selected TXT file.");
            Toast.makeText(this,"DRNAS TXT log saved ("+bytes+" bytes)",Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            append("TXT export FAILED: "+e.getClass().getSimpleName()+": "+e.getMessage());
            Toast.makeText(this,"Could not export TXT log",Toast.LENGTH_LONG).show();
        }
    }
    private File rawWineTraceFile() {
        return new File(new File(getFilesDir(),"logs"),RawWineTrace.LATEST);
    }

    private void exportRawWineTrace() {
        if(container.isBusy()) {
            append("RAW Wine trace export: wait until Wineboot test finishes.");
            Toast.makeText(this,"Wait until the Wineboot test finishes.",Toast.LENGTH_LONG).show();
            return;
        }
        File raw=rawWineTraceFile();
        if(!raw.isFile() || raw.length()<30 ||
                java.nio.file.Files.isSymbolicLink(raw.toPath())) {
            append("RAW Wine trace is not yet available. Run Wineboot test first.");
            Toast.makeText(this,"Run Wineboot test first to create RAW trace.",Toast.LENGTH_LONG).show();
            return;
        }
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE,"DRNAS-Wine-RAW-"+new SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date())+".txt");
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(i,EXPORT_RAW_WINE_LOG);
        } catch(android.content.ActivityNotFoundException e) {
            append("RAW Wine TXT export: no Android document picker available.");
        }
    }

    private void completeRawWineExport(Uri uri) {
        if(container.isBusy()) {
            append("RAW trace export aborted: Wineboot currently running.");
            Toast.makeText(this,"Wait for Wineboot to finish.",Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(()->{
            try {
                File raw=rawWineTraceFile();
                if(!raw.isFile() || java.nio.file.Files.isSymbolicLink(raw.toPath()))
                    throw new IOException("RAW trace missing or symlinked");
                long copied=0;
                try(InputStream src=new FileInputStream(raw);
                    OutputStream dst=getContentResolver().openOutputStream(uri,"w")) {
                    if(dst==null)throw new IOException("Document provider gave no output stream");
                    byte[] buf=new byte[32768];int count;
                    while((count=src.read(buf))!=-1) {
                        dst.write(buf,0,count);copied+=count;
                    }
                    dst.flush();
                }
                final long total=copied;
                runOnUiThread(()->{
                    append("Exported RAW Wine trace: "+total+" bytes");
                    Toast.makeText(this,"RAW Wine TXT saved ("+total+" bytes)",Toast.LENGTH_LONG).show();
                });
            } catch(Exception error) {
                runOnUiThread(()->{
                    append("RAW Wine export FAILED: "+error.getClass().getSimpleName()+
                            ": "+error.getMessage());
                    Toast.makeText(this,"Could not save RAW Wine trace.",Toast.LENGTH_LONG).show();
                });
            }
        },"drnas-raw-trace-export").start();
    }

    private boolean readyForDesktop() {
        try {
            File plan=new File(getFilesDir(),"containers/default/boot-plan.json");
            return plan.isFile() &&
                    com.drnas.console.WcpRuntimeStager.readJson(plan)
                            .optBoolean("winebootGuestVerified",false);
        } catch(Exception ignored) { return false; }
    }
    private void startDesktop() {
        if(container.isBusy()) {
            Toast.makeText(this,"Preparing Windows; please wait.",Toast.LENGTH_SHORT).show();
        } else if(readyForDesktop()) {
            openWineGui();
        } else {
            Toast.makeText(this,"Preparing Windows desktop…",Toast.LENGTH_SHORT).show();
            startWineboot();
        }
    }
    private void startFiles() {
        if(container.isBusy()) {
            Toast.makeText(this,"Preparing Windows; please wait.",Toast.LENGTH_SHORT).show();
        } else if(readyForDesktop()) openWineFiles();
        else startWineboot(); // successful boot opens Explorer; user can choose R: afterwards
    }
    private void startConfig() {
        if(container.isBusy()) {
            Toast.makeText(this,"Preparing Windows; please wait.",Toast.LENGTH_SHORT).show();
        } else if(readyForDesktop())
            startActivity(new Intent(this,WineGuiActivity.class)
                    .putExtra("DRNAS_GUI_APP","config"));
        else startWineboot();
    }

    private void startContainer() {
        if(!container.start()) append("Container bootstrap already in progress");
        else { append("Wine CLI requested; output retained in private logs."); }
    }
    private void openWineGui() {
        if(container.isBusy() || wineBooting || !wineReady || !readyForDesktop()) {
            wineReady=false;
            refreshPowerPanel();
            Toast.makeText(this,"Turn on Windows first.",Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this,WineGuiActivity.class));
    }
    private void openWineFiles() {
        if(container.isBusy()){append("Wait until container initialization finishes.");return;}
        startActivity(new Intent(this,WineGuiActivity.class).putExtra("DRNAS_GUI_APP","files"));
    }
    private void resetWine() {
        if(wineBooting || container.isBusy() || !readyForDesktop())return;
        wineBooting=true;wineReady=false;bootProgress.begin();refreshPowerPanel();
        new Thread(()->{
            Exception failure=null;
            try {WineGuiSession.stopActive();}
            catch(Exception e){failure=e;}
            final Exception error=failure;
            runOnUiThread(()->{
                wineBooting=false;wineReady=error==null && readyForDesktop();
                bootProgress.finish(wineReady);refreshPowerPanel();
                if(error!=null)Toast.makeText(this,"Wine reset failed: "+error.getMessage(),Toast.LENGTH_LONG).show();
                else if(!isFinishing())openWineGui();
            });
        },"drnas-reset-wine").start();
    }

    private void startWineboot() {
        if(wineBooting || container.isBusy()) {
            Toast.makeText(this,"Wine initialization is already running.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(wineReady && readyForDesktop()) {
            Toast.makeText(this,"Windows is ready. Open Windows Explorer.",Toast.LENGTH_SHORT).show();
            return;
        }
        wineBooting=true;
        wineReady=false;
        wineInitializationError="";
        bootProgress.begin();
        refreshPowerPanel();
        if(!container.startWineboot()) {
            wineBooting=false;
            bootProgress.finish(false);
            refreshPowerPanel();
            Toast.makeText(this,"Could not start Wine initialization.",Toast.LENGTH_LONG).show();
        } else append("User started Wine initialization; phase progress enabled.");
    }
    private void selectPrerequisite(int code) {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,code);
    }
    private void selectWcp() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,PICK_WCP);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_SHORTCUT_ICON) {
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&shortcutEditor!=null)
                shortcutEditor.importIcon(data.getData());
            return;
        }
        if(request==PICK_SHARED_FOLDER) {
            boolean startup=startupWaitingForFolder;
            startupWaitingForFolder=false;
            if(result==RESULT_OK && data!=null && data.getData()!=null) {
                Uri tree=data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(tree,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedFolderDrive.persistTree(this,tree);
                    append("Android SAF folder permission granted for Wine R:");
                    synchronizeSharedFolder(startup);
                } catch(Exception e) {
                    append("SAF grant failed: "+e.getMessage());
                    if(startup)autoInstallAndBoot();
                }
            } else {
                append("Folder permission declined. Wine R: remains private, no Android access.");
                if(startup)autoInstallAndBoot();
            }
            return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==EXPORT_SESSION_LOG) {
            completeSessionLogExport(uri);
            return;
        }
        if(request==EXPORT_RAW_WINE_LOG) {
            completeRawWineExport(uri);
            return;
        }
        if(request==PICK_DEPENDENCIES_ZIP) {
            if(dependencyImporting){append("Dependency archive import already active");return;}
            dependencyImporting=true;
            dependencyImportPercent=0;
            dependencyImportLabel="Kopiranje Dependencies.zip";
            if(dependencyImportProgress!=null){dependencyImportProgress.setProgress(0);dependencyImportProgress.setVisibility(View.VISIBLE);}
            if(dependencyImportStatus!=null)dependencyImportStatus.setText(dependencyImportLabel+"  •  0%");
            new Thread(()->{
                try {
                    String summary=WineDependencyBundle.importUri(this,uri,(percent,label)->runOnUiThread(()->{
                        dependencyImportPercent=percent;
                        dependencyImportLabel=label;
                        if(dependencyImportProgress!=null)dependencyImportProgress.setProgress(percent);
                        if(dependencyImportStatus!=null)dependencyImportStatus.setText(label+"  •  "+percent+"%");
                    }));
                    append(summary);
                } catch(Exception error) {
                    append("Dependencies.zip import failed: "+error.getMessage());
                    runOnUiThread(()->dependencyImportLabel="Import nije uspio: "+error.getMessage());
                } finally {
                    runOnUiThread(()->{
                        dependencyImporting=false;
                        if(dependencyImportProgress!=null)dependencyImportProgress.setVisibility(View.GONE);
                        if(dependencyImportStatus!=null)dependencyImportStatus.setText(dependencyImportLabel);
                    });
                }
            },"drnas-dependencies-zip-import").start();
            return;
        }
        if(request==PICK_MONO || request==PICK_VC) {
            String kind=request==PICK_MONO?WinePrerequisites.MONO:WinePrerequisites.VC;
            append("Copying selected Wine prerequisite into private installer vault...");
            new Thread(()->{
                try {append(WinePrerequisites.importUri(this,uri,kind));}
                catch(Exception error){append("Prerequisite import failed: "+error.getMessage());}
            },"drnas-prerequisite-import").start();
            return;
        }
        if(request!=PICK_WCP)return;
        append("Importing selected WCP into private staged vault...");
        vault.importUri(uri,(message,success)->{
            append(message);
            if("Components".equals(section))render();
        });
    }
    private final ExitConfirmation exitConfirmation=new ExitConfirmation();
    @Override public void onBackPressed(){exitConfirmation.back(this);}

    @Override protected void onDestroy() {
        sessions.stop();
        vault.close();
        if(completeLog!=null) {
            try {completeLog.close();} catch(IOException ignored){}
        }
        super.onDestroy();
    }
}

