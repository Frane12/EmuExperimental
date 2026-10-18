package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.widget.*;

/** All controls edit a local draft; cancel/back/outside never save it. */
final class AppSettingsDialog {
    static void show(Activity a,WineShortcuts.Entry entry,Runnable changed){
        AppLaunchSettings.Profile profile=AppLaunchSettings.load(a,entry.id);
        LinearLayout form=new LinearLayout(a);form.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(a,14);form.setPadding(pad,dp(a,6),pad,dp(a,10));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff19384b,0xff0d1b30});
        bg.setCornerRadius(dp(a,12));form.setBackground(bg);
        label(a,form,"NAZIV IKONE");
        EditText name=input(a,form,profile.title(entry.title),entry.title,InputType.TYPE_CLASS_TEXT);
        label(a,form,"DXVK FPS  ·  prazno = globalno, 0 = bez limita");
        EditText fps=input(a,form,profile.fps<0?"":Integer.toString(profile.fps),
                "Globalno (0–240 FPS)",InputType.TYPE_CLASS_NUMBER);
        label(a,form,"GRAFIČKI API · Vulkan bez DXVK pretvorbe");
        Spinner graphics=spinner(a,form,new String[]{
                "Automatski", "DirectX preko DXVK", "Native Vulkan (WineVulkan → Turnip)", "OpenGL (Wine11 EGL → Zink → Turnip)"},
                "opengl".equals(profile.graphicsApi)?3:"vulkan".equals(profile.graphicsApi)?2:"dxvk".equals(profile.graphicsApi)?1:0);
        label(a,form,"ARGUMENTI PREČACA (.exe / .lnk) · npr. --gpu-backend vulkan");
        EditText launchArgs=input(a,form,profile.launchArgs,
                "npr. --gpu-backend vulkan",InputType.TYPE_CLASS_TEXT);
        label(a,form,"DXVK VERZIJA");
        Spinner dxvk=spinner(a,form,new String[]{"Globalno","DXVK 1.10.3 Standard","DXVK 1.10.3 Async"},
                "async".equals(profile.dxvk)?2:"standard".equals(profile.dxvk)?1:0);
        label(a,form,"PRILAGODBA ZASLONA");
        Spinner stretch=spinner(a,form,new String[]{"Globalno","Fit · zadrži omjer","Stretch · cijeli zaslon"},
                profile.stretch<0?0:profile.stretch+1);
        label(a,form,"OSJETLJIVOST KAMERE  ·  0.5×–10×");
        EditText sensitivity=input(a,form,profile.sensitivity<0?"":Float.toString(profile.sensitivity),
                "Globalno",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView hint=new TextView(a);hint.setText("Postavke vrijede od idućeg pokretanja. Native Vulkan koristi WineVulkan; "+
                "Blender.exe automatski dobiva --gpu-backend vulkan. "+
                "I za .lnk prečace argumenti se prosljeđuju kroz Windows ShellLink helper. "+
                "GPU i X11 Present moramo potvrditi logom.");
        hint.setTextColor(0xff9fc4d1);hint.setTextSize(11);form.addView(hint);
        CheckBox remove=new CheckBox(a);
        if(UserShortcuts.isId(entry.id)){
            remove.setText("Ukloni ovaj prečac");remove.setTextColor(0xffffa6ad);form.addView(remove);
        }
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        int available=a.getResources().getDisplayMetrics().heightPixels-dp(a,180);
        LinearLayout wrapper=new LinearLayout(a);wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(scroll,new LinearLayout.LayoutParams(-1,Math.max(dp(a,150),Math.min(dp(a,470),available))));
        AlertDialog dialog=new AppDialogBuilder(a).setTitle(profile.title(entry.title))
                .setView(wrapper).setPositiveButton("Prihvati",null).setNegativeButton("Odbaci",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{
            String title=name.getText().toString().trim();
            if(title.isEmpty()||title.length()>80){name.setError("Naziv treba imati 1–80 znakova");return;}
            int cap=-1;float gain=-1;
            try{
                String text=fps.getText().toString().trim();
                if(!text.isEmpty()){cap=Integer.parseInt(text);if(cap<0||cap>240)throw new NumberFormatException();}
            }catch(NumberFormatException bad){fps.setError("Upiši 0–240 ili ostavi prazno");return;}
            try{
                String text=sensitivity.getText().toString().trim().replace(',','.');
                if(!text.isEmpty()){gain=Float.parseFloat(text);
                    if(!Float.isFinite(gain)||gain<.5f||gain>10)throw new NumberFormatException();}
            }catch(NumberFormatException bad){sensitivity.setError("Upiši 0.5–10 ili ostavi prazno");return;}
            String commandArgs=launchArgs.getText().toString().trim();
            try{LaunchArguments.parse(commandArgs);}
            catch(java.io.IOException invalid){launchArgs.setError(invalid.getMessage());return;}
            try{
                if(remove.isChecked()){
                    UserShortcuts.remove(a,entry.id);AppLaunchSettings.remove(a,entry.id);
                }else{
                    int mode=dxvk.getSelectedItemPosition();
                    AppLaunchSettings.save(a,entry.id,new AppLaunchSettings.Profile(
                            title.equals(entry.title)?"":title,cap,mode==1?"standard":mode==2?"async":"default",
                            stretch.getSelectedItemPosition()-1,gain,
                            graphics.getSelectedItemPosition()==3?"opengl":
                             graphics.getSelectedItemPosition()==2?"vulkan":
                            graphics.getSelectedItemPosition()==1?"dxvk":"auto",commandArgs));
                }
                dialog.dismiss();changed.run();
            }catch(Exception error){AppLanguage.toast(a,"Nije spremljeno: "+error.getMessage(),Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    private static void label(Activity a,LinearLayout root,String text){
        TextView t=new TextView(a);t.setText(text);t.setTextColor(0xff80ebc9);t.setTextSize(10);
        t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);t.setPadding(0,dp(a,7),0,dp(a,2));root.addView(t);
    }
    private static EditText input(Activity a,LinearLayout root,String value,String hint,int type){
        EditText e=new EditText(a);e.setSingleLine(true);e.setTextSize(13);e.setTextColor(0xffebfff7);
        e.setHintTextColor(0xffa2bfc8);e.setInputType(type);e.setHint(hint);e.setText(value);
        root.addView(e,new LinearLayout.LayoutParams(-1,dp(a,40)));return e;
    }
    private static Spinner spinner(Activity a,LinearLayout root,String[] labels,int selection){
        Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,AppLanguage.array(a,labels)){
            @Override public android.view.View getView(int p,android.view.View v,android.view.ViewGroup parent){
                TextView text=(TextView)super.getView(p,v,parent);text.setTextColor(0xffe4fff4);text.setTextSize(12);return text;
            }
        });s.setSelection(selection);root.addView(s,new LinearLayout.LayoutParams(-1,dp(a,40)));return s;
    }
    private AppSettingsDialog(){}
}
