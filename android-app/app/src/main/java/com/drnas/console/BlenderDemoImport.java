package com.drnas.console;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/**
 * Explicit Blender .blend import into the user's already-selected Android D:
 * (never into hidden Wine C:). Supports any Android DocumentsProvider as input,
 * and downloads one small official CC0 Blender demo on explicit user request.
 * Does not overwrite, rename, or modify existing user data.
 */
final class BlenderDemoImport {
    static final String DEMO_URL=
            "https://download.blender.org/demo/geometry-nodes/fields/pebble_scattering.blend";
    static final String DEMO_NAME="Blender-Pebble-Scattering.blend";
    static final long MAX_BYTES=128L*1024*1024;
    static final long MAX_DEMO_BYTES=8L*1024*1024;

    static String cleanName(String proposed) throws IOException {
        if(proposed==null || proposed.length()>240 ||
                !proposed.toLowerCase(Locale.ROOT).endsWith(".blend"))
            throw new IOException("Odaberi .blend datoteku, ne ZIP ili backup .blend1");
        String stem=proposed.substring(0,proposed.length()-6);
        stem=stem.replaceAll("[^a-zA-Z0-9._ -]","_").trim();
        while(stem.startsWith("."))stem=stem.substring(1);
        if(stem.isEmpty())throw new IOException("Neispravno ime .blend projekta");
        if(stem.length()>112)stem=stem.substring(0,112);
        return stem+".blend";
    }

    static boolean isBlendMagic(byte[] bytes) {
        return bytes!=null && bytes.length>=7 &&
                bytes[0]=='B' && bytes[1]=='L' && bytes[2]=='E' &&
                bytes[3]=='N' && bytes[4]=='D' && bytes[5]=='E' && bytes[6]=='R';
    }

    private static Path selectedD(Context context) throws IOException {
        if(!BlenderProjectDrive.ready(context))
            throw new IOException("Disk D: nije dostupan. Otvori postavke Android mape i ponovno odaberi mapu.");
        File folder=BlenderProjectDrive.folder(context,BlenderProjectDrive.selected(context));
        Path result=folder.getCanonicalFile().toPath();
        if(Files.isSymbolicLink(result) ||
                !Files.isDirectory(result,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Odabrana mapa D: nije sigurna");
        return result;
    }

    private static File copyIntoD(Context context,String name,InputStream input,
                                  long maxBytes) throws IOException {
        Path folder=selectedD(context);
        String clean=cleanName(name);
        String stem=clean.substring(0,clean.length()-6);
        Path temp=Files.createTempFile(folder,".drnas-blend-import-",".partial");
        try {
            byte[] buffer=new byte[64*1024];
            long written=0;
            try(InputStream source=input;
                OutputStream output=Files.newOutputStream(temp,StandardOpenOption.TRUNCATE_EXISTING)) {
                if(source==null)throw new IOException("Android izvor datoteke nije dostupan");
                int n;
                while((n=source.read(buffer))!=-1) {
                    if(n==0)continue;
                    written+=n;
                    if(written>maxBytes)throw new IOException("Datoteka je prevelika za ovaj test");
                    output.write(buffer,0,n);
                }
                output.flush();
            }
            if(written<12)throw new IOException("Prekratka ili prazna Blender datoteka");
            try(InputStream in=Files.newInputStream(temp)) {
                byte[] magic=new byte[7];
                if(in.read(magic)!=7 || !isBlendMagic(magic))
                    throw new IOException("Odabrana datoteka nije valjani .blend (BLENDER header)");
            }
            for(int i=0;i<1000;i++) {
                String candidate=i==0?clean:stem+"-import-"+i+".blend";
                Path target=folder.resolve(candidate);
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))continue;
                // Move within the same local D: filesystem. No REPLACE_EXISTING;
                // original Android document stays untouched.
                Files.move(temp,target);
                return target.toFile();
            }
            throw new IOException("Ne mogu naći slobodno ime projekta u D:");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static File fromAndroidPicker(Context context,Uri source) throws IOException {
        if(source==null || !"content".equals(source.getScheme()))
            throw new IOException("Izaberi Android .blend pomoću dijaloga");
        String name;
        try(Cursor cursor=context.getContentResolver().query(
                source,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor==null || !cursor.moveToFirst())
                throw new IOException("Android nije vratio ime datoteke");
            name=cursor.getString(0);
        }
        cleanName(name); // Reject unsupported formats before creating files.
        return copyIntoD(context,name,context.getContentResolver().openInputStream(source),MAX_BYTES);
    }

    static File downloadDemo(Context context) throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(DEMO_URL).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent","DRNAS-Blender-Demo/1.0");
        try {
            int code=connection.getResponseCode();
            if(code!=HttpURLConnection.HTTP_OK)
                throw new IOException("Blender demo HTTPS: HTTP "+code);
            if(connection.getContentLengthLong()>MAX_DEMO_BYTES)
                throw new IOException("Službeni .blend je neočekivano velik");
            return copyIntoD(context,DEMO_NAME,connection.getInputStream(),MAX_DEMO_BYTES);
        } finally {
            connection.disconnect();
        }
    }

    private BlenderDemoImport(){}
}
