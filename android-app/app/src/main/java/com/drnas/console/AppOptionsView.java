package com.drnas.console;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.*;

/** Global app settings, separate from the launchpad and per-icon profiles. */
final class AppOptionsView extends LinearLayout {
    private final Activity a;
    private final SharedPreferences prefs;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    static SharedPreferences desktop(Context c){return c.getSharedPreferences("WineGuiActivity",Context.MODE_PRIVATE);}
    AppOptionsView(Activity activity,Runnable languageChanged){
        super(activity);a=activity;prefs=desktop(a);setOrientation(VERTICAL);
        title("JEZIK APLIKACIJE");
        LinearLayout languages=new LinearLayout(a);
        for(String language:new String[]{"hr","en"}){
            Button b=button("hr".equals(language)?"Hrvatski":"English",()->{
                AppLanguage.set(a,language);languageChanged.run();
            });
            if(language.equals(AppLanguage.selected(a)))b.setTextColor(0xff75f2bd);
            languages.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));
        }
        addView(languages);note("Jezik se odnosi na Android sučelje, ne na Windows programe i dijagnostičke logove.");
        title("PRIKAZ I IZVEDBA");
        toggle("Kompaktan početni ekran","ui_compact",true);
        Button fps=button("",()->{});updateFps(fps);
        fps.setOnClickListener(v->DrnasDxvkDialog.show(a,WineGuiActivity.savedFrameCap(a),false,
                cap->{WineGuiActivity.saveFrameCap(a,cap);updateFps(fps);},()->{}));
        addView(fps,new LinearLayout.LayoutParams(-1,dp(42)));
        chooser("DXVK",new String[]{"Standard","Async"},DxvkVariants.ASYNC.equals(DxvkVariants.selected(a))?1:0,
                value->DxvkVariants.select(a,value==1?DxvkVariants.ASYNC:DxvkVariants.STANDARD));
        toggle("Stretch · puni zaslon","full_screen_stretch",true);
        toggle("Živa pozadina desktopa","desktop_live",true);
        toggle("Pelud na sučelju · bura","ui_bora_pollen",true);
        toggle("Veći Wine taskbar","desktop_taskbar_large",true);
        toggle("Prikaži FPS / RAM / bateriju","perf_hud",false);
        toggle("Prikaži pet tipki na desktopu","desktop_keys",true);
        Switch turnip=new Switch(a);turnip.setText(AppLanguage.t(a,"A840 H2.9 automatski"));
        turnip.setTextColor(0xffe2f7ee);turnip.setChecked(TurnipRuntime.autoH29Enabled(a));
        turnip.setOnCheckedChangeListener((b,on)->TurnipRuntime.setAutoH29Enabled(a,on));addView(turnip);
        title("JOYSTICK / XINPUT");
        String mode=prefs.getString("control_mode","off");
        chooser("Kontrole pri paljenju",new String[]{"Isključeno","WASD joystick","Xbox XInput"},
                "wasd".equals(mode)?1:"xinput".equals(mode)?2:0,
                value->prefs.edit().putString("control_mode",value==1?"wasd":value==2?"xinput":"off").apply());
        toggle("Relativna kamera","relative_camera",false);
        TextView gain=note("");float saved=prefs.getFloat("relative_sensitivity",7.5f);
        SeekBar slider=new SeekBar(a);slider.setMax(95);
        slider.setProgress(Math.min(95,Math.max(0,Math.round(saved*10)-5)));
        gain.setText(AppLanguage.t(a,"Osjetljivost kamere")+" · "+String.format(java.util.Locale.ROOT,"%.1f×",saved));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int value,boolean user){
                gain.setText(AppLanguage.t(a,"Osjetljivost kamere")+" · "+String.format(java.util.Locale.ROOT,"%.1f×",(value+5)/10f));
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){
                prefs.edit().putFloat("relative_sensitivity",(s.getProgress()+5)/10f).apply();
            }
        });addView(slider,new LinearLayout.LayoutParams(-1,dp(34)));
        addView(button("Uredi raspored joysticka / XML",()->a.startActivity(new Intent(a,XInputLayoutEditorActivity.class))),
                new LinearLayout.LayoutParams(-1,dp(44)));
        note("Globalne postavke vrijede pri idućem paljenju desktopa. Postavke pojedine ikone imaju prednost.");
    }
    private void updateFps(Button b){
        int cap=WineGuiActivity.savedFrameCap(a);
        b.setText("DXVK FPS · "+(cap==0?AppLanguage.t(a,"Bez limita"):Integer.toString(cap)));
    }
    private Button button(String label,Runnable action){
        Button b=new Button(a);b.setText(AppLanguage.t(a,label));b.setAllCaps(false);
        b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setTextColor(0xffd3f9ee);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff214354));
        b.setOnClickListener(v->action.run());return b;
    }
    private void title(String text){
        TextView t=new TextView(a);t.setText(AppLanguage.t(a,text));t.setTextSize(11);
        t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);t.setTextColor(0xff7de7c4);
        t.setPadding(0,dp(10),0,dp(4));addView(t);
    }
    private TextView note(String text){
        TextView t=new TextView(a);t.setText(AppLanguage.t(a,text));t.setTextSize(11);
        t.setTextColor(0xffa2c3cb);t.setPadding(0,dp(3),0,dp(5));addView(t);return t;
    }
    private void toggle(String label,String key,boolean fallback){
        Switch toggle=new Switch(a);toggle.setText(AppLanguage.t(a,label));toggle.setTextSize(12);
        toggle.setTextColor(0xffe2f7ee);toggle.setMinHeight(dp(40));toggle.setChecked(prefs.getBoolean(key,fallback));
        toggle.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean(key,on).apply());addView(toggle);
    }
    private void chooser(String label,String[] values,int selected,java.util.function.IntConsumer changed){
        LinearLayout row=new LinearLayout(a);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t=new TextView(a);t.setText(AppLanguage.t(a,label));t.setTextColor(0xffd3f9ee);t.setTextSize(12);
        row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        Spinner spinner=new Spinner(a);
        spinner.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,AppLanguage.array(a,values)){
            @Override public android.view.View getView(int p,android.view.View v,android.view.ViewGroup parent){
                TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(0xffd3f9ee);t.setTextSize(12);return t;
            }
        });
        spinner.setSelection(selected);final int[] current={selected};
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,android.view.View v,int position,long id){
                if(current[0]!=position){current[0]=position;changed.accept(position);}
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });row.addView(spinner,new LinearLayout.LayoutParams(0,dp(42),1));addView(row);
    }
}
