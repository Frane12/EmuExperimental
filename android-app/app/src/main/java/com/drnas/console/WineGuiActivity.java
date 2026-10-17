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
import android.text.InputType;
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
    private LinearLayout dependencyPanel;
    private TextView dependencyProgressText;
    private ProgressBar dependencyProgressBar;
    private boolean cpuCanvasFallback;
    private volatile boolean verboseLogs; // OFF by default, live menu toggle
    private VirtualStickView virtualStick;
    private volatile boolean desktopLaunchRequested;
    private volatile boolean desktopFailed;
    private Button shareLog;
    private boolean keyboardPending;
    private int keyboardAttempts;

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
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

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
        FrameLayout.LayoutParams sl=new FrameLayout.LayoutParams(dp(132),dp(46),Gravity.TOP|Gravity.END);
        sl.topMargin=dp(5);sl.rightMargin=dp(56);
        layout.addView(shareLog,sl);
        shareLog.setOnClickListener(v->shareGuiLog());
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
                .setItems(new String[]{"Keyboard","Toggle CPU/GPU desktop Canvas","Reset Wine","Exit desktop","Pošalji log","Spremi log","Task Manager","Promijeni prozor (Alt+Tab)","Fullscreen / prozor (Alt+Enter)","Tekst → Wine (IME fallback)","Vulkan FIFO (izoliran)","Vulkan IMMEDIATE (izoliran)","WSI A: Create/Destroy bez Present","WSI B: FIFO + XSync","Eksperiment 1: X11 bez Vulkana","Eksperiment 2: Vulkan bez slike + provjera veze","Eksperiment 3: Prikaz slike + provjera veze","Virtual stick (WASD) ON/OFF","Dijagnostika / logovi: ON/OFF"},(dialog,which)->{
                    if(which==0) {
                        keyboardPending=true;
                        keyboardAttempts=0;
                        ((AlertDialog)dialog).setOnDismissListener(d ->
                                xserver.getScreen().postDelayed(this::showKeyboard,120));
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
                    else if(which==18) {
                        verboseLogs=!verboseLogs;
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
                }).show());
        menu.setOnLongClickListener(v->{saveGuiLog();return true;});

        Button tasks=new Button(this);
        tasks.setText("Task Manager");tasks.setAllCaps(false);
        FrameLayout.LayoutParams tl=new FrameLayout.LayoutParams(dp(144),dp(46),Gravity.TOP|Gravity.END);
        tl.topMargin=dp(5);tl.rightMargin=dp(192);
        layout.addView(tasks,tl);tasks.setOnClickListener(v->openTaskManager());
        // An always-visible keyboard button remains usable if a Wine game changes focus.
        Button keyboard=new Button(this);
        keyboard.setText("⌨");
        keyboard.setContentDescription("Prikaži Android tipkovnicu za Wine");
        keyboard.setTextSize(20);
        keyboard.setAllCaps(false);
        FrameLayout.LayoutParams kl=new FrameLayout.LayoutParams(dp(64),dp(46),Gravity.TOP|Gravity.END);
        kl.topMargin=dp(5);kl.rightMargin=dp(343);
        layout.addView(keyboard,kl);
        keyboard.setOnClickListener(v->{
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
        setContentView(layout);
        try {
            AutomaticCrashLog.begin(this);
            raw=new RawWineTrace(new File(getFilesDir(),"logs"),"latest-wine-gui-raw.txt");
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
                    try {session.run(WineGuiActivity.this,WineGuiActivity.this::log,raw,launchApp);}
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

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused) {immersive();if(keyboardPending)xserver.getScreen().post(this::showKeyboard);}
    }

    @Override protected void onPause() {
        if(virtualStick!=null)virtualStick.releaseAll();
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
        AutomaticCrashLog.end(this);
        if(session!=null)new Thread(()->{session.close();if(xserver!=null)xserver.stop();},"drnas-desktop-stop").start();
        else if(xserver!=null)xserver.stop();
        super.onDestroy();
    }
}


