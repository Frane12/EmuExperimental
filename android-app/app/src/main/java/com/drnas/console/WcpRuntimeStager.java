package com.drnas.console;

import com.github.luben.zstd.ZstdInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tukaani.xz.XZInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Content-addressed unpacker for real Winlator WCP tar.zst/tar.xz packages.
 * All input is untrusted. Nothing extracted here is executed or globally installed.
 * Windows DLLs must only be applied to a separate per-container prefix.
 */
public final class WcpRuntimeStager {
    public static final class PackageInfo {
        public final String kind;
        public final String version;
        public final File folder;
        public final JSONObject profile;
        PackageInfo(String kind, String version, File folder, JSONObject profile) {
            this.kind=kind; this.version=version; this.folder=folder; this.profile=profile;
        }
    }
    private static final int MAX_ENTRIES=30000;
    private static final long MAX_EXPANDED=6L*1024*1024*1024;
    private static final long MAX_FILE=3L*1024*1024*1024;
    private static final int MAX_PROFILE=256*1024;

    static String clean(String raw, boolean directory) throws IOException {
        if (raw==null || raw.isEmpty() || raw.startsWith("/") || raw.contains("\\")
                || raw.indexOf(0)>=0) throw new IOException("Unsafe archive path");
        while(raw.startsWith("./")) raw=raw.substring(2);
        while(raw.endsWith("/")) raw=raw.substring(0,raw.length()-1);
        if(raw.isEmpty() || raw.equals(".")) {
            if(directory) return "";
            throw new IOException("Empty file entry");
        }
        for(String part:raw.split("/",-1))
            if(part.isEmpty() || part.equals("..") || part.equals("."))
                throw new IOException("Traversal/empty path: "+raw);
        return raw;
    }

    private static String safeVersion(String str) throws IOException {
        if(str==null || !str.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,80}"))
            throw new IOException("Invalid component versionName");
        return str;
    }
    private static InputStream decompress(File source) throws IOException {
        byte[] magic=new byte[6];
        try(FileInputStream probe=new FileInputStream(source)) {
            if(probe.read(magic)<6) throw new IOException("WCP too small");
        }
        FileInputStream file=new FileInputStream(source);
        try {
            if((magic[0]&255)==0x28 && (magic[1]&255)==0xb5 &&
                    (magic[2]&255)==0x2f && (magic[3]&255)==0xfd)
                return new ZstdInputStream(file);
            if((magic[0]&255)==0xfd && (magic[1]&255)==0x37 &&
                    (magic[2]&255)==0x7a && (magic[3]&255)==0x58 &&
                    (magic[4]&255)==0x5a && magic[5]==0)
                return new XZInputStream(file);
            throw new IOException("Not TAR+ZSTD / TAR+XZ");
        } catch(Throwable ex) {
            file.close();
            if(ex instanceof IOException) throw (IOException)ex;
            throw new IOException("ZSTD decoder unavailable: "+ex.getMessage(),ex);
        }
    }

    private static void remove(File folder) {
        // NEVER traverse directory symlinks during rollback, even if a
        // malicious TAR member created one before a later validation error.
        if(Files.isSymbolicLink(folder.toPath())) {
            folder.delete();return;
        }
        File[] children=folder.listFiles();
        if(children!=null) for(File child:children)remove(child);
        folder.delete();
    }
    private static File outputFor(File root, String normalized) throws IOException {
        File out=new File(root,normalized);
        String base=root.getCanonicalPath()+File.separator;
        if(!out.getCanonicalPath().startsWith(base))
            throw new IOException("Archive escape rejected: "+normalized);
        return out;
    }

