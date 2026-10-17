package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.KeyEvent;

/** Five configurable floating desktop keys: mappings and placement are saved.
 * Edits are staged in the dialog, committed only on SPREMI, and can be applied
 * immediately to an active Wine X11 session without restarting Wine.
 */
final class DesktopKeysSettings {
    static final int SLOT_COUNT=5;
    private static final String PREFS="drnas-desktop-keys";
    private static final String[] LABELS={
            "Lijevi klik","Desni klik","Enter","Esc","Backspace","Tab","Space",
            "Strelica ↑","Strelica ↓","Strelica ←","Strelica →","Ctrl","Shift","Alt",
            "W","A","S","D","F1","F2","F5","Delete"
    };
    private static final int[] KEYS={
            -1,-3,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_TAB,KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_SHIFT_LEFT,KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_W,KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_F1,KeyEvent.KEYCODE_F2,KeyEvent.KEYCODE_F5,
            KeyEvent.KEYCODE_FORWARD_DEL
    };
    private static final int[] DEFAULT={0,1,2,3,4};
    private DesktopKeysSettings(){}
    private static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static int checkedIndex(int index){
        return index>=0 && index<LABELS.length?index:0;
    }
    static int option(Context context,int slot){
        if(slot<0||slot>=SLOT_COUNT)throw new IllegalArgumentException("Invalid key slot");
        int value=prefs(context).getInt("slot_"+slot,DEFAULT[slot]);
        return value>=0&&value<KEYS.length?value:DEFAULT[slot];
    }
    static int key(Context context,int slot){return KEYS[option(context,slot)];}
    static String label(Context context,int slot){
        int option=option(context,slot);
        if(option==0)return AppLanguage.t(context,"Left mouse");
        if(option==1)return AppLanguage.t(context,"Right mouse");
        return LABELS[option];
    }
    static int horizontal(Context context){return clamp(prefs(context).getInt("x_percent",50),0,100);}
    static int lift(Context context){return clamp(prefs(context).getInt("lift_dp",8),8,160);}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    static boolean save(Context context,int[] mapping,int x,int lift){
        if(mapping==null || mapping.length!=SLOT_COUNT)return false;
        for(int selection:mapping)if(selection<0||selection>=KEYS.length)return false;
        SharedPreferences.Editor editor=prefs(context).edit();
        for(int i=0;i<SLOT_COUNT;i++)editor.putInt("slot_"+i,mapping[i]);
        return editor.putInt("x_percent",clamp(x,0,100))
                .putInt("lift_dp",clamp(lift,8,160)).commit();
    }
    static void editor(Activity activity,Runnable changed){
        int dp=Math.round(activity.getResources().getDisplayMetrics().density*8);
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp*2,dp,dp*2,dp);
        TextView tip=new TextView(activity);
        tip.setText("Svakoj od 5 tipki odaberi radnju i namjesti položaj.\n" +
                "Promjene vrijede tek kad pritisneš SPREMI.");
        tip.setTextColor(0xffd5f4ee);
        panel.addView(tip);
        Spinner[] buttons=new Spinner[SLOT_COUNT];
        String[] localized=LABELS.clone();
        localized[0]=AppLanguage.t(activity,"Left mouse");
        localized[1]=AppLanguage.t(activity,"Right mouse");
        for(int i=0;i<SLOT_COUNT;i++){
            TextView header=new TextView(activity);
            header.setText("TIPKA "+(i+1));header.setTextColor(0xff85dfc7);
            header.setPadding(0,dp,0,0);panel.addView(header);
            Spinner spinner=new Spinner(activity);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(
                    activity,android.R.layout.simple_spinner_dropdown_item,localized);
            spinner.setAdapter(adapter);
            spinner.setSelection(option(activity,i));
            buttons[i]=spinner;
            panel.addView(spinner);
        }
        TextView xLabel=new TextView(activity);xLabel.setTextColor(0xffd5f4ee);
        SeekBar x=new SeekBar(activity);x.setMax(100);x.setProgress(horizontal(activity));
        panel.addView(xLabel);panel.addView(x);
        TextView yLabel=new TextView(activity);yLabel.setTextColor(0xffd5f4ee);
        SeekBar y=new SeekBar(activity);y.setMax(152);y.setProgress(lift(activity)-8);
        panel.addView(yLabel);panel.addView(y);
        Runnable status=()->{
            xLabel.setText("Položaj vodoravno: "+x.getProgress()+"% ekrana");
            yLabel.setText("Visina iznad taskbara: "+(y.getProgress()+8)+" dp");
        };
        SeekBar.OnSeekBarChangeListener listener=new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){status.run();}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        };
        x.setOnSeekBarChangeListener(listener);y.setOnSeekBarChangeListener(listener);
        status.run();
        ScrollView scroll=new ScrollView(activity);scroll.addView(panel);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Uredi 5 desktop tipki")
                .setView(scroll)
                .setNegativeButton("ODUSTANI",null)
                .setNeutralButton("VRATI ZADANO",null)
                .setPositiveButton("SPREMI",null).create();
        dialog.setOnShowListener(ignored->{
            Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v->{
                int[] mapping=new int[SLOT_COUNT];
                for(int i=0;i<SLOT_COUNT;i++)mapping[i]=buttons[i].getSelectedItemPosition();
                if(!save(activity,mapping,x.getProgress(),y.getProgress()+8)){
                    android.widget.Toast.makeText(activity,"Nije uspjelo spremanje",android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                if(changed!=null)changed.run();
                android.widget.Toast.makeText(activity,"Tipke i položaj spremljeni",android.widget.Toast.LENGTH_SHORT).show();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                for(int i=0;i<SLOT_COUNT;i++)buttons[i].setSelection(DEFAULT[i]);
                x.setProgress(50);y.setProgress(0);status.run();
            });
        });
        dialog.show();
    }
}
