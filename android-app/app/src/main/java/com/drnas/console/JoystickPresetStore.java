package com.drnas.console;

import android.content.Context;
import android.util.Xml;
import android.os.Build;
import android.content.ContentValues;
import android.os.Environment;
import android.provider.MediaStore;
import android.net.Uri;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** XML layout independent of device pixel density. Normalized centers preserve
 * placement across landscape resolutions. Private source of truth is always
 * internal persistent storage; Save also exports a readable copy to Downloads.
 */
final class JoystickPresetStore {
    static final String[] IDS={"L","LB","LT","BACK","L3","UP","LEFT","RIGHT","DOWN",
            "R","START","RB","RT","R3","Y","X","B","A"};
    // x,y normalized to a 16:9 landscape screen. Approximate original controls.
    private static final float[][] DEFAULT={
        {.085f,.77f},{.043f,.48f},{.21f,.48f},{.135f,.575f},{.19f,.575f},
        {.193f,.70f},{.16f,.775f},{.226f,.775f},{.193f,.85f},
        {.77f,.77f},{.785f,.48f},{.89f,.48f},{.972f,.48f},{.718f,.58f},
        {.928f,.675f},{.882f,.78f},{.976f,.78f},{.928f,.86f}
    };
    static final class Layout {
        final float[] xs=new float[IDS.length],ys=new float[IDS.length];
        Layout(){for(int i=0;i<IDS.length;i++){xs[i]=DEFAULT[i][0];ys[i]=DEFAULT[i][1];}}
        Layout copy(){Layout c=new Layout();System.arraycopy(xs,0,c.xs,0,xs.length);
            System.arraycopy(ys,0,c.ys,0,ys.length);return c;}
        void place(int i,float x,float y) {
            xs[i]=Math.max(.025f,Math.min(.975f,x));
            ys[i]=Math.max(.09f,Math.min(.96f,y));
        }
    }
    static File internal(Context context) {
        return new File(new File(context.getFilesDir(),"joystick-presets"),"custom.xml");
    }
    static Layout load(Context context) {
        Layout layout=new Layout();
        File f=internal(context);
        if(!f.isFile()||f.length()>32768)return layout;
        try(FileInputStream in=new FileInputStream(f)) {
            XmlPullParser parser=Xml.newPullParser();
            parser.setInput(in,"UTF-8");
            for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;
                event=parser.next()) {
                if(event!=XmlPullParser.START_TAG||!"control".equals(parser.getName()))continue;
                String id=parser.getAttributeValue(null,"id");
                int index=Arrays.asList(IDS).indexOf(id);
                if(index<0)continue;
                float x=Float.parseFloat(parser.getAttributeValue(null,"x"));
                float y=Float.parseFloat(parser.getAttributeValue(null,"y"));
                if(Float.isFinite(x)&&Float.isFinite(y))layout.place(index,x,y);
            }
        } catch(Exception badXml) {
            // Corrupt/untrusted file: return safe layout, never lose controls.
            return new Layout();
        }
        return layout;
    }
    static void save(Context context,Layout layout) throws IOException {
        File to=internal(context),dir=to.getParentFile();
        if(dir==null||(!dir.isDirectory()&&!dir.mkdirs()))throw new IOException("Cannot make joystick-presets directory");
        File tmp=new File(dir,"custom.xml.tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)) {
            writeXml(out,layout);out.getFD().sync();
        }
        if(!tmp.renameTo(to))throw new IOException("Joystick preset rename failed");
    }
    static void writeXml(OutputStream out,Layout layout) throws IOException {
        try {
            XmlSerializer xml=Xml.newSerializer();
            xml.setOutput(out,"UTF-8");
            xml.startDocument("UTF-8",true);
            xml.startTag(null,"emudrnas-joystick");
            xml.attribute(null,"version","1");
            for(int i=0;i<IDS.length;i++) {
                xml.startTag(null,"control");
                xml.attribute(null,"id",IDS[i]);
                xml.attribute(null,"x",Float.toString(layout.xs[i]));
                xml.attribute(null,"y",Float.toString(layout.ys[i]));
                xml.endTag(null,"control");
            }
            xml.endTag(null,"emudrnas-joystick");xml.endDocument();xml.flush();
        } catch(Exception e){throw new IOException("Joystick XML output failed",e);}
    }
    /** Independent user-owned backup in Downloads/DRNAS/JoystickPresets. */
    static String exportToDownloads(Context context,Layout layout) throws IOException {
        if(Build.VERSION.SDK_INT<29)throw new IOException("Android 10+ needed for Downloads export");
        ContentValues vals=new ContentValues();
        String name="EmuDrnas-joystick-custom.xml";
        vals.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        vals.put(MediaStore.MediaColumns.MIME_TYPE,"application/xml");
        vals.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS+"/DRNAS/JoystickPresets");
        vals.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,vals);
        if(uri==null)throw new IOException("Downloads provider refused joystick XML");
        boolean success=false;
        try {
            try(OutputStream out=context.getContentResolver().openOutputStream(uri,"w")) {
                if(out==null)throw new IOException("Cannot open XML output");
                writeXml(out,layout);
            }
            vals.clear();vals.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(context.getContentResolver().update(uri,vals,null,null)!=1)
                throw new IOException("Failed to publish XML");
            success=true;return "Downloads/DRNAS/JoystickPresets/"+name;
        } finally {if(!success)context.getContentResolver().delete(uri,null,null);}
    }
    private JoystickPresetStore(){}
}
