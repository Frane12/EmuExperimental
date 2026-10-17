package com.drnas.console;

import android.content.Context;
import android.content.ContentValues;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.net.Uri;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import java.io.*;
import java.util.Locale;

/** Full-screen, resolution-independent XInput button positions (0..1). */
final class XInputLayoutPreset {
    static final String[] NAMES={"L","R","LB","LT","BACK","L3","UP","LEFT","RIGHT","DOWN",
            "START","RB","RT","R3","Y","X","B","A"};
    static final int L=0,R=1,LB=2,LT=3,BACK=4,L3=5,UP=6,LEFT=7,RIGHT=8,DOWN=9,
            START=10,RB=11,RT=12,R3=13,Y=14,X=15,B=16,A=17;
    final float[] x=new float[NAMES.length];
    final float[] y=new float[NAMES.length];
    private static float clamp(float v){return Math.max(.03f,Math.min(.97f,v));}
    void set(int id,float nx,float ny){x[id]=clamp(nx);y[id]=clamp(ny);}

    static XInputLayoutPreset defaults(int width,int height,float density){
        XInputLayoutPreset p=new XInputLayoutPreset();
        float l=3f*density,lw=214f*density,top=height-245f*density;
        float rw=272f*density,rx=width-277f*density;
        float[] lx={.33f,.0f,.83f-.37f,.83f,.52f,.75f,.76f,.62f,.90f,.76f};
        float[] ly={.72f,0f,.17f,.17f,.36f,.36f,.59f,.725f,.725f,.86f};
        p.set(L,(l+lw*.33f)/width,(top+214f*density*.72f)/height);
        float[] lbx={.83f-.37f,.83f,.52f,.75f,.76f,.62f,.90f,.76f};
        float[] lby={.17f,.17f,.36f,.36f,.59f,.725f,.725f,.86f};
        for(int i=0;i<8;i++)p.set(i+LB,(l+lbx[i]*lw)/width,
                (top+lby[i]*214f*density)/height);
        p.set(R,(rx+rw*.25f)/width,(top+214f*density*.72f)/height);
        float[] rbx={.31f,.90f-.37f*214f/272f,.90f,.09f,.76f,.61f,.91f,.76f};
        float[] rby={.17f,.17f,.17f,.37f,.54f,.70f,.70f,.87f};
        for(int i=0;i<8;i++)p.set(i+START,(rx+rbx[i]*rw)/width,
                (top+rby[i]*214f*density)/height);
        return p;
    }
    private static String filename(String name){
        String safe=name==null?"Default":name.replaceAll("[^a-zA-Z0-9_-]","_");
        if(safe.length()>42)safe=safe.substring(0,42);
        if(safe.isEmpty())safe="Default";
        return safe+".xml";
    }
    static File directory(Context c) {
        File f=new File(c.getFilesDir(),"joystick-presets");
        if(!f.isDirectory())f.mkdirs();
        return f;
    }
    void save(Context c,String name) throws IOException {
        File directory=directory(c);
        if(!directory.isDirectory())throw new IOException("Preset directory unavailable");
        File target=new File(directory,filename(name));
        File tmp=new File(directory,filename(name)+".tmp");
        try(OutputStream os=new FileOutputStream(tmp)){
            write(os);
        }
        if(!tmp.renameTo(target)){
            // renameTo may fail for an existing file on some Android filesystems.
            if(!target.delete()||!tmp.renameTo(target))
                throw new IOException("Cannot replace preset XML");
        }
        c.getSharedPreferences("joystick",Context.MODE_PRIVATE)
                .edit().putString("selected",filename(name)).apply();
        // Publish an actual user-visible XML copy for backup/import.
        if(Build.VERSION.SDK_INT>=29){
            ContentValues cv=new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME,filename(name));
            cv.put(MediaStore.MediaColumns.MIME_TYPE,"application/xml");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS+"/DRNAS/JoystickPresets");
            cv.put(MediaStore.MediaColumns.IS_PENDING,1);
            Uri uri=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
            if(uri==null)throw new IOException("Preset saved privately; Downloads unavailable");
            try{
                try(OutputStream os=c.getContentResolver().openOutputStream(uri,"w")){
                    if(os==null)throw new IOException("Preset Downloads output unavailable");
                    write(os);
                }
                cv.clear();cv.put(MediaStore.MediaColumns.IS_PENDING,0);
                c.getContentResolver().update(uri,cv,null,null);
            }catch(IOException fail){
                c.getContentResolver().delete(uri,null,null);
                throw fail;
            }
        }
    }
    private void write(OutputStream out) throws IOException {
        try {
            XmlSerializer xml=Xml.newSerializer();
            xml.setOutput(out,"UTF-8");
            xml.startDocument("UTF-8",true);
            xml.startTag(null,"emudrnasJoystick");
            xml.attribute(null,"version","1");
            for(int i=0;i<NAMES.length;i++){
                xml.startTag(null,"control");
                xml.attribute(null,"id",NAMES[i]);
                xml.attribute(null,"x",String.format(Locale.ROOT,"%.6f",x[i]));
                xml.attribute(null,"y",String.format(Locale.ROOT,"%.6f",y[i]));
                xml.endTag(null,"control");
            }
            xml.endTag(null,"emudrnasJoystick");xml.endDocument();xml.flush();
        }catch(Exception ex){throw new IOException("Cannot write preset XML",ex);}
    }
    static XInputLayoutPreset load(Context c,int width,int height){
        XInputLayoutPreset result=defaults(width,height,
                c.getResources().getDisplayMetrics().density);
        String name=c.getSharedPreferences("joystick",Context.MODE_PRIVATE)
                .getString("selected","Default.xml");
        File target=new File(directory(c),filename(name.replaceAll("\\.xml$","")));
        if(!target.isFile())return result;
        try(InputStream in=new FileInputStream(target)){
            XmlPullParser parser=Xml.newPullParser();
            parser.setInput(in,"UTF-8");
            int event=parser.getEventType();
            while(event!=XmlPullParser.END_DOCUMENT){
                if(event==XmlPullParser.START_TAG && "control".equals(parser.getName())){
                    String id=parser.getAttributeValue(null,"id");
                    for(int i=0;i<NAMES.length;i++){
                        if(!NAMES[i].equals(id))continue;
                        float x=Float.parseFloat(parser.getAttributeValue(null,"x"));
                        float y=Float.parseFloat(parser.getAttributeValue(null,"y"));
                        if(Float.isFinite(x)&&Float.isFinite(y))result.set(i,x,y);
                        break;
                    }
                }
                event=parser.next();
            }
        }catch(Exception invalid){ android.util.Log.w("EmuDrnas","Preset load failed: "+invalid);}
        return result;
    }
}