    private static long copyExactly(InputStream src,OutputStream dst,long count,long max) throws IOException {
        if(count<0 || count>max) throw new IOException("Archive entry too large");
        byte[] buffer=new byte[65536];
        long rest=count;
        while(rest>0) {
            int n=src.read(buffer,0,(int)Math.min(buffer.length,rest));
            if(n<0)throw new IOException("Truncated TAR entry");
            if(n==0)continue;
            dst.write(buffer,0,n);
            rest-=n;
        }
        return count;
    }
    private static boolean hasFile(Set<String> names, String path) {return names.contains(path);}
    private static void validate(JSONObject profile,Set<String> names) throws Exception {
        String type=profile.optString("type");
        safeVersion(profile.optString("versionName"));
        if(type.equals("Proton") || type.equals("Wine")) {
            JSONObject section=profile.optJSONObject(type.toLowerCase(Locale.ROOT));
            if(section==null)section=profile.optJSONObject(type.equals("Proton")?"wine":"proton");
            if(section==null)throw new IOException("Proton/Wine paths missing in profile.json");
            for(String key:new String[]{"binPath","libPath","prefixPack"}) {
                String p=clean(section.optString(key),false);
                if(key.equals("prefixPack")) {
                    if(!hasFile(names,p))throw new IOException("Missing prefixPack "+p);
                } else {
                    boolean found=false;
                    for(String n:names) if(n.startsWith(p+"/")) {found=true;break;}
                    if(!found)throw new IOException("Missing "+key+" directory contents: "+p);
                }
            }
        } else if(type.equals("DXVK")) {
            JSONArray mappings=profile.optJSONArray("files");
            if(mappings==null || mappings.length()==0)throw new IOException("DXVK DLL mappings missing");
            for(int i=0;i<mappings.length();i++) {
                JSONObject map=mappings.getJSONObject(i);
                String src=clean(map.getString("source"),false);
                String target=map.getString("target");
                boolean valid=(target.matches("\\$\\{system32\\}/[A-Za-z0-9_]+\\.dll")
                        || target.matches("\\$\\{syswow64\\}/[A-Za-z0-9_]+\\.dll"));
                if(!valid || !names.contains(src) || !src.substring(src.lastIndexOf('/')+1)
                        .equals(target.substring(target.lastIndexOf('/')+1)))
                    throw new IOException("Untrusted DXVK mapping "+target);
            }
        } else throw new IOException("Only Proton, Wine or DXVK supported by TAR stager; got "+type);
    }

    public static PackageInfo stageWcp(File archive,File vault,String sha,Consumer<String> report)
            throws Exception {
        File base=new File(vault,"runtime-packages");
        if(!base.isDirectory() && !base.mkdirs())throw new IOException("Cannot create component cache");
        File result=new File(base,sha);
        File record=new File(result,"drnas-staged.json");
        if(record.isFile()) {
            JSONObject obj=readJson(record);
            return new PackageInfo(obj.getString("kind"),obj.getString("version"),
                    result,obj.getJSONObject("profile"));
        }
        File temp=new File(base,".stage-"+sha+"-"+System.nanoTime());
        if(!temp.mkdir())throw new IOException("Cannot allocate component staging directory");
        boolean success=false;
        try {
            JSONObject profile=null;
            Set<String> found=new HashSet<>();
            long sum=0; int count=0;
            report.accept("Reading "+archive.getName()+" ("+(archive.length()/1024/1024)+" MiB compressed)");
            try(InputStream decoded=decompress(archive);
                TarArchiveInputStream tar=new TarArchiveInputStream(decoded)) {
                TarArchiveEntry entry;
                while((entry=tar.getNextTarEntry())!=null) {
                    if(++count>MAX_ENTRIES)throw new IOException("Too many TAR members");
                    String name=clean(entry.getName(),entry.isDirectory());
                    if(name.isEmpty() && entry.isDirectory())continue;
                    if(!found.add(name))throw new IOException("Duplicate TAR name: "+name);
                    if(!(entry.isFile()||entry.isDirectory()))
                        throw new IOException("Links/special members forbidden: "+name);
                    if(entry.getSize()<0 || entry.getSize()>MAX_FILE ||
                            (sum+=entry.getSize())>MAX_EXPANDED)
                        throw new IOException("Unpacked data exceeds safety limit");
                    File out=outputFor(temp,name);
                    if(entry.isDirectory()) {
                        if(!out.isDirectory() && !out.mkdirs())throw new IOException("mkdir "+name);
                        continue;
                    }
                    if(!out.getParentFile().isDirectory() && !out.getParentFile().mkdirs())
                        throw new IOException("mkdir parent "+name);
                    if("profile.json".equals(name) && entry.getSize()>MAX_PROFILE)
                        throw new IOException("Manifest too large");
                    if(out.exists())throw new IOException("Duplicate file "+name);
                    try(FileOutputStream dst=new FileOutputStream(out)) {
                        copyExactly(tar,dst,entry.getSize(),MAX_FILE);
                    }
                    if(name.equals("profile.json")) profile=readJson(out);
                }
            }
            if(profile==null)throw new IOException("Missing profile.json");
            validate(profile,found);
            String kind=profile.getString("type");
            String version=safeVersion(profile.getString("versionName"));
            JSONObject obj=new JSONObject();
            obj.put("kind",kind).put("version",version).put("profile",profile)
                    .put("archiveSha256",sha).put("expandedBytes",sum)
                    .put("memberCount",count).put("executionVerified",false);
            writeJson(new File(temp,"drnas-staged.json"),obj);
            if(result.exists()) {
                if(!record.isFile())throw new IOException("Incomplete cached staging; remove only after backup");
            } else if(!temp.renameTo(result))throw new IOException("Cannot commit component staging");
            success=true;
            report.accept(kind+" "+version+" staged: "+count+" TAR members");
            return new PackageInfo(kind,version,result,profile);
        } finally {if(!success || temp.exists())remove(temp);}
    }

