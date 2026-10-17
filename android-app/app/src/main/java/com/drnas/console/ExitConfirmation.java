package com.drnas.console;
import android.app.Activity;
import android.app.AlertDialog;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.widget.Toast;

/** Two distinct Back presses arm a confirmation. Never finishes on first Back. */
final class ExitConfirmation {
    static final class Gate {
        private long first=-1;
        boolean press(long now){
            if(first<0||now-first>2000){first=now;return false;}
            first=-1;return true;
        }
        void reset(){first=-1;}
    }
    private final Gate gate=new Gate();
    private AlertDialog dialog;
    void back(Activity a){
        if(dialog!=null&&dialog.isShowing())return;
        long now=SystemClock.uptimeMillis();
        if(!gate.press(now)){
            AppLanguage.toast(a,"Pritisni još jednom za potvrdu izlaza",Toast.LENGTH_SHORT).show();
            return;
        }
        confirm(a);
    }
    void confirm(Activity a){
        if(dialog!=null&&dialog.isShowing())return;
        try{
            ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,65);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP,160);
            a.getWindow().getDecorView().postDelayed(tone::release,350);
        }catch(RuntimeException ignored){}
        dialog=new AppDialogBuilder(a).setTitle("Izaći iz aplikacije?")
                .setMessage("Zatvoriti ovaj ekran i Windows sesiju?")
                .setPositiveButton("Yes",(d,w)->a.finish())
                .setNegativeButton("Decline",(d,w)->gate.reset())
                .setOnCancelListener(d->gate.reset()).create();
        dialog.show();
    }
}
