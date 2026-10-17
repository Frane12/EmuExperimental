package com.drnas.console;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.util.*;
import java.util.regex.Pattern;

/** Explicit user shortcuts: persistent IDs, validated C:/R: targets, no shell text. */
final class UserShortcuts {
    private static final Pattern ID=Pattern.compile("user:[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    static boolean isId(String id){return id!=null&&ID.matcher(id).matches();}
    static File root(Context context,String drive) throws IOException {
        if("c".equals(drive))return new File(WineShortcuts.prefix(context),"drive_c");
        if("r".equals(drive))return SharedFolderDrive.mirror(context);
        throw new IOException("Nepoznat disk");
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("drnas_user_shortcuts",Context.MODE_PRIVATE);}
    static void add(Context c,String title,String drive,String relative) throws Exception {
        add(c,title,drive,relative,null);
    }
    static void add(Context c,String title,String drive,String relative,Bitmap icon) throws Exception {
        File file=AppFiles.resolve(root(c,drive),relative);
        if(!file.isFile()||!relative.toLowerCase(Locale.ROOT).endsWith(".exe"))
            throw new IOException("Odaberi postojeću .exe datoteku");
        title=title.trim();
        if(title.isEmpty()||title.length()>80)throw new IOException("Naziv treba imati 1–80 znakova");
        JSONObject record=new JSONObject().put("title",title).put("drive",drive).put("path",relative)
                .put("created",System.currentTimeMillis());
        String id="user:"+UUID.randomUUID();
        File image=null;
        try{
            if(icon!=null){
                image=iconFile(c,id);
                File dir=image.getParentFile();
                if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Mapa ikona nije dostupna");
                try(FileOutputStream out=new FileOutputStream(image)){
                    if(!icon.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Ikona nije spremljena");
                    out.getFD().sync();
                }
            }
            if(!prefs(c).edit().putString(id,record.toString()).commit())
                throw new IOException("Prečac nije spremljen");
            if(icon!=null)IconCache.ICONS.put(id,icon);
        }catch(Exception error){if(image!=null)image.delete();throw error;}
    }
    static List<WineShortcuts.Entry> list(Context c) {
        List<WineShortcuts.Entry> result=new ArrayList<>();
        for(Map.Entry<String,?> item:prefs(c).getAll().entrySet()) {
            if(!isId(item.getKey())||!(item.getValue() instanceof String))continue;
            try {
                JSONObject r=new JSONObject((String)item.getValue());
                result.add(new WineShortcuts.Entry(item.getKey(),r.getString("title"),"↗","Moj prečac"));
            } catch(Exception ignored){}
        }
        result.sort(Comparator.comparing(e->e.title.toLowerCase(Locale.ROOT)));
        return result;
    }
    static File resolve(Context c,String id) throws Exception {
        if(!isId(id))throw new IOException("Nepoznat prečac");
        String data=prefs(c).getString(id,null);
        if(data==null)throw new IOException("Prečac više ne postoji");
        JSONObject r=new JSONObject(data);
        File file=AppFiles.resolve(root(c,r.getString("drive")),r.getString("path"));
        if(!file.isFile()||!file.getName().toLowerCase(Locale.ROOT).endsWith(".exe"))
            throw new IOException("Program više nije na spremljenoj putanji");
        return file;
    }
    private static final class IconCache {
        static final LruCache<String,Bitmap> ICONS=new LruCache<String,Bitmap>(4*1024*1024){
            @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
        };
    }
    private static File iconFile(Context c,String id) throws IOException {
        if(!isId(id))throw new IOException("Neispravan prečac");
        return new File(new File(c.getFilesDir(),"shortcut-icons"),id.substring(5)+".png");
    }
    static Bitmap icon(Context c,String id){
        if(!isId(id))return null;
        Bitmap cached=IconCache.ICONS.get(id);if(cached!=null)return cached;
        try{
            File image=iconFile(c,id);
            if(!image.isFile()||image.length()>1024*1024||Files.isSymbolicLink(image.toPath()))return null;
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(image.getAbsolutePath(),bounds);
            if(bounds.outWidth<1||bounds.outWidth>128||bounds.outHeight<1||bounds.outHeight>128)return null;
            Bitmap bitmap=BitmapFactory.decodeFile(image.getAbsolutePath());
            if(bitmap!=null)IconCache.ICONS.put(id,bitmap);
            return bitmap;
        }catch(IOException error){return null;}
    }
    static void remove(Context c,String id){
        if(isId(id)){
            prefs(c).edit().remove(id).apply();IconCache.ICONS.remove(id);
            try{iconFile(c,id).delete();}catch(IOException ignored){}
        }
    }
    private UserShortcuts(){}
}
