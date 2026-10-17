package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import java.io.File;

/** Explicit shortcut name + optional ICO picker; imported icon is copied into app storage. */
final class ShortcutEditor {
    private final Activity activity;
    private final String drive,path;
    private final Runnable pickIcon,done;
    private AlertDialog dialog;
    private EditText title;
    private TextView status;
    private LinearLayout preview;
    private Button choose,reset;
    private Bitmap icon;
    private boolean busy;
    private int generation;
    ShortcutEditor(Activity activity,String drive,String path,Runnable pickIcon,Runnable done){
        this.activity=activity;this.drive=drive;this.path=path;this.pickIcon=pickIcon;this.done=done;
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private Button button(String text){
        Button b=new Button(activity);b.setText(text);b.setAllCaps(false);b.setTextSize(12);
        b.setTextColor(0xff8ef0d0);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff173449);
        bg.setCornerRadius(dp(10));bg.setStroke(dp(1),0xff3bc9cc);b.setBackground(bg);
        b.setMinHeight(dp(42));return b;
    }
    void show(){
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(10),dp(16),dp(10));
        title=new EditText(activity);title.setSingleLine(true);title.setTextColor(0xffeffff8);
        title.setHint("Naziv prečaca");String filename=new File(path).getName();
        title.setText(filename.substring(0,filename.length()-4));root.addView(title);
        preview=new LinearLayout(activity);preview.setGravity(android.view.Gravity.CENTER);
        root.addView(preview,new LinearLayout.LayoutParams(-1,dp(70)));
        updatePreview();
        status=new TextView(activity);status.setTextColor(0xffa9cbd8);status.setTextSize(12);
        status.setText("Neobavezna .ico ikona");root.addView(status);
        LinearLayout actions=new LinearLayout(activity);
        choose=button("Odaberi .ico");choose.setOnClickListener(v->{if(!busy)pickIcon.run();});
        reset=button("Zadana ikona");reset.setOnClickListener(v->{if(!busy){generation++;icon=null;
            updatePreview();status.setText("Zadana retro ikona");}});
        actions.addView(choose,new LinearLayout.LayoutParams(0,dp(44),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.leftMargin=dp(6);
        actions.addView(reset,p);root.addView(actions);
        dialog=new AppDialogBuilder(activity).setTitle("Dodaj na početni ekran").setView(root)
                .setPositiveButton("Dodaj",null).setNegativeButton("Odustani",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->save()));
        dialog.setOnDismissListener(v->generation++);
        dialog.show();
    }
    private void updatePreview(){
        preview.removeAllViews();
        View view;
        if(icon==null)view=new RetroIconView(activity,"user:preview");
        else {ImageView image=new ImageView(activity);image.setImageBitmap(icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);view=image;}
        preview.addView(view,new LinearLayout.LayoutParams(dp(56),dp(56)));
    }
    private void setBusy(boolean value){
        busy=value;choose.setEnabled(!value);reset.setEnabled(!value);
        dialog.getButton(-1).setEnabled(!value);title.setEnabled(!value);
        dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);
    }
    void importIcon(Uri uri){
        if(dialog==null||!dialog.isShowing()||busy)return;
        setBusy(true);status.setText("Učitavam ICO…");final int request=++generation;
        new Thread(()->{
            Bitmap bitmap=null;String failure=null;
            try{bitmap=IcoBitmap.read(activity.getContentResolver().openInputStream(uri));}
            catch(Exception e){failure=e.getMessage();}
            final Bitmap loaded=bitmap;final String error=failure;
            activity.runOnUiThread(()->{
                if(activity.isFinishing()||!dialog.isShowing()||request!=generation)return;
                setBusy(false);
                if(error!=null){status.setText("Ikona nije učitana: "+error);return;}
                icon=loaded;updatePreview();status.setText("ICO odabran · "+icon.getWidth()+" × "+icon.getHeight());
            });
        },"drnas-ico-import").start();
    }
    private void save(){
        if(busy)return;
        final String name=title.getText().toString().trim();
        if(name.isEmpty()||name.length()>80){title.setError("Naziv treba imati 1–80 znakova");return;}
        setBusy(true);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);
        status.setText("Spremam prečac…");final Bitmap selected=icon;
        new Thread(()->{
            String failure=null;
            try{UserShortcuts.add(activity.getApplicationContext(),name,drive,path,selected);}
            catch(Exception e){failure=e.getMessage();}
            final String error=failure;
            activity.runOnUiThread(()->{
                if(activity.isFinishing()||!dialog.isShowing())return;
                setBusy(false);
                if(error!=null){status.setText("Nije spremljeno: "+error);return;}
                dialog.dismiss();AppLanguage.toast(activity,"Prečac je dodan",Toast.LENGTH_SHORT).show();done.run();
            });
        },"drnas-shortcut-save").start();
    }
}
