package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Native pre-Wine file browser. Copy/Paste operate on the private drive files. */
final class HostFileExplorer extends LinearLayout {
    private final Activity activity;
    private final Runnable chooseFolder,added;
    private final SharedPreferences prefs;
    private String drive,path;
    private String selected;
    private boolean busy;
    private int pageSize=200;
    private final LinearLayout list;
    private final TextView location,status;
    private final Button copy,paste;
    HostFileExplorer(Activity activity,Runnable chooseFolder,Runnable added) {
        super(activity);this.activity=activity;this.chooseFolder=chooseFolder;this.added=added;
        prefs=activity.getSharedPreferences("drnas_host_files",Activity.MODE_PRIVATE);
        drive=prefs.getString("drive","r");path=prefs.getString("path","");
        setOrientation(VERTICAL);setPadding(dp(10),dp(8),dp(10),dp(10));
        setBackground(surface());
        row(button("C:  WINE",()->navigate("c","")),button("R:  ODABRANA MAPA",()->navigate("r","")));
        location=text("",12);addView(location);
        row(button("↑  GORE",()->{
            int i=path.lastIndexOf('/');navigate(drive,i<0?"":path.substring(0,i));
        }),button("＋  NOVA MAPA",this::newFolder));
        copy=button("KOPIRAJ",this::copySelected);
        paste=button("ZALIJEPI OVDJE",this::paste);
        row(copy,paste);
        row(button("Odaberi Android mapu",chooseFolder),button("Uvezi / osvježi R:",()->work(()->{
            SharedFolderDrive.importSelected(activity.getApplicationContext(),msg->{});
            return "R: osvježen";
        })));
        status=text("Odaberi datoteku → Kopiraj → otvori odredište → Zalijepi.",11);
        addView(status);
        TextView hint=text("R: je privatna kopija odabrane Android mape. Kopiranje u R: ne mijenja izvornu Android mapu. Dodir na .exe → Dodaj na početni ekran. Dugi dodir na mapu → Kopiraj.",11);
        hint.setPadding(0,dp(5),0,dp(7));addView(hint);
        list=new LinearLayout(activity);list.setOrientation(VERTICAL);addView(list);
        refresh();
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable surface(){
        GradientDrawable b=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xe5193045,0xf00b1b2d});
        b.setCornerRadius(dp(14));b.setStroke(dp(1),0xff3ac9d6);return b;
    }
    private TextView text(String s,int size){
        TextView t=new TextView(activity);t.setText(s);t.setTextColor(0xffb9d6df);t.setTextSize(size);return t;
    }
    private Button button(String s,Runnable action){
        Button b=new Button(activity);b.setText(s);b.setAllCaps(false);b.setTextSize(11);
        b.setTextColor(0xffb1ffe1);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setMinHeight(dp(40));b.setMinimumHeight(dp(40));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(7),dp(3),dp(7),dp(3));b.setBackground(surface());
        b.setOnClickListener(v->{if(!busy)action.run();});
        return b;
    }
    private void row(View a,View b){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(a,new LinearLayout.LayoutParams(0,dp(42),1));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(42),1);rp.leftMargin=dp(5);
        row.addView(b,rp);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(5);addView(row,lp);
    }
    private void navigate(String d,String p){if(busy)return;drive=d;path=p;selected=null;pageSize=200;
        prefs.edit().putString("drive",d).putString("path",p).apply();refresh();}
    private void message(String msg){status.setText(msg);}
    private interface Job {String run() throws Exception;}
    private void work(Job task){
        if(busy)return;busy=true;message("Radim…");copy.setEnabled(false);paste.setEnabled(false);
        new Thread(()->{
            String result;
            try{result=task.run();}catch(Exception e){result="Nije dovršeno: "+e.getMessage()+". Ako je kopirana mapa, dovršene datoteke ostaju u odredištu.";}
            final String value=result;
            activity.runOnUiThread(()->{busy=false;if(activity.isFinishing())return;refresh();message(value);});
        },"drnas-native-file-copy").start();
    }
    private File root() throws IOException {return UserShortcuts.root(activity,drive);}
    private void refresh(){
        if(busy)return;
        location.setText(drive.toUpperCase(Locale.ROOT)+":/"+path);
        copy.setEnabled(selected!=null);
        paste.setEnabled(!prefs.getString("clipboard","").isEmpty());
        list.removeAllViews();final String requestedDrive=drive,requestedPath=path;
        new Thread(()->{
            File[] files=null;String error=null;
            try{
                File root=UserShortcuts.root(activity,requestedDrive);
                if("r".equals(requestedDrive)&&!root.exists()&&!root.mkdirs())throw new IOException("R: nije dostupan");
                File dir=AppFiles.resolve(root,requestedPath);
                if(!dir.isDirectory())throw new IOException("Mapa nije dostupna. Za C: prvo pripremi Windows.");
                files=dir.listFiles();
                if(files==null)throw new IOException("Nije moguće pročitati mapu");
                Arrays.sort(files,Comparator.comparing((File f)->!f.isDirectory())
                        .thenComparing(f->f.getName().toLowerCase(Locale.ROOT)));
            }catch(Exception e){error=e.getMessage();}
            final File[] data=files;final String failure=error;
            activity.runOnUiThread(()->{
                if(activity.isFinishing()||!drive.equals(requestedDrive)||!path.equals(requestedPath)||busy)return;
                list.removeAllViews();
                if(failure!=null){list.addView(text(failure,12));return;}
                int count=0;
                for(File f:data){
                    if(Files.isSymbolicLink(f.toPath()))continue;
                    if(count++>=pageSize)break;
                    String rel;
                    try{rel=AppFiles.child(path,f.getName());}catch(IOException ignored){continue;}
                    final String target=rel;
                    Button entry=button((f.isDirectory()?"▣  ":"·  ")+f.getName(),()->{
                        if(f.isDirectory())navigate(drive,target);
                        else {selected=target;copy.setEnabled(true);message("Odabrano: "+f.getName());fileActions(target);}
                    });
                    entry.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                    entry.setOnLongClickListener(v->{if(!busy){selected=target;copySelected();}return true;});
                    LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.bottomMargin=dp(4);
                    list.addView(entry,ep);
                }
                if(data.length==0)list.addView(text("Prazna mapa",12));
                if(count>pageSize)list.addView(button("Prikaži još 200",()->{pageSize+=200;refresh();}));
            });
        },"drnas-native-file-list").start();
    }
    private void fileActions(String relative){
        boolean exe=relative.toLowerCase(Locale.ROOT).endsWith(".exe");
        String[] options=exe?new String[]{"Dodaj na početni ekran","Kopiraj"}:new String[]{"Kopiraj"};
        new AlertDialog.Builder(activity).setTitle(new File(relative).getName()).setItems(options,(d,w)->{
            if(exe&&w==0)addShortcut(relative);else copySelected();
        }).show();
    }
    private void copySelected(){
        if(selected==null){message("Prvo odaberi datoteku ili dugo pritisni mapu.");return;}
        prefs.edit().putString("clipboard",drive+":"+selected).apply();
        paste.setEnabled(true);message("Kopirano: "+drive.toUpperCase(Locale.ROOT)+":/"+selected+
                " · Otvori odredišnu mapu i pritisni ZALIJEPI OVDJE.");
    }
    private void paste(){
        String token=prefs.getString("clipboard","");
        if(token.length()<3){message("Nema kopirane datoteke.");return;}
        final String sourceDrive=token.substring(0,1),sourcePath=token.substring(2);
        final String targetDrive=drive,targetDir=path;
        work(()->{
            File sr=UserShortcuts.root(activity,sourceDrive),tr=UserShortcuts.root(activity,targetDrive);
            File source=AppFiles.resolve(sr,sourcePath);
            String destination=AppFiles.child(targetDir,source.getName());
            long bytes=AppFiles.copy(sr,sourcePath,tr,destination);
            return "Kopiranje dovršeno: "+source.getName()+" · "+(bytes/1024)+" KiB";
        });
    }
    private void newFolder(){
        EditText input=new EditText(activity);input.setSingleLine(true);input.setHint("Naziv mape");
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Nova mapa").setView(input)
                .setPositiveButton("Stvori",null).setNegativeButton("Odustani",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
            try{
                File target=AppFiles.resolve(root(),AppFiles.child(path,input.getText().toString()));
                if(!target.mkdir())throw new IOException("Mapa postoji ili je nije moguće stvoriti");
                dialog.dismiss();refresh();
            }catch(Exception e){input.setError(e.getMessage());}
        }));dialog.show();
    }
    private void addShortcut(String relative){
        EditText input=new EditText(activity);input.setSingleLine(true);
        String name=new File(relative).getName();input.setText(name.substring(0,name.length()-4));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Dodaj na početni ekran").setView(input)
                .setPositiveButton("Dodaj",null).setNegativeButton("Odustani",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
            try{UserShortcuts.add(activity,input.getText().toString(),drive,relative);
                dialog.dismiss();Toast.makeText(activity,"Prečac je dodan",Toast.LENGTH_SHORT).show();added.run();}
            catch(Exception e){input.setError(e.getMessage());}
        }));dialog.show();
    }
}
