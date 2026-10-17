package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.IntConsumer;

/** Clear, explicit DXVK frame cap editor before Wine Explorer starts.
 * A single Start button commits selected 0..240; 0 = unlimited DXVK.
 */
final class DrnasDxvkDialog {
    private final Activity context;
    private final boolean beforeStartup;
    private final IntConsumer apply;
    private final Runnable finished;
    private final int selected;
    private AlertDialog dialog;
    private EditText field;

    static void show(Activity context,int value,boolean beforeStartup,
                     IntConsumer apply,Runnable finished) {
        new DrnasDxvkDialog(context,value,beforeStartup,apply,finished).show();
    }
    private DrnasDxvkDialog(Activity context,int value,boolean beforeStartup,
                             IntConsumer apply,Runnable finished) {
        this.context=context;this.selected=value;this.beforeStartup=beforeStartup;
        this.apply=apply;this.finished=finished;
    }
    private int dp(float x){return Math.round(x*context.getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int top,int bottom) {
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{top,bottom});
        g.setCornerRadius(dp(15));g.setStroke(dp(1),0x7d79bed2);
        return g;
    }
    private TextView label(String s,int sp,int color) {
        TextView t=new TextView(context);
        t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        return t;
    }
    private Button preset(String title,int fps) {
        Button b=new Button(context);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        b.setTextColor(0xffdceef5);
        b.setBackground(bg(0xd62a4a60,0xe1193147));
        b.setOnClickListener(v->{
            field.setText(Integer.toString(fps));
            field.setSelection(field.length());
            Toast.makeText(context,fps==0?"Bez FPS limita":fps+" FPS odabrano",
                    Toast.LENGTH_SHORT).show();
        });
        return b;
    }
    private void show() {
        LinearLayout root=new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(18));
        root.setBackground(bg(0xf21d3444,0xfa0a1a2d));

        TextView name=label("DXVK  •  OGRANIČENJE FPS-a",18,0xfff0fafb);
        name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(name);
        TextView hint=label("Odaberi tipku ili upiši broj.  0 = bez ograničenja.\n"+
                "Postavka se primjenjuje prije Wine Explorera.",12,0xffb4c5d2);
        hint.setPadding(0,dp(8),0,dp(10));root.addView(hint);

        GridLayout presets=new GridLayout(context);
        presets.setColumnCount(4);
        int[] opts={0,30,40,45,60,90,120};
        for(int fps:opts) {
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();
            p.width=dp(76);p.height=dp(42);p.setMargins(dp(2),dp(2),dp(2),dp(2));
            presets.addView(preset(fps==0?"∞":Integer.toString(fps),fps),p);
        }
        root.addView(presets);
        TextView number=label("RUČNI UNOS  (0–240 FPS)",11,0xff8ce6c3);
        number.setPadding(0,dp(8),0,dp(5));
        root.addView(number);
        field=new EditText(context);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(Integer.toString(selected));
        field.setTextColor(Color.WHITE);
        field.setSelectAllOnFocus(true);
        field.setBackground(bg(0xd01b334b,0xcb162b43));
        field.setPadding(dp(15),dp(8),dp(15),dp(8));
        root.addView(field,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(45)));

        Button launch=new Button(context);
        launch.setText(beforeStartup?"▶  POKRENI WINDOWS":"✓  SPREMI ZA IDUĆI START");
        launch.setAllCaps(false);
        launch.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        launch.setTextColor(0xff10272c);
        launch.setBackground(bg(0xff80edcf,0xff5dcbb7));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(47));
        lp.topMargin=dp(12);root.addView(launch,lp);
        launch.setOnClickListener(v->{
            int fps;
            try{fps=Integer.parseInt(field.getText().toString().trim());}
            catch(NumberFormatException e){
                field.setError("Upiši broj 0–240");return;
            }
            if(fps<0||fps>240){field.setError("Vrijednost treba biti 0–240");return;}
            apply.accept(fps);
            dialog.dismiss();
        });

        dialog=new AlertDialog.Builder(context).setView(root)
                .setCancelable(true).create();
        dialog.setOnDismissListener(d->finished.run());
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            int available=context.getResources().getDisplayMetrics().widthPixels-dp(30);
            w.setLayout(Math.min(dp(430),available),WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
