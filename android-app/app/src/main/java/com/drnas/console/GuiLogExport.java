package com.drnas.console;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.OutputStream;

/** App-owned Downloads file; scoped storage, no picker or storage permission. */
final class GuiLogExport {
    interface Writer {void copyTo(OutputStream target) throws IOException;}
    static String save(Context context,Writer writer) throws IOException {
        if(Build.VERSION.SDK_INT<29)throw new IOException("Downloads export requires Android 10; use Save as");
        String name="DRNAS-Wine-GUI-"+new java.text.SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss-SSS",java.util.Locale.US).format(new java.util.Date())+".txt";
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/DRNAS");
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri created=context.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(created==null)throw new IOException("Downloads provider did not create a file");
        boolean published=false;
        try {
            try(OutputStream out=context.getContentResolver().openOutputStream(created,"w")) {
                if(out==null)throw new IOException("Downloads output unavailable");
                writer.copyTo(out);
            }
            values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(context.getContentResolver().update(created,values,null,null)!=1)
                throw new IOException("Downloads file was not published");
            published=true;return name;
        } finally {
            if(!published)context.getContentResolver().delete(created,null,null);
        }
    }
    private GuiLogExport(){}
}
