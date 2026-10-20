package com.drnas.console;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;

/** Blender launcher; reuses the persistent DRNAS runtime. */
public final class BlenderLauncherActivity extends Activity {
    private TextView status;
    private boolean opened;
    private volatile boolean destroyed;
    private static final int PICK_FOLDER=3401, STORAGE_ACCESS=3402, LEGACY_STORAGE=3403;
    private boolean preparing, checking;
    private boolean labChoiceMade, r64WinecfgBaseline;
    private String r64LabMode="";
    private android.net.Uri pickedTree;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        // Match the latest EmuDRNAS Blender launch screen on EVERY launch.
        // Animated orbiting dots do not depend on debug output or installation.
        status=new TextView(this);
        status.setText(AppLanguage.t(this,"Pripremam Blender…"));
        LinearLayout panel=StartupAnimationView.blenderPanel(this,status);
        FrameLayout root=new FrameLayout(this);
        root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout brand=new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setAlpha(0.72f);
        ImageView mini=new ImageView(this);
        mini.setImageResource(R.drawable.ic_drnas_launcher);
        int iconSize=Math.round(18*getResources().getDisplayMetrics().density);
        brand.addView(mini,new LinearLayout.LayoutParams(iconSize,iconSize));
        TextView label=new TextView(this);
        label.setText("EMUDRNAS");
        label.setTextColor(0xffcbd7dd);label.setTextSize(11);
        label.setPadding(6,0,0,0);brand.addView(label);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-2,-2,
                Gravity.END|Gravity.BOTTOM);
        int margin=Math.round(12*getResources().getDisplayMetrics().density);
        position.rightMargin=margin;position.bottomMargin=margin;
        root.addView(brand,position);
        setContentView(root);
        if(state!=null) {
            String pending=state.getString("pickedTree");
            if(pending!=null)pickedTree=android.net.Uri.parse(pending);
            labChoiceMade=state.getBoolean("r64LabChoiceMade",false);
            r64WinecfgBaseline=state.getBoolean("r64WinecfgBaseline",false);
            r64LabMode=state.getString("r64LabMode","");
            // The external picker/settings activity will return its result to us.
            if(state.getBoolean("waitingExternal")) {waitingExternal=true;return;}
        }
        // The device-tested R64 GUI is the default. No seven-mode
        // diagnostic selection dialog, background/Vulkan probe or benchmark.
        // Retain the existing Vulkan-Wine path that mapped the real Blender GUI.
        if(isR64Lab()) {
            labChoiceMade=true;
            r64WinecfgBaseline=false;
            r64LabMode="vulkan";
        }
        if(r64WinecfgBaseline || "background".equals(r64LabMode) ||
                R64Arm64Isolation.isCpuOnly(r64LabMode)) prepareRuntime();
        else checkStartupAccess();
    }
    /**
     * A user-selected GDI-only Wine smoke test without Blender's 1.6 GB unpack,
     * project folder picker or Android storage permissions. Only available in
     * the isolated R64 LAB application; never alter published Blender builds.
     */
    private boolean isR64Lab() {
        return "com.drnas.console.mali.r64.lab".equals(getPackageName())
                || "com.drnas.console.mali.r64.gui.lab".equals(getPackageName());
    }
    private void showR64LabChoice() {
        // AlertDialog message and list are mutually exclusive in Android's
        // content panel: calling setMessage() here HIDES the setItems() rows.
        // Put descriptions in each list item and leave the message unset.
        new AppDialogBuilder(this).setTitle("DRNAS R64 LAB · odaberi test")
                .setItems(new CharSequence[]{
                        "1 · TEST WINECFG (referenca)",
                        "2 · TEST BLENDER BACKGROUND (bez GUI-ja)",
                        "3 · TEST BLENDER VULKAN (GUI + Wine SEH)",
                        "4 · CMD BEZ BOOTSTRAP (bez X11)",
                        "5 · CMD SA BOOTSTRAP (bez X11)",
                        "6 · BLENDER HEADLESS (bez X11)",
                        "7 · BLENDER --VERSION (bez GUI-ja)"},(dialog,index)->{
                    labChoiceMade=true;
                    r64LabMode=new String[]{"winecfg","background","vulkan",
                            R64Arm64Isolation.CMD_NORMAL,R64Arm64Isolation.CMD_BOOTSTRAP,
                            R64Arm64Isolation.HEADLESS,R64Arm64Isolation.VERSION}[index];
                    r64WinecfgBaseline="winecfg".equals(r64LabMode);
                    status.setText("R64 LAB test: "+r64LabMode+"…");
                    if(r64WinecfgBaseline || "background".equals(r64LabMode) ||
                            R64Arm64Isolation.isCpuOnly(r64LabMode))
                        prepareRuntime();
                    else checkStartupAccess();
                })
                .setNeutralButton("Zatvori",(dialog,which)->finish())
                .setCancelable(false).show();
    }
    private boolean waitingExternal;
    private boolean hasStorageAccess() {
        if(android.os.Build.VERSION.SDK_INT>=30)
            return android.os.Environment.isExternalStorageManager();
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                ==android.content.pm.PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                ==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    private void checkStartupAccess() {
        if(hasStorageAccess())continueProjectSelection();
        else requestStorageAccess();
    }
    private void continueProjectSelection() {
        if(destroyed||opened||preparing||checking)return;
        if(getIntent().getBooleanExtra("choose_project_folder",false))chooseFolder();
        else checkFolder();
    }
    private void storageAccessReturned() {
        if(destroyed)return;
        if(hasStorageAccess())continueProjectSelection();
        else new AppDialogBuilder(this).setTitle("Dozvola za datoteke")
                .setMessage("Pristup datotekama nije odobren. U Android postavkama uključi pristup svim datotekama za Blender kako bi otvaranje i spremanje projekata radilo.")
                .setPositiveButton("Otvori postavke",(d,w)->requestStorageAccess())
                .setNegativeButton("Zatvori",(d,w)->finish()).setCancelable(false).show();
    }
    private void checkFolder() {
        if(destroyed||opened||preparing||checking)return;
        if(!BlenderProjectDrive.hasGrant(this,BlenderProjectDrive.selected(this))) {
            chooseFolder();return;
        }
        checking=true;
        status.setText(AppLanguage.t(BlenderLauncherActivity.this,"Provjeravam disk D:…"));
        new Thread(()->{
            boolean usable=BlenderProjectDrive.ready(this);
            runOnUiThread(()->{
                checking=false;if(destroyed)return;
                if(usable)prepareRuntime();
                else new AppDialogBuilder(this).setTitle("Blender · Android disk D:")
                        .setMessage("Odabrana mapa nije dostupna za izravno čitanje i spremanje. Provjeri dozvolu ili odaberi drugu lokalnu mapu.")
                        .setPositiveButton("Dozvola za datoteke",(d,w)->requestStorageAccess())
                        .setNeutralButton("Odaberi mapu",(d,w)->chooseFolder())
                        .setNegativeButton("Zatvori",(d,w)->finish()).setCancelable(false).show();
            });
        },"blender-d-check").start();
    }
    private void chooseFolder() {
        status.setText(AppLanguage.t(BlenderLauncherActivity.this,"Odaberi Android mapu za Blenderov disk D:"));
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        android.net.Uri previous=BlenderProjectDrive.selected(this);
        if(previous!=null)intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,previous);
        try {waitingExternal=true;startActivityForResult(intent,PICK_FOLDER);}
        catch(android.content.ActivityNotFoundException e){waitingExternal=false;folderError("Androidov odabir mape nije dostupan.");}
    }
    private void requestStorageAccess() {
        status.setText(AppLanguage.t(this,"Dozvola za datoteke"));
        if(android.os.Build.VERSION.SDK_INT>=30) {
            if(hasStorageAccess()) {continueProjectSelection();return;}
            new AppDialogBuilder(this).setTitle("Izravno spremanje iz Blendera")
                    .setMessage("Wine treba pristup stvarnim datotekama kako bi Blenderov Save/Open radio na disku D:. Android za to traži širu dozvolu ‘Pristup svim datotekama’. D: će pokazivati samo odabranu mapu. Dozvolu možeš naknadno ukinuti u Android postavkama.")
                    .setPositiveButton("Otvori postavke",(d,w)->{
                        Intent intent=new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                android.net.Uri.parse("package:"+getPackageName()));
                        try {waitingExternal=true;startActivityForResult(intent,STORAGE_ACCESS);}
                        catch(android.content.ActivityNotFoundException e){
                            try {startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),STORAGE_ACCESS);}
                            catch(android.content.ActivityNotFoundException unavailable){waitingExternal=false;folderError("Postavka pristupa datotekama nije dostupna.");}
                        }
                    }).setNegativeButton("Odustani",(d,w)->finish()).setCancelable(false).show();
        } else {
            waitingExternal=true;
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE},LEGACY_STORAGE);
        }
    }
    private void folderError(String text) {
        if(destroyed)return;
        new AppDialogBuilder(this).setTitle("Blender · disk D:").setMessage(text)
                .setPositiveButton("Odaberi mapu",(d,w)->chooseFolder())
                .setNegativeButton("Zatvori",(d,w)->finish()).setCancelable(false).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);waitingExternal=false;
        if(request==PICK_FOLDER) {
            if(result!=RESULT_OK||data==null||data.getData()==null){folderError("Odaberi mapu za projekte prije pokretanja.");return;}
            pickedTree=data.getData();
            try {BlenderProjectDrive.remember(this,pickedTree,data.getFlags());}
            catch(Exception e){folderError(e.getMessage());return;}
            // Startup has checked file permission; now validate the selected local folder.
            checkFolder();
        } else if(request==STORAGE_ACCESS)storageAccessReturned();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==LEGACY_STORAGE) {waitingExternal=false;storageAccessReturned();}
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(pickedTree!=null)out.putString("pickedTree",pickedTree.toString());
        out.putBoolean("waitingExternal",waitingExternal);
        out.putBoolean("r64LabChoiceMade",labChoiceMade);
        out.putBoolean("r64WinecfgBaseline",r64WinecfgBaseline);
        out.putString("r64LabMode",r64LabMode);
        super.onSaveInstanceState(out);
    }
    private void prepareRuntime() {
        if(preparing||opened||destroyed)return;
        preparing=true;
        if(ready())open();
        else new ContainerBootstrap(this,new ContainerBootstrap.Callback() {
            public void onLog(String line) {
                // Quiet GUI LAB: no per-line diagnostic Logcat flood.
                runOnUiThread(()->{if(!destroyed)status.setText(AppLanguage.t(BlenderLauncherActivity.this,"Prva priprema Blender okruženja…"));});
            }
            public void onComplete(boolean started,String result) {
                runOnUiThread(()->{
                    if(destroyed)return;
                    if(ready())open();
                    else new AppDialogBuilder(BlenderLauncherActivity.this)
                            .setTitle("Blender se nije pripremio")
                            .setMessage(result)
                            .setPositiveButton("Dijagnostika",(d,w)->{
                                startActivity(new Intent(BlenderLauncherActivity.this,MainActivity.class));finish();
                            }).setNegativeButton("Zatvori",(d,w)->finish()).show();
                });
            }
        }).startWineboot();
    }
    private boolean ready() {
        try {
            File plan=new File(getFilesDir(),"containers/default/boot-plan.json");
            return plan.isFile() && WcpRuntimeStager.readJson(plan)
                    .optBoolean("winebootGuestVerified",false);
        }catch(Exception e){return false;}
    }
    private void open() {
        if(opened||destroyed)return;
        opened=true;
        startActivity(new Intent(this,WineGuiActivity.class)
                .putExtra("DRNAS_GUI_APP",BlenderShellLaunch.ID)
                .putExtra("DRNAS_R64_WINECFG_BASELINE",isR64Lab() && r64WinecfgBaseline)
                .putExtra("DRNAS_R64_LAB_MODE",isR64Lab()?r64LabMode:""));
        finish();
    }
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
}

