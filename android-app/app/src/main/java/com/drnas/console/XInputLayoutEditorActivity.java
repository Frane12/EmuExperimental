package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/** Offline all-screen XInput layout editor, available before Wine Explorer. */
public final class XInputLayoutEditorActivity extends Activity {
    private DrnasPadLayoutView editor;
    private XInputLayoutPreset layout;

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable glass(int color) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(14));
        d.setStroke(dp(1),0x8863bbd5);
        return d;
    }
    private Button button(String text,LinearLayout bar,Runnable action){
        Button b=new Button(this);b.setText(AppLanguage.t(this,text));b.setAllCaps(false);
        b.setTextSize(12);b.setTextColor(0xffeff8fd);
        b.setBackground(glass(0xe5234056));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1f);
        lp.setMargins(dp(3),0,dp(3),0);
        bar.addView(b,lp);
        b.setOnClickListener(v->action.run());
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xff071420);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        int w=getResources().getDisplayMetrics().widthPixels;
        int h=getResources().getDisplayMetrics().heightPixels;
        layout=XInputLayoutPreset.load(this,w,h);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(0xff0a1c2a);
        editor=new DrnasPadLayoutView(this,null,true);
        editor.setPreset(layout);
        root.addView(editor,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(7),dp(6),dp(7),dp(6));
        bar.setBackground(glass(0xee0f2739));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,dp(58),
                Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        p.topMargin=dp(8);
        root.addView(bar,p);
        button("← Natrag",bar,this::finish);
        button("Učitaj XML",bar,this::choose);
        button("Reset",bar,()->{
            layout=XInputLayoutPreset.defaults(editor.getWidth(),editor.getHeight(),
                    getResources().getDisplayMetrics().density);
            editor.setPreset(layout);
        });
        button("💾 Spremi",bar,this::saveDialog);
        setContentView(root);
    }
    private void saveDialog(){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText("Crysis");
        new AppDialogBuilder(this).setTitle("Spremi raspored kontrolera")
                .setMessage("Raspored se sprema u XML (Downloads/DRNAS/JoystickPresets)."+
                        " Aplikacija ga pamti i koristi pri sljedećem otvaranju Explorera.")
                .setView(input).setPositiveButton("Spremi",(dialog,which)->{
                    try{
                        layout.save(this,input.getText().toString());
                        AppLanguage.toast(this,"XML spremljen i preset aktiviran",
                                Toast.LENGTH_LONG).show();
                    }catch(IOException error){
                        AppLanguage.toast(this,"Spremanje: "+error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Odustani",null).show();
    }
    private void choose(){
        File[] files=XInputLayoutPreset.directory(this).listFiles((dir,name)->name.endsWith(".xml"));
        if(files==null||files.length==0){
            AppLanguage.toast(this,"Još nema XML preseta",Toast.LENGTH_SHORT).show();return;
        }
        Arrays.sort(files,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
        String[] names=new String[files.length];
        for(int i=0;i<files.length;i++)names[i]=files[i].getName();
        new AppDialogBuilder(this).setTitle("Odaberi spremljeni XML")
                .setItems(names,(dialog,which)->{
                    getSharedPreferences("joystick",MODE_PRIVATE).edit()
                            .putString("selected",names[which]).apply();
                    layout=XInputLayoutPreset.load(this,editor.getWidth(),editor.getHeight());
                    editor.setPreset(layout);
                }).setNegativeButton("Odustani",null).show();
    }
}
