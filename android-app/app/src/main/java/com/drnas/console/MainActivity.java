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
        new AlertDialog.Builder(this)
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
                    if("Components".equals(section))render();
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
    private StateListDrawable buttonSurface(boolean primary) {
        StateListDrawable layers=new StateListDrawable();
        layers.addState(new int[]{android.R.attr.state_pressed},
                glass(primary?0xFF4DC5A9:0xF0466480,
                      primary?0xFF36A48F:0xF034536E,14));
        layers.addState(new int[]{},
                glass(primary?0xFF81EAD2:0xCF234059,
                      primary?0xFF4ACDAF:0xCF142C45,14));
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
        t.setText(text);
        t.setTextSize(size);
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
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }
    private LinearLayout card() {
        LinearLayout panel = column();
        panel.setPadding(dp(19),dp(17),dp(19),dp(19));
        panel.setBackground(glass(0xDF192D43,0xEA0D1C30,21));
        panel.setElevation(dp(3));
        content.addView(panel,pad(0,14));
        return panel;
    }
    private Button button(String text, boolean prominent, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        b.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{0xFF829BAD,prominent?0xFF08202B:TEXT}));
        b.setPadding(dp(15),0,dp(15),0);
        b.setMinimumHeight(dp(46));
        b.setMinHeight(dp(46));
        b.setBackground(buttonSurface(prominent));
        b.setStateListAnimator(null);
        b.setOnClickListener(action);
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
        winePowerLed=null;windowsExplorerButton=null;turnOnWindowsButton=null;
        wineStartupBar=null;wineStartupText=null;
        ScrollView scroll = new ScrollView(this);
        pageScroll=scroll;renderedSection=section;
        SystemBarInsets.apply(scroll);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF080F1C,0xFF0D2231,0xFF070D19}));
        content=column();
        content.setPadding(dp(18),dp(22),dp(18),dp(36));
        scroll.addView(content);
        setContentView(scroll);
        LinearLayout header=horizontal();
        header.setPadding(dp(12),dp(12),dp(12),dp(12));
        header.setBackground(glass(0xD51C3749,0xD10D1D32,18));
        TextView glyph=label("◈",26,ACCENT,true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(box(0xAA214A58,0x6685E7D2,15));
        header.addView(glyph,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout names=column();
        names.setPadding(dp(12),0,0,0);
        TextView brand=label("EmuDrnas",23,TEXT,true);
        brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        names.addView(brand);
        TextView subtitle=label("preAlpha 0.1   •   WINDOWS RUNTIME",10,ACCENT,true);
        subtitle.setLetterSpacing(.09f);
        names.addView(subtitle);
        header.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView prealpha=label("EARLY ACCESS",9,0xFFE7D6A5,true);
        prealpha.setGravity(Gravity.CENTER);
        prealpha.setPadding(dp(7),dp(7),dp(7),dp(7));
        prealpha.setBackground(box(0x7757422B,0x99866F51,10));
        header.addView(prealpha);
        content.addView(header,pad(0,19));

        HorizontalScrollView bar=new HorizontalScrollView(this);
        bar.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons=horizontal();
        for(String name:new String[]{"Console","Components","Shortcuts"}) {
            Button b=button(name,name.equals(section),v->showSection(name));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,dp(42));
            lp.rightMargin=dp(8);
            buttons.addView(b,lp);
        }
        bar.addView(buttons);
        content.addView(bar,pad(0,21));

        switch(section) {
            case "Components": showComponents(); break;
            case "Shortcuts": showShortcuts(false); break;
            case "Diagnostics": showDiagnostics(); break;
            default: showConsole();
        }

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
        if(turnOnWindowsButton!=null)turnOnWindowsButton.setEnabled(!wineBooting && !ready);
        if(windowsExplorerButton!=null)windowsExplorerButton.setEnabled(ready);
        if(resetWineButton!=null)resetWineButton.setEnabled(!wineBooting && !container.isBusy() && readyForDesktop());
        if(wineStartupBar!=null) {
            wineStartupBar.setVisibility(wineBooting || bootProgress.percent()>0?
                    View.VISIBLE:View.GONE);
            wineStartupBar.setProgress(bootProgress.percent());
        }
        if(wineStartupText!=null) {
            wineStartupText.setText(bootProgress.phase()+
                    (wineBooting ? "  •  "+bootProgress.percent()+"%" : ""));
            wineStartupText.setTextColor(ready?ACCENT:wineBooting?WARM:MUTED);
        }
    }

    /** Two-step power + Explorer UI; logs exist only in private files. */
    private void showConsole() {
        MeadowBannerView meadow=new MeadowBannerView(this);
        content.addView(meadow,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(122)));
        showShortcuts(true);
        LinearLayout desktop=card();
        LinearLayout title=horizontal();
        TextView header=label("◈  WINDOWS ENVIRONMENT",11,ACCENT,true);
        header.setLetterSpacing(.10f);
        title.addView(header,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView secure=label("LOCAL · OFFLINE",10,MUTED,true);
        title.addView(secure);
        desktop.addView(title,pad(0,14));
        LinearLayout power=horizontal();
        turnOnWindowsButton=button("⏻  TURN ON WINDOWS",true,v->startWineboot());
        LinearLayout.LayoutParams onParams=new LinearLayout.LayoutParams(0,dp(52),1);
        power.addView(turnOnWindowsButton,onParams);
        winePowerLed=new View(this);
        LinearLayout.LayoutParams ledParams=new LinearLayout.LayoutParams(dp(23),dp(23));
        ledParams.leftMargin=dp(15);
        power.addView(winePowerLed,ledParams);
        desktop.addView(power,pad(0,8));
        resetWineButton=button("↻  RESET WINE",false,v->resetWine());
        desktop.addView(resetWineButton,pad(4,6));

        addButton(desktop,"🎮  EDITOR XINPUT KONTROLERA · XML",false,
                v->startActivity(new Intent(this,XInputLayoutEditorActivity.class)));
        windowsExplorerButton=button("▣  WINDOWS EXPLORER",false,v->openWineGui());
        desktop.addView(windowsExplorerButton,pad(10,12));
        // Exact-GPU H2.9 auto selection. This is a Vulkan loader *request*;
        // whether the driver actually loaded is reported by Wine probe logs.
        final Button turnipPreference=button("",false,v->{});
        final Runnable updateTurnipLabel=()->turnipPreference.setText(
                "A840 NeuroFlow H2.9 auto: "+
                (TurnipRuntime.autoH29Enabled(this)?"ON  ●":"OFF  ○"));
        updateTurnipLabel.run();
        turnipPreference.setOnClickListener(v->{
            boolean next=!TurnipRuntime.autoH29Enabled(this);
            TurnipRuntime.setAutoH29Enabled(this,next);
            updateTurnipLabel.run();
            android.widget.Toast.makeText(this,
                    next?"A840 H2.9 automatski će se pokušati učitati pri sljedećem pokretanju Explorera":
                            "A840 H2.9 auto isključen; koristi prethodni odabir",
                    android.widget.Toast.LENGTH_LONG).show();
        });
        desktop.addView(turnipPreference,pad(7,4));
        // Persisted pre-Explorer selection. The same package contains BOTH
        // DXVK 1.10.3 variants; changing it requires restarting Wine.
        final Button dxvkVariant=button("",false,v->{});
        final Runnable updateDxvkLabel=()->{
            boolean async=DxvkVariants.ASYNC.equals(DxvkVariants.selected(this));
            dxvkVariant.setText(async?
                    "DXVK 1.10.3 Async   •   UKLJUČEN  ✓":
                    "DXVK 1.10.3 Standard   •   Async OFF");
            dxvkVariant.setTextColor(async?ACCENT:TEXT);
        };
        updateDxvkLabel.run();
        dxvkVariant.setOnClickListener(v->{
            String mode=DxvkVariants.toggle(this);
            updateDxvkLabel.run();
            android.widget.Toast.makeText(this,
                    "Odabran DXVK "+mode+
                    ". Primjenjuje se pri sljedećem pokretanju Explorera.",
                    android.widget.Toast.LENGTH_LONG).show();
        });
        desktop.addView(dxvkVariant,pad(7,4));
        note(desktop,"A840 H2.9 je ugrađen u APK. Automatska aktivacija " +
                "zahtijeva potvrđen Adreno 840 i ispravan Vulkan loader. " +
                "Točan rezultat provjeri u Wine/Vulkan logu; ON nije dokaz aktivacije.");
        addButton(desktop,"Pošalji zapis pada / zadnjeg desktopa",false,v->shareCrashDiagnostics());
        wineStartupBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        wineStartupBar.setMax(100);
        wineStartupBar.setIndeterminate(false);
        wineStartupBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        desktop.addView(wineStartupBar,pad(10,3));
        wineStartupText=label("",12,MUTED,false);
        desktop.addView(wineStartupText,pad(7,6));
        refreshPowerPanel();

        TextView runtimeInfo=label("1280 × 720    •    Proton 11 ARM64EC    •    DXVK / FEX",12,MUTED,false);
        runtimeInfo.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(runtimeInfo,pad(9,17));
        LinearLayout folder=card();
        sectionEyebrow(folder,"EXPLORER  /  ANDROID FILES");
        addButton(folder,"Select Android folder for R:",false,v->requestSharedFolder());
        addButton(folder,"Refresh R: files",false,v->synchronizeSharedFolder(false));
        note(folder,"While the desktop is running, R: checks for new Android folders every 20 seconds. SAF uses a private import-only mirror, not a real Android filesystem mount.");
        addButton(folder,"Wine configuration",false,v->startConfig());
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
        int columns=4;
        LinearLayout row=null;
        for(int i=0;i<count;i++) {
            if(i%columns==0) {
                row=horizontal();row.setGravity(Gravity.CENTER_VERTICAL);
                panel.addView(row,pad(3,4));
            }
            WineShortcuts.Entry entry=shortcuts.get(i);
            LinearLayout tile=column();
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(3),dp(8),dp(3),dp(7));
            tile.setBackground(glass(0xC01B4355,0xC010293F,12));
            TextView icon=label(entry.glyph,27,ACCENT,true);
            icon.setGravity(Gravity.CENTER);
            tile.addView(icon);
            TextView name=label(entry.title,11,TEXT,false);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            tile.addView(name,pad(5,0));
            tile.setContentDescription(entry.title+" · "+entry.status);
            tile.setOnClickListener(v->launchWineShortcut(entry));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(78),1);
            if(i%columns!=0)lp.leftMargin=dp(5);
            row.addView(tile,lp);
        }
        if(preview) {
            addButton(panel,"＋  Više prečaca",false,v->showSection("Shortcuts"));
        } else {
            addButton(panel,"↻  Osvježi Wine prečace",false,v->render());
            addButton(panel,"▣  Otvori Wine Explorer za instalaciju programa",false,
                    v->openWineGui());
            note(panel,"Za AkelPad, Notepad++ i igre instaliraj program u Wine pa " +
                    "napravi prečac (Create Shortcut / Desktop). " +
                    "Prečaci se čitaju iz Desktop i Start Menu mapa Wine prefiksa. " +
                    "Neki alati poput WordPada i Painta ovise o sadržaju Proton/Wine paketa.");
        }
        TextView footnote=label("ⓘ  Wine shortcuts appear here. "+
                (!wineReady?"Pokreni Windows za instalirane programe.":""),11,MUTED,false);
        panel.addView(footnote,pad(10,0));
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
        // A new Activity inherits the same persistent prefix and starts the
        // named guest app inside Wine's virtual desktop. No host shell parsing.
        startActivity(new Intent(this,WineGuiActivity.class)
                .putExtra("DRNAS_GUI_APP",entry.id));
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
        dependencyImportProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        dependencyImportProgress.setMax(100);
        dependencyImportProgress.setProgress(dependencyImportPercent);
        dependencyImportProgress.setIndeterminate(false);
        dependencyImportProgress.setVisibility(dependencyImporting?View.VISIBLE:View.GONE);
        panel.addView(dependencyImportProgress,pad(10,4));
        dependencyImportStatus=label(dependencyImportLabel+
                (dependencyImporting?"  •  "+dependencyImportPercent+"%":""),12,MUTED,false);
        panel.addView(dependencyImportStatus,pad(7,6));
        note(panel,"Dependencies.zip is imported once through Android Files, not downloaded. Offline first-time installation starts before Wine Explorer: DirectX June 2010, VC++ x86/x64, OpenAL, XNA and VulkanRT. .NET and websetup are skipped. Check the GUI install progress bar and private log for failures.");
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
    @Override protected void onDestroy() {
        sessions.stop();
        vault.close();
        if(completeLog!=null) {
            try {completeLog.close();} catch(IOException ignored){}
        }
        super.onDestroy();
    }
}

