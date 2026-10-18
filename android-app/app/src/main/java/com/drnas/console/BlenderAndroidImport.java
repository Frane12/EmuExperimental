package com.drnas.console;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Explicit Android DocumentsProvider -> private Wine C: .blend import.
 * No broad-storage permission, no background filesystem scan, no replacement
 * of user projects. Blender opens the resulting file with File > Open.
 */
final class BlenderAndroidImport {
    static final long MAX_BYTES=128L*1024L*1024L;

    static File importScene(Context context,Uri from) throws IOException {
        if(from==null || !"content".equals(from.getScheme()))
            throw new IOException("Choose a .blend using the Android file picker");
        String title=fileName(context,from);
        if(title==null || !title.toLowerCase(java.util.Locale.ROOT).endsWith(".blend"))
            throw new IOException("Select a Blender .blend project (not a ZIP)");
        String safe=title.replaceAll("[^a-zA-Z0-9._-]","_");
        if(safe.length()>100)safe=safe.substring(0,94)+".blend";
        if(!safe.toLowerCase(java.util.Locale.ROOT).endsWith(".blend"))
            throw new IOException("Invalid .blend filename");
        File prefix=WineShortcuts.prefix(context).getCanonicalFile();
        Path drive=new File(prefix,"drive_c").getCanonicalFile().toPath();
        Path targetDir=drive.resolve("users/xuser/Documents/Blender");
        if(!Files.isDirectory(drive,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Wine C: is not ready; start Blender first");
        Path walk=drive;
        for(String part:new String[]{"users","xuser","Documents","Blender"}) {
            walk=walk.resolve(part);
            if(Files.isSymbolicLink(walk))
                throw new IOException("Unsafe Wine folder symlink");
            Files.createDirectories(walk);
        }
        if(!targetDir.toRealPath().startsWith(drive))
            throw new IOException("Selected Wine folder is outside C:");
        Path dest=targetDir.resolve(safe).normalize();
        if(!dest.startsWith(targetDir))throw new IOException("Unsafe Blender filename");
        String base=safe.substring(0,safe.length()-6);
        for(int i=1;Files.exists(dest,LinkOption.NOFOLLOW_LINKS);i++) {
            if(i>999)throw new IOException("Too many existing files with this name");
            dest=targetDir.resolve(base+"-import-"+i+".blend");
        }
        Path temp=Files.createTempFile(targetDir,".drnas-import-", ".partial");
        boolean done=false;
        try {
            try(InputStream in=context.getContentResolver().openInputStream(from);
                OutputStream out=Files.newOutputStream(temp,StandardOpenOption.TRUNCATE_EXISTING)) {
                if(in==null)throw new IOException("Android provider did not open source");
                byte[] data=new byte[65536];int n;
                long length=0;
                while((n=in.read(data))!=-1) {
                    if(n==0)continue;
                    length+=n;
                    if(length>MAX_BYTES)throw new IOException(".blend exceeds 128 MiB import limit");
                    out.write(data,0,n);
                }
            }
            try(InputStream check=Files.newInputStream(temp)) {
                byte[] head=new byte[7];
                if(check.read(head)!=7)throw new IOException("Imported .blend is empty");
                // Raw .blend files begin with BLENDER; Blender also supports
                // a gzipped .blend (header 1f 8b), so accept both variants.
                String magic=new String(head,java.nio.charset.StandardCharsets.US_ASCII);
                if(!magic.equals("BLENDER") &&
                        !((head[0]&255)==0x1f && (head[1]&255)==0x8b))
                    throw new IOException("The selected file is not a .blend");
            }
            Files.move(temp,dest);
            done=true;
            return dest.toFile();
        } finally {
            if(!done)Files.deleteIfExists(temp);
        }
    }

    private static String fileName(Context ctx,Uri uri) throws IOException {
        try(Cursor cursor=ctx.getContentResolver().query(
                uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor==null || !cursor.moveToFirst())throw new IOException("Missing file name");
            return cursor.getString(0);
        }catch(RuntimeException e) {
            throw new IOException("Cannot read Android file name",e);
        }
    }
    private BlenderAndroidImport(){}
}
