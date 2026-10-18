package com.drnas.console;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Export a real .blend from Wine C: to an Android DocumentsProvider chosen
 * by the user. SAF content:// is NOT a mounted Unix/Windows drive and should
 * never be passed directly to Wine as a C: or R: path.
 *
 * Never alters the source project, scans Wine system directories or asks for
 * Android MANAGE_EXTERNAL_STORAGE. No periodic polling in Blender render paths.
 */
final class BlenderAndroidExport {
    private static final int MAX_PROJECTS=160;
    private static final int MAX_VISITS=480;
    static final class Project {
        final File file;
        final String guestRelativePath;
        final long bytes;
        Project(File file,String relative) {
            this.file=file;this.guestRelativePath=relative;bytes=file.length();
        }
        String title() {
            return file.getName()+"  ·  "+guestRelativePath;
        }
    }

    private static final class Scan {
        final Path drive;
        final List<Project> projects=new ArrayList<>();
        final Set<Path> visited=new HashSet<>();
        int visits;
        Scan(Path drive){this.drive=drive;}
        void visit(File dir,int depth) throws IOException {
            if(depth>4 || projects.size()>=MAX_PROJECTS || visits>=MAX_VISITS)
                return;
            Path p=dir.toPath();
            if(Files.isSymbolicLink(p) || !Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))
                return;
            Path real=dir.getCanonicalFile().toPath();
            if(!real.startsWith(drive) || !visited.add(real))return;
            File[] children=dir.listFiles();
            if(children==null)return;
            for(File child:children) {
                if(++visits>MAX_VISITS || projects.size()>=MAX_PROJECTS)return;
                Path path=child.toPath();
                if(Files.isSymbolicLink(path))continue;
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) {
                    visit(child,depth+1);
                } else if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) &&
                        child.getName().toLowerCase(Locale.ROOT).endsWith(".blend") &&
                        child.length()>0) {
                    Path actual=child.getCanonicalFile().toPath();
                    if(actual.startsWith(drive)) {
                        String relative=drive.relativize(actual).toString()
                                .replace(File.separatorChar,'\\');
                        projects.add(new Project(child,"C:\\"+relative));
                    }
                }
            }
        }
    }

    static List<Project> projects(File prefix) throws IOException {
        File driveDir=new File(prefix,"drive_c").getCanonicalFile();
        Path drive=driveDir.toPath();
        if(!Files.isDirectory(drive,LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(drive))throw new IOException("Wine C: nije dostupan");
        Scan scan=new Scan(drive);
        File users=new File(driveDir,"users");
        File[] homes=users.listFiles();
        if(homes!=null)for(File home:homes) {
            if(scan.visits>=MAX_VISITS || scan.projects.size()>=MAX_PROJECTS)break;
            if(Files.isSymbolicLink(home.toPath()) ||
                    !Files.isDirectory(home.toPath(),LinkOption.NOFOLLOW_LINKS))continue;
            for(String root:new String[]{"Documents","Desktop","Downloads","My Documents"}) {
                scan.visit(new File(home,root),0);
            }
        }
        scan.visit(new File(driveDir,"DRNAS/Projects"),0);
        scan.visit(new File(driveDir,"DRNAS/Apps/Blender-4.5.14"),0);
        scan.projects.sort(Comparator.comparingLong(
                (Project p)->p.file.lastModified()).reversed()
                .thenComparing(p->p.guestRelativePath));
        return scan.projects;
    }

    /**
     * Copy using constant RAM even for large .blend scenes. ACTION_CREATE_DOCUMENT
     * returns a new provider-owned destination; a failed export removes only
     * that newly created destination. Existing source files are never touched.
     */
    static long export(Context ctx,File project,Uri destination) throws IOException {
        if(project==null || destination==null ||
                !"content".equals(destination.getScheme()))
            throw new IOException("Android nije odabrao valjanu odredišnu datoteku");
        File drive=new File(WineShortcuts.prefix(ctx),"drive_c").getCanonicalFile();
        Path source=project.getCanonicalFile().toPath();
        if(!source.startsWith(drive.toPath()) ||
                Files.isSymbolicLink(project.toPath()) ||
                !project.isFile() ||
                !project.getName().toLowerCase(Locale.ROOT).endsWith(".blend"))
            throw new IOException("Odabrana Blender datoteka nije u sigurnom Wine C:");
        long originalLength=project.length();
        long originalTime=project.lastModified();
        if(originalLength<=0)throw new IOException("Blender projekt je prazan ili se još sprema");
        boolean ok=false;
        long written=0;
        try {
            try(FileInputStream input=new FileInputStream(project);
                OutputStream output=ctx.getContentResolver().openOutputStream(destination,"w")) {
                if(output==null)throw new IOException("Android mapa nije dopustila zapis");
                byte[] buf=new byte[1024*1024];
                int n;
                while((n=input.read(buf))!=-1) {
                    if(n==0)continue;
                    output.write(buf,0,n);
                    written+=n;
                }
                output.flush();
            }
            if(written!=originalLength || project.length()!=originalLength ||
                    project.lastModified()!=originalTime)
                throw new IOException("Projekt se mijenjao tijekom izvoza; pokušaj ponovno nakon Save");
            ok=true;
            return written;
        } finally {
            if(!ok) {
                // ACTION_CREATE_DOCUMENT creates only this new document;
                // never delete the Wine original on export failure.
                try {ctx.getContentResolver().delete(destination,null,null);}
                catch(Exception ignored) {}
            }
        }
    }
    private BlenderAndroidExport(){}
}
