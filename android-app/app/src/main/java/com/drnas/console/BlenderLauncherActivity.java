package com.drnas.console;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;

/** Private Blender presentation build; reuses the persistent DRNAS runtime. */
public final class BlenderLauncherActivity extends Activity {
    private TextView status;
    private boolean opened;
    private volatile boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xff202024);
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.ic_blender);
        int size=Math.round(100*getResources().getDisplayMetrics().density);
        panel.addView(logo,new LinearLayout.LayoutParams(size,size));
        TextView name=new TextView(this);name.setText("Blender");
        name.setTextSize(25);name.setTextColor(0xffeeeeee);
        panel.addView(name);
        status=new TextView(this);status.setText("Pripremam Blender…");
        status.setTextSize(13);status.setTextColor(0xffaaaab2);status.setPadding(16,20,16,20);
        status.setGravity(Gravity.CENTER);panel.addView(status);
        setContentView(panel);
        if(ready())open();
        else new ContainerBootstrap(this,new ContainerBootstrap.Callback() {
            public void onLog(String line) {
                android.util.Log.i("DRNAS-Blender",line);
                runOnUiThread(()->{if(!destroyed)status.setText("Prva priprema Blender okruženja…");});
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
                .putExtra("DRNAS_GUI_APP",BlenderShellLaunch.ID));
        finish();
    }
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
}
