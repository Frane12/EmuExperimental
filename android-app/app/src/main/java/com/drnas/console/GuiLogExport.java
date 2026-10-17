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
        return create(context,writer).name;
    }
    static String saveRenderSample(Context context,Writer writer) throws IOException {
        return create(context,writer,"EmuDrnas-Render-5s").name;
    }
    static android.content.Intent share(Context context,Writer writer) throws IOException {
        Saved saved=create(context,writer);
        android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_STREAM,saved.uri);
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT,saved.name);
        intent.setClipData(android.content.ClipData.newRawUri(saved.name,saved.uri));
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }
    private static final class Saved {
        final String name;final Uri uri;
        Saved(String name,Uri uri){this.name=name;this.uri=uri;}
    }
    private static Saved create(Context context,Writer writer) throws IOException {
        return create(context,writer,"DRNAS-Wine-GUI");
    }
    private static Saved create(Context context,Writer writer,String prefix) throws IOException {
        if(Build.VERSION.SDK_INT<29)throw new IOException("Downloads export requires Android 10; use Save as");
        String name=prefix+"-"+new java.text.SimpleDateFormat(
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
            published=true;return new Saved(name,created);
        } finally {
            if(!published)context.getContentResolver().delete(created,null,null);
        }
    }
    private GuiLogExport(){}
}

