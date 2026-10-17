package com.drnas.console;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
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
        File file=AppFiles.resolve(root(c,drive),relative);
        if(!file.isFile()||!relative.toLowerCase(Locale.ROOT).endsWith(".exe"))
            throw new IOException("Odaberi postojeću .exe datoteku");
        title=title.trim();
        if(title.isEmpty()||title.length()>80)throw new IOException("Naziv treba imati 1–80 znakova");
        JSONObject record=new JSONObject().put("title",title).put("drive",drive).put("path",relative)
                .put("created",System.currentTimeMillis());
        if(!prefs(c).edit().putString("user:"+UUID.randomUUID(),record.toString()).commit())
            throw new IOException("Prečac nije spremljen");
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
    static void remove(Context c,String id){if(isId(id))prefs(c).edit().remove(id).apply();}
    private UserShortcuts(){}
}
