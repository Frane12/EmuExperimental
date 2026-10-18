package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Local external-storage document IDs only. A content URI is never a mount. */
final class BlenderDrivePaths {
    static File resolve(String id, File primary, File storage) throws IOException {
        if(id==null || id.indexOf(0)>=0 || id.indexOf('\\')>=0)
            throw new IOException("Invalid Android folder");
        int colon=id.indexOf(':');
        if(colon<1)throw new IOException("Select a local device folder");
        String volume=id.substring(0,colon), relative=id.substring(colon+1);
        if(relative.isEmpty() || relative.startsWith("/"))
            throw new IOException("Select a subfolder, such as Documents/Blender");
        for(String part:relative.split("/",-1))
            if(part.isEmpty() || part.equals(".") || part.equals(".."))
                throw new IOException("Invalid folder path");
        File root;
        if(volume.equals("primary"))root=primary.getCanonicalFile();
        else if(volume.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))
            root=new File(storage,volume).getCanonicalFile();
        else throw new IOException("This provider cannot expose a local Wine drive");
        File folder=new File(root,relative).getCanonicalFile();
        if(!folder.toPath().startsWith(root.toPath()) || folder.equals(root))
            throw new IOException("Folder escaped selected storage");
        return folder;
    }

    static void verify(File folder) throws IOException {
        if(!folder.isDirectory())
            throw new IOException("Android folder is unavailable or cannot be read");
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(folder.toPath())) {
            entries.iterator().hasNext(); // Verify listing without allocating a whole inventory.
        }
        // Exercise real filesystem writes and rename, as Blender rotates .blend backups.
        Path probe=Files.createTempFile(folder.toPath(),".drnas-access-",".tmp");
        Path renamed=probe.resolveSibling(probe.getFileName()+".renamed");
        try {
            byte[] data={68,82,78,65,83};
            Files.write(probe,data);
            if(!java.util.Arrays.equals(data,Files.readAllBytes(probe)))
                throw new IOException("Android folder read/write check failed");
            Files.move(probe,renamed);
        } finally {Files.deleteIfExists(probe);Files.deleteIfExists(renamed);}
    }

    static void map(File prefix, File folder) throws IOException {
        Path dos=new File(prefix,"dosdevices").toPath();
        Files.createDirectories(dos);
        Path link=dos.resolve("d:"), owner=dos.resolve(".drnas-blender-d-owner");
        String target=folder.getCanonicalPath();
        if(Files.isSymbolicLink(owner))throw new IOException("Unsafe D: ownership record");
        if(Files.exists(link,LinkOption.NOFOLLOW_LINKS)) {
            if(!Files.isSymbolicLink(link) || !Files.isRegularFile(owner,LinkOption.NOFOLLOW_LINKS)
                    || !Files.readSymbolicLink(link).toString().equals(
                        new String(Files.readAllBytes(owner),StandardCharsets.UTF_8)))
                throw new IOException("D: is already assigned; preserving existing disk");
            if(Files.readSymbolicLink(link).toString().equals(target))return;
        }
        Path stage=Files.createTempFile(dos,".drnas-d-",".link");
        try {
            Files.delete(stage);
            Files.createSymbolicLink(stage,folder.getCanonicalFile().toPath());
            Files.move(stage,link,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            Files.write(owner,target.getBytes(StandardCharsets.UTF_8));
        } finally {Files.deleteIfExists(stage);}
    }
    private BlenderDrivePaths(){}
}
