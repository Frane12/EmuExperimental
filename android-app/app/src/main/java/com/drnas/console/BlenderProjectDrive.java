package com.drnas.console;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

final class BlenderProjectDrive {
    private static final String PREF="blender-project-drive", TREE="tree";
    static Uri selected(Context ctx) {
        String value=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(TREE,"");
        return value.isEmpty()?null:Uri.parse(value);
    }
    static File folder(Context ctx,Uri tree) throws IOException {
        if(tree==null || !"content".equals(tree.getScheme()) ||
                !"com.android.externalstorage.documents".equals(tree.getAuthority()))
            throw new IOException("Choose a local folder in Internal storage or an SD card. Cloud folders use Android export.");
        try {return BlenderDrivePaths.resolve(DocumentsContract.getTreeDocumentId(tree),
                Environment.getExternalStorageDirectory(),new File("/storage"));}
        catch(IllegalArgumentException e){throw new IOException("Invalid Android folder",e);}
    }
    static boolean hasGrant(Context ctx,Uri tree) {
        if(tree==null)return false;
        for(android.content.UriPermission grant:ctx.getContentResolver().getPersistedUriPermissions())
            if(tree.equals(grant.getUri()) && grant.isReadPermission() && grant.isWritePermission())return true;
        return false;
    }
    static boolean ready(Context ctx) {
        try {Uri tree=selected(ctx);if(!hasGrant(ctx,tree))return false;
            BlenderDrivePaths.verify(folder(ctx,tree));return true;
        }catch(Exception e){return false;}
    }
    static void remember(Context ctx,Uri tree,int flags) throws IOException {
        folder(ctx,tree); // Reject unsupported providers before requesting any permission.
        int rw=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        if((flags&rw)!=rw)throw new IOException("Read and write permission are required for D:");
        ctx.getContentResolver().takePersistableUriPermission(tree,rw);
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(TREE,tree.toString()).commit();
    }
    static File map(Context ctx,File prefix,Consumer<String> log) throws IOException {
        Uri tree=selected(ctx);
        if(!hasGrant(ctx,tree))throw new IOException("Choose the Blender Android folder again");
        File folder=folder(ctx,tree);
        BlenderDrivePaths.verify(folder);
        BlenderDrivePaths.map(prefix,folder);
        log.accept("DRNAS_BLENDER_D_READY D:\\ = "+folder+" (direct read/write; no mirror or export queue)");
        return folder;
    }
    private BlenderProjectDrive(){}
}
