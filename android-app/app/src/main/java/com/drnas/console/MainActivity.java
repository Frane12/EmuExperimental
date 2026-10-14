package com.drnas.console;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private static final int BACK = 0xFF0A101B;
    private static final int PANEL = 0xFF132034;
    private static final int BORDER = 0xFF2C4054;
    private static final int TEXT = 0xFFE8F3FA;
    private static final int MUTED = 0xFF9BAFC3;
    private static final int ACCENT = 0xFF7BE4C3;
    private static final int WARM = 0xFFFFC476;

    private LinearLayout content;
    private TextView liveLog;
    private TextView stateBadge;
    private final StringBuilder log = new StringBuilder();
    private SessionManager sessions;
    private ComponentStore vault;
    private ContainerBootstrap container;
    private String section = "Console";
    private String currentGame = "Far Cry 3";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        vault = new ComponentStore(this);
        container = new ContainerBootstrap(this,new ContainerBootstrap.Callback() {
            @Override public void onLog(String msg) { runOnUiThread(()->append(msg)); }
            @Override public void onComplete(boolean guestStarted,String status) {
                runOnUiThread(()->{
                    append("Container status: "+status);
                    if("Diagnostics".equals(section))render();
                });
            }
        });
        sessions = new SessionManager(new SessionManager.Listener() {
            @Override public void onStateChanged(SessionManager.State status) {
                append("Session state: " + status);
                render();
            }
            @Override public void onLog(String line) { append(line); }
        });
        append("DRNAS CONSOLE · Runtime Core v0.3.0");
        append("No Windows runner connected — this APK does not launch games.");
        render();
    }
    @Override public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        render();
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radius));
        if (stroke != 0) bg.setStroke(dp(1), stroke);
        return bg;
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
        panel.setPadding(dp(17),dp(15),dp(17),dp(17));
        panel.setBackground(box(PANEL,BORDER,17));
        content.addView(panel,pad(0,14));
        return panel;
    }
    private Button button(String text, boolean prominent, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(prominent ? BACK : TEXT);
        b.setPadding(dp(12),0,dp(12),0);
        b.setMinimumHeight(dp(49));
        b.setMinHeight(dp(49));
        b.setBackground(box(prominent ? ACCENT : 0xFF21364A, prominent ? ACCENT : BORDER,12));
        b.setOnClickListener(action);
        return b;
    }
    private void addButton(LinearLayout root, String name, boolean main, View.OnClickListener action) {
        Button b=button(name,main,action);
        root.addView(b,pad(4,4));
    }
    private void heading(String text) {
        content.addView(label(text,30,TEXT,true),pad(0,9));
    }
    private void note(LinearLayout root,String message) {
        TextView t=label(message,12,WARM,false);
        t.setPadding(dp(12),dp(10),dp(12),dp(10));
        t.setBackground(box(0xFF30291E,0xFF65503A,11));
        root.addView(t,pad(12,0));
    }
    private void showSection(String s) { section=s; render(); }
    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACK);
        content=column();
        content.setPadding(dp(16),dp(26),dp(16),dp(30));
        scroll.addView(content);
        setContentView(scroll);
        LinearLayout header=horizontal();
        TextView glyph=label("◈",30,ACCENT,true);
        glyph.setPadding(dp(4),0,dp(13),0);
        header.addView(glyph);
        LinearLayout names=column();
        names.addView(label("DRNAS CONSOLE",20,TEXT,true));
        names.addView(label("ADAPTIVE GAMING LAB · V0.3 EXPERIMENTAL",10,ACCENT,true));
        header.addView(names);
        content.addView(header,pad(0,19));

        HorizontalScrollView bar=new HorizontalScrollView(this);
        bar.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons=horizontal();
        for(String name:new String[]{"Console","Components","Diagnostics"}) {
            Button b=button(name,name.equals(section),v->showSection(name));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,dp(42));
            lp.rightMargin=dp(8);
            buttons.addView(b,lp);
        }
        bar.addView(buttons);
        content.addView(bar,pad(0,22));

        switch(section) {
            case "Components": showComponents(); break;
            case "Diagnostics": showDiagnostics(); break;
            default: showConsole();
        }
        content.addView(label("EXPERIMENTAL BOOT · GUEST LAUNCH NOT YET CONNECTED",10,MUTED,true),pad(6,0));
    }

    private void showConsole() {
        content.addView(label("SESSION LAB / ANDROID",11,ACCENT,true),pad(0,6));
        heading("PC gaming, your rules.");
        content.addView(label("Proof of Android process lifecycle first. " +
                "Proton 10, FEXCore 2609, DXVK 1.10.3 and Turnip will remain modular.",14,MUTED,false),pad(0,20));
        LinearLayout game = card();
        game.addView(label("SELECTED GAME PROFILE",10,ACCENT,true),pad(0,8));
        game.addView(label(currentGame,23,TEXT,true),pad(0,9));
        HorizontalScrollView horizontal=new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout row=horizontal();
        for(String g:new String[]{"Far Cry 3","Crysis","Far Cry 4"}) {
            Button b=button(g,g.equals(currentGame),v->{currentGame=g;render();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,dp(43));
            lp.rightMargin=dp(6);
            row.addView(b,lp);
        }
        horizontal.addView(row);
        game.addView(horizontal,pad(0,6));
        game.addView(label("Proton 10 ARM64EC · FEXCore 2609 O3 · DXVK 1.10.3",12,MUTED,false),pad(9,5));
        note(game,"Game launch is NOT implemented in v0.2. Game cards represent profiles only.");

        LinearLayout bootPanel=card();
        bootPanel.addView(label("EXPERIMENTAL WINE CONTAINER BOOT",11,ACCENT,true),pad(0,9));
        bootPanel.addView(label("Stages Proton 10 WCP, verified FEX 2609, DXVK WCP and Turnip ZIP in an isolated prefix. Logs exact boot blockers.",13,MUTED,false),pad(0,10));
        addButton(bootPanel,"▶ Start container (experimental)",true,v->startContainer());
        note(bootPanel,"First boot may unpack gigabytes and take several minutes. Guest launch requires Bionic/glibc/X11/Vulkan support not yet included.");

        LinearLayout panel=card();
        panel.addView(label("REAL ANDROID SESSION TEST",11,ACCENT,true),pad(0,9));
        panel.addView(label("Starts a bounded diagnostic /system/bin/sh process; reads output asynchronously; allows stop/cleanup. Does NOT load Wine.",13,MUTED,false),pad(0,9));
        stateBadge=label("State: "+sessions.getState(),15,WARM,true);
        panel.addView(stateBadge,pad(0,9));
        addButton(panel,"▶ Start native smoke test",true,v->{
            if(!sessions.startAndroidSmokeTest())append("Start rejected: another session active");
        });
        addButton(panel,"■ Stop test process",false,v->sessions.stop());
        addButton(panel,"↗ Open diagnostics",false,v->showSection("Diagnostics"));
    }

    private void showComponents() {
        content.addView(label("VERSIONED COMPONENT VAULT",11,ACCENT,true),pad(0,6));
        heading("Your engine parts.");
        content.addView(label("Import Proton ARM64EC WCP, FEXCore 2609 WCP, DXVK WCP and your Turnip .zip. Start container extracts them into an isolated sandbox.",13,MUTED,false),pad(0,18));
        LinearLayout panel=card();
        panel.addView(label("STAGE A COMPONENT ARCHIVE",12,ACCENT,true),pad(0,8));
        addButton(panel,"+ Import .wcp / Turnip .zip",true,v->selectWcp());
        note(panel,"FEXCore PE bridges are verified at import. Proton TAR+ZSTD, DXVK TAR+XZ and Turnip ZIP are validated on boot. Live Windows loader is not yet included.");

        LinearLayout installed=card();
        installed.addView(label("LOCAL VAULT",12,ACCENT,true),pad(0,8));
        List<String> items=vault.list();
        if(items.isEmpty()) installed.addView(label("No components staged on this phone.",13,MUTED,false),pad(0,6));
        for(String item:items) installed.addView(label("◉ "+item,12,TEXT,false),pad(3,12));
        installed.addView(label("Our example backend inventory (not installed here):\n" +
                "Proton 10 ARM64EC • FEXCore 2609 O3 • DXVK 1.10.3 • Turnip A840",12,MUTED,false),pad(7,0));
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
        liveLog=label(log.toString(),12,ACCENT,false);
        liveLog.setTypeface(Typeface.MONOSPACE);
        liveLog.setTextIsSelectable(true);
        liveLog.setPadding(dp(12),dp(11),dp(12),dp(11));
        liveLog.setBackground(box(0xFF07101B,BORDER,11));
        panel.addView(liveLog,pad(0,10));
        addButton(panel,"▶ Start container (experimental)",true,v->startContainer());
        addButton(panel,"▶ Start native smoke test",false,v->{
            if(!sessions.startAndroidSmokeTest()) append("Start rejected: another session active");
        });
        addButton(panel,"■ Stop process",false,v->sessions.stop());
        addButton(panel,"Clear visible log",false,v->{
            log.setLength(0);
            append("Log reset");
        });
    }
    private void append(String message) {
        String time=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date());
        String msg="["+time+"] "+message+"\n";
        if(log.length()>30000)log.delete(0,Math.min(12000,log.length()));
        log.append(msg);
        if(liveLog!=null)liveLog.setText(log.toString());
    }
    private void startContainer() {
        if(!container.start()) append("Container bootstrap already in progress");
        else { append("Container boot requested; check live Diagnostics"); showSection("Diagnostics"); }
    }
    private void selectWcp() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,PICK_WCP);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_WCP||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        append("Importing selected WCP into private staged vault...");
        vault.importUri(uri,(message,success)->{
            append(message);
            if("Components".equals(section))render();
        });
    }
    @Override protected void onDestroy() {
        sessions.stop();
        vault.close();
        super.onDestroy();
    }
}
