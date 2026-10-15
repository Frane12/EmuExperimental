package com.drnas.console;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * SAF grants are content:// URIs, NOT Unix mount points. Mirror user-selected
 * documents into private app storage and expose that private directory to Wine.
 * Android 11+ forbids a tree grant to the root Download directory; a selected
 * subfolder (e.g. Download/DRNAS) or Documents folder is supported.
 *
 * Import-only mirror: the user can refresh manually, and while the Wine GUI
 * runs it polls the persisted SAF grant so newly created directories appear
 * without selecting the Android folder again. Wine files are never deleted or
 * written back to the Android provider. This is NOT a live filesystem mount.
 */
final class SharedFolderDrive {
    private static final String PREF = "drnas_drive";
    private static final String TREE = "user_tree";
    private static final long MAX_COPY = 4L * 1024 * 1024 * 1024;
    private static final int MAX_FILES = 4096;

    static File mirror(Context ctx) {
        return new File(ctx.getFilesDir(), "windows-drives/shared");
    }
    static Uri selectedTree(Context ctx) {
        String value = ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(TREE,"");
        return value.isEmpty() ? null : Uri.parse(value);
    }
    static void persistTree(Context ctx,Uri tree) {
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(TREE,tree.toString()).apply();
    }

    static void mapWineDrive(Context ctx,File prefix,Consumer<String> report) throws Exception {
        File root=mirror(ctx);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create R: drive storage");
        if (Files.isSymbolicLink(root.toPath())) throw new IOException("Refuse symlinked R: storage");
        File dos = new File(prefix,"dosdevices");
        if (!dos.isDirectory() && !dos.mkdirs()) throw new IOException("Missing Wine dosdevices");
        Path link = new File(dos,"r:").toPath();
        Path intended = root.getCanonicalFile().toPath();
        if(Files.isSymbolicLink(link)) {
            if(!Files.readSymbolicLink(link).equals(intended))
                throw new IOException("Existing R: mapping is not ours");
        } else if(Files.exists(link,LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("R: already assigned by Wine; never overwrite another drive");
        } else {
            Files.createSymbolicLink(link,intended);
        }
        report.accept("Mapped R: to app-private DRNAS Shared (user-selected SAF files are copied here)");
    }

    static synchronized String importSelected(Context ctx,Consumer<String> report) throws Exception {
        Uri tree=selectedTree(ctx);
        if(tree==null) throw new IOException("Select a shared Android folder first");
        File root=mirror(ctx);
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot prepare R: drive");
        if(Files.isSymbolicLink(root.toPath()))throw new IOException("Unsafe R: root symlink");
        String id=DocumentsContract.getTreeDocumentId(tree);
        Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,id);
        Budget b=new Budget();
        walk(ctx,tree,doc,root,root,0,b,report);
        String summary="R: imported "+b.files+" file(s), "+(b.bytes/(1024*1024))+
                " MiB; skipped "+b.skipped+" (size/count limits), pre-existing files preserved";
        report.accept(summary);
        return summary;
    }

    private static final class Budget {
        long bytes;
        int files;
        int skipped;
        final Set<String> seen=new HashSet<>();
    }
    private static void walk(Context ctx,Uri tree,Uri parent,File destination,File root,
                             int depth,Budget budget,Consumer<String> report) throws Exception {
        if(depth>8){budget.skipped++;return;}
        String pid=DocumentsContract.getDocumentId(parent);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,pid);
        try(Cursor cursor=ctx.getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE},null,null,null)) {
            if(cursor==null)throw new IOException("Selected Android provider did not return folder contents");
            while(cursor.moveToNext()) {
                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedIOException("R: import interrupted");
                if(budget.files+budget.skipped>=MAX_FILES) {
                    budget.skipped++;return;
                }
                String did=cursor.getString(0), name=cursor.getString(1);
                String mime=cursor.getString(2);
                long declared=cursor.isNull(3)?-1:cursor.getLong(3);
                if(name==null || name.equals(".") || name.equals("..") ||
                        name.contains("/") || name.contains("\\") || name.indexOf(0)>=0 ||
                        name.length()>240 || !budget.seen.add(did)) {
                    budget.skipped++;continue;
                }
                File target=new File(destination,name);
                if(!target.getCanonicalFile().toPath().startsWith(root.getCanonicalFile().toPath()) ||
                        Files.isSymbolicLink(target.toPath())) {
                    budget.skipped++;continue;
                }
                Uri child=DocumentsContract.buildDocumentUriUsingTree(tree,did);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if(!target.isDirectory() && !target.mkdirs()) {
                        budget.skipped++;continue;
                    }
                    walk(ctx,tree,child,target,root,depth+1,budget,report);
                } else if(declared>MAX_COPY-budget.bytes) {
                    budget.skipped++;
                } else {
                    // Do not overwrite changes made locally through Wine.
                    if(target.exists()) {budget.skipped++;continue;}
                    File part=new File(destination,".drnas-import-"+Long.toHexString(System.nanoTime()));
                    try(InputStream in=ctx.getContentResolver().openInputStream(child);
                        FileOutputStream out=new FileOutputStream(part)) {
                        if(in==null)throw new IOException("Provider refused "+name);
                        byte[] buf=new byte[128*1024];
                        int n;long nFile=0;
                        while((n=in.read(buf))!=-1) {
                            if(Thread.currentThread().isInterrupted())
                                throw new InterruptedIOException("R: import interrupted");
                            if(n==0)continue;
                            nFile+=n;
                            if(budget.bytes+nFile>MAX_COPY)
                                throw new IOException("Shared-folder mirror reached 4 GiB limit");
                            out.write(buf,0,n);
                        }
                        out.getFD().sync();
                        if(!part.renameTo(target))throw new IOException("Cannot commit "+name);
                        budget.bytes+=nFile;budget.files++;
                    } finally {part.delete();}
                }
            }
        }
    }
    private SharedFolderDrive(){}
}