    public static PackageInfo stageTurnipZip(File archive,File vault,String sha,
                                               Consumer<String> report) throws Exception {
        File base=new File(vault,"runtime-packages");
        if(!base.isDirectory() && !base.mkdirs())throw new IOException("Cannot create Turnip staging");
        File result=new File(base,sha);
        File record=new File(result,"drnas-staged.json");
        if(record.isFile()) {
            JSONObject meta=readJson(record);
            return new PackageInfo("Turnip",meta.optString("version"),result,meta);
        }
        File temp=new File(base,".turnip-"+sha+"-"+System.nanoTime());
        if(!temp.mkdir())throw new IOException("Cannot allocate Turnip staging");
        boolean ok=false;
        try {
            Set<String> found=new HashSet<>();
            int count=0;int drivers=0;long sum=0;
            byte[] buffer=new byte[65536];
            try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive))) {
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    if(++count>MAX_ENTRIES)throw new IOException("ZIP has too many members");
                    String name=clean(entry.getName(),entry.isDirectory());
                    if(name.isEmpty() && entry.isDirectory())continue;
                    if(!found.add(name))throw new IOException("Duplicate ZIP entry");
                    // Driver archives often include metadata. Only stage Vulkan shared libraries,
                    // not arbitrary executables or scripts.
                    if(entry.isDirectory() || !name.toLowerCase(Locale.ROOT).endsWith(".so")) {
                        zip.closeEntry();continue;
                    }
                    File out=outputFor(temp,name);
                    if(!out.getParentFile().isDirectory() && !out.getParentFile().mkdirs())
                        throw new IOException("mkdir Turnip parent");
                    try(OutputStream dst=new FileOutputStream(out)) {
                        int n;
                        long single=0;
                        while((n=zip.read(buffer))!=-1) {
                            if(n==0)continue;
                            single+=n;sum+=n;
                            if(single>MAX_FILE || sum>MAX_EXPANDED)
                                throw new IOException("ZIP expansion limit");
                            dst.write(buffer,0,n);
                        }
                    }
                    if(name.contains("vulkan") || name.contains("freedreno"))drivers++;
                    zip.closeEntry();
                }
            }
            if(drivers==0)throw new IOException("ZIP lacks a Vulkan/Turnip .so driver");
            JSONObject obj=new JSONObject();
            obj.put("kind","Turnip").put("version",archive.getName())
                    .put("driverLibraries",drivers).put("archiveSha256",sha)
                    .put("executionVerified",false);
            writeJson(new File(temp,"drnas-staged.json"),obj);
            if(result.exists()) {
                if(!record.isFile())throw new IOException("Turnip cache incomplete");
            } else if(!temp.renameTo(result))throw new IOException("Cannot commit Turnip staging");
            ok=true;
            report.accept("Turnip ZIP staged with "+drivers+" candidate library file(s)");
            return new PackageInfo("Turnip",archive.getName(),result,obj);
        } finally {if(!ok || temp.exists())remove(temp);}
    }

    /** A dosdevices drive letter often points to the original Winlator host
     * storage (/storage, /mnt, / or /sdcard). Never follow or recreate those
     * external links in our private prefix. It is not a real Android mount.
     * Other unsafe symlinks remain hard errors.
     *
     * Returns false when a DOS drive mapping must be deferred.
     */
    static boolean acceptPrefixSymlink(String name,String target) throws IOException {
        if(target==null || target.isEmpty() || target.indexOf(0)>=0
                || target.contains("\\"))
            throw new IOException("Invalid prefix symlink target: "+name);
        boolean dosdevices=name.matches("(?:^|.*/)dosdevices/[A-Za-z]:{1,2}");
        if(target.startsWith("/")) {
            if(dosdevices) return false;
            throw new IOException("Absolute non-DOS prefix symlink: "+name);
        }
        final Path resolved;
        try {
            Path parent=Paths.get(name).getParent();
            if(parent==null) parent=Paths.get("");
            resolved=parent.resolve(target).normalize();
        } catch(RuntimeException e) {
            throw new IOException("Malformed prefix symlink target: "+name,e);
        }
        if(resolved.isAbsolute() || resolved.startsWith("..") || resolved.toString().isEmpty()) {
            if(dosdevices) return false;
            throw new IOException("Escaping prefix symlink: "+name);
        }
        return true;
    }

    // The activity may start multiple bootstrap instances. Serialize both within the
    // app process and across Android processes; the marker must be checked UNDER lock.
    public static synchronized void unpackPrefixPack(File prefixPack,File prefix,
                                                       Consumer<String> report)
            throws Exception {
        File parent=prefix.getParentFile();
        if(!parent.isDirectory() && !parent.mkdirs())throw new IOException("No prefix parent");
        File lockPath=new File(parent,".drnas-prefix-init.lock");
        if(Files.isSymbolicLink(lockPath.toPath()))
            throw new IOException("Unsafe prefix lock path");
        try(RandomAccessFile lockHandle=new RandomAccessFile(lockPath,"rw");
            FileChannel lockChannel=lockHandle.getChannel();
            FileLock installLock=lockChannel.lock()) {
            if(Files.isSymbolicLink(prefix.toPath()))
                throw new IOException("Unsafe prefix symlink");
            File marker=new File(prefix,"drnas-prefix.ready");
            if(marker.isFile()) {
                report.accept("PREFIX_READY: existing staged Wine prefix reused");
                return;
            }
            if(prefix.exists()) {
                if(!prefix.isDirectory())
                    throw new IOException("Existing prefix is not a directory");
                report.accept("PREFIX_RECOVERY: incomplete prefix detected; preserving it before replacement");
            }
            File temp=new File(parent,".prefix-"+System.nanoTime());
            if(!temp.mkdir())throw new IOException("Cannot create prefix");
        boolean ok=false;
        int regular=0, symlinks=0, deferredDrives=0, hardlinks=0;long sum=0;
        List<String[]> pendingSymlinks=new ArrayList<>();
        List<String[]> pendingHardlinks=new ArrayList<>();
        try {
            try(InputStream decoded=new XZInputStream(new FileInputStream(prefixPack));
                TarArchiveInputStream tar=new TarArchiveInputStream(decoded)) {
                TarArchiveEntry entry;
                Set<String> names=new HashSet<>();
                while((entry=tar.getNextTarEntry())!=null) {
                    String name=clean(entry.getName(),entry.isDirectory());
                    if(name.isEmpty()&&entry.isDirectory())continue;
                    if(!names.add(name))throw new IOException("Duplicate prefix name");
                    if(names.size()>MAX_ENTRIES)throw new IOException("Prefix entry limit");
                    File out=outputFor(temp,name);
                    if(entry.isDirectory()) {
                        if(!out.isDirectory()&&!out.mkdirs())throw new IOException("Prefix directory");
                    } else if(entry.isSymbolicLink()) {
                        String target=entry.getLinkName();
                        if(!acceptPrefixSymlink(name,target)) {
                            deferredDrives++;
                            report.accept("Deferred external Windows drive mapping: "+name+
                                    " (no access to host storage)");
                            continue;
                        }
                        // Do not materialize links while extracting regular files,
                        // otherwise a later TAR entry could follow a symlink.
                        pendingSymlinks.add(new String[]{name,target});
                    } else if(entry.isLink()) {
                        // TAR hardlink names are relative to archive root.
                        String target=clean(entry.getLinkName(),false);
                        pendingHardlinks.add(new String[]{name,target});
                    } else if(entry.isFile()) {
                        sum+=entry.getSize();
                        if(sum>MAX_EXPANDED)throw new IOException("Prefix expansion too large");
                        if(!out.getParentFile().isDirectory()&&!out.getParentFile().mkdirs())
                            throw new IOException("Prefix file parent");
                        try(OutputStream dst=new FileOutputStream(out)) {
                            copyExactly(tar,dst,entry.getSize(),MAX_FILE);
                        }
                        regular++;
                    } else throw new IOException("Untrusted prefix special entry: "+name);
                }
            }
            // Hardlinks first, symlinks last. This prevents archive member
            // extraction from following attacker-controlled symbolic links.
            for(String[] pair:pendingHardlinks) {
                File link=outputFor(temp,pair[0]);
                File target=outputFor(temp,pair[1]);
                if(!Files.isRegularFile(target.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Hardlink target is not a regular file: "+pair[0]);
                if(Files.exists(link.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Duplicate hardlink path: "+pair[0]);
                if(!link.getParentFile().isDirectory()&&!link.getParentFile().mkdirs())
                    throw new IOException("Prefix hardlink parent");
                Files.createLink(link.toPath(),target.toPath());
                hardlinks++;
            }
            for(String[] pair:pendingSymlinks) {
                File link=outputFor(temp,pair[0]);
                if(Files.exists(link.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Duplicate symlink path: "+pair[0]);
                if(!link.getParentFile().isDirectory()&&!link.getParentFile().mkdirs())
                    throw new IOException("Prefix symlink parent");
                Files.createSymbolicLink(link.toPath(),Paths.get(pair[1]));
                symlinks++;
            }
            try(FileOutputStream output=new FileOutputStream(new File(temp,"drnas-prefix.ready"))) {
                output.write(("files="+regular+" symlinks="+symlinks+
                        " hardlinks="+hardlinks+" deferredDrives="+deferredDrives)
                        .getBytes(StandardCharsets.UTF_8));
            }
            // Never delete an incomplete prefix: it may contain user data. Rename
            // it into a same-filesystem backup only AFTER the new image is ready.
            File backup=null;
            if(prefix.exists()) {
                if(Files.isSymbolicLink(prefix.toPath()) || !prefix.isDirectory())
                    throw new IOException("Unsafe existing prefix at commit");
                backup=new File(parent,".prefix-incomplete-backup-"+System.currentTimeMillis()+
                        "-"+System.nanoTime());
                if(backup.exists() || !prefix.renameTo(backup))
                    throw new IOException("Could not preserve incomplete prefix; nothing overwritten");
                report.accept("PREFIX_RECOVERY_BACKUP: "+backup.getAbsolutePath());
            }
            if(!temp.renameTo(prefix)) {
                boolean restored=backup!=null && !prefix.exists() && backup.renameTo(prefix);
                throw new IOException("Could not commit prefix"+
                        (restored?"; original restored":
                         backup!=null?"; original preserved in "+backup.getAbsolutePath():""));
            }
            ok=true;
            report.accept("Wine prefix staged ("+regular+" files, "+symlinks+
                    " internal symlinks, "+hardlinks+" hardlinks, "+deferredDrives+
                    " external drive mappings deferred)");
        } finally {if(!ok)remove(temp);}
        }
    }

    public static JSONObject readJson(File file) throws Exception {
        if(file.length()>512*1024)throw new IOException("JSON metadata too large");
        byte[] data=Files.readAllBytes(file.toPath());
        return new JSONObject(new String(data,StandardCharsets.UTF_8));
    }
    public static void writeJson(File out,JSONObject obj) throws Exception {
        try(FileOutputStream stream=new FileOutputStream(out)) {
            stream.write(obj.toString(2).getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
    }
    private WcpRuntimeStager() {}
}
