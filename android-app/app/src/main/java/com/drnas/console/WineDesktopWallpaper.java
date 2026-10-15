package com.drnas.console;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Custom DRNAS blue-wave BMP for Wine Explorer's own desktop.
 * No Microsoft wallpaper, logo, binary downloads or Android overlay is used.
 * The BMP lives on the guest C: drive; a one-time HKCU registry import runs
 * before explorer.exe starts so the background is a real Wine desktop setting.
 */
final class WineDesktopWallpaper {
    static final int WIDTH=1280;
    static final int HEIGHT=720;
    private static final int ROW_BYTES=((WIDTH*3+3)/4)*4;
    static final int BMP_BYTES=54+ROW_BYTES*HEIGHT;
    private static final String BMP_RELATIVE="drive_c/windows/Web/Wallpaper/DRNAS-Waves.bmp";
    private static final String REG_RELATIVE="drive_c/windows/Temp/DRNAS-Wallpaper.reg";
    private static final String WIN_REG_PATH="C:\\windows\\Temp\\DRNAS-Wallpaper.reg";
    private static final String MARKER=".drnas-wallpaper-v1";

    static void apply(File prefix, File linker, File wine, File cwd,
                      Map<String,String> env, Consumer<String> log,
                      RawWineTrace raw) throws Exception {
        Path base=prefix.getCanonicalFile().toPath();
        Path image=safeGuestFile(base,BMP_RELATIVE);
        if(!Files.isRegularFile(image,LinkOption.NOFOLLOW_LINKS) ||
                Files.size(image)!=BMP_BYTES) {
            writeBmp(image);
            log.accept("DRNAS Waves wallpaper created on Wine C: ("+WIDTH+"x"+HEIGHT+").");
        }
        Path marker=base.resolve(MARKER);
        if(Files.isSymbolicLink(marker))throw new IOException("Unsafe wallpaper marker");
        // Never reset a user-chosen wallpaper on subsequent desktop launches.
        if(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))return;

        Path reg=safeGuestFile(base,REG_RELATIVE);
        // reg.exe accepts UTF-16LE .reg files with BOM; generate it atomically
        // below instead of relying on a process-global Windows code page.
        byte[] encoded=registryText().getBytes(StandardCharsets.UTF_16LE);
        try(OutputStream out=Files.newOutputStream(reg)) {
            out.write(0xff);out.write(0xfe);out.write(encoded);
        }
        List<String> command=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
        command.set(command.size()-1,"reg.exe");
        command.add("import");
        command.add(WIN_REG_PATH);
        Process process=null;
        try {
            ProcessBuilder pb=new ProcessBuilder(command).directory(cwd).redirectErrorStream(true);
            pb.environment().clear();pb.environment().putAll(env);
            process=pb.start();
            Thread output=WineServerDiagnostics.pipe(process,"[wallpaper] ",log,8,raw);
            boolean exited=process.waitFor(16,TimeUnit.SECONDS);
            if(!exited)process.destroyForcibly();
            output.join(1200);
            if(exited && process.exitValue()==0) {
                Files.write(marker,new byte[]{'1'});
                log.accept("Wine desktop wallpaper registered; Explorer will use DRNAS Waves.");
            } else {
                log.accept("Wallpaper registry import not confirmed; Wine desktop starts unchanged.");
            }
        } finally {
            if(process!=null && process.isAlive())process.destroyForcibly();
            Files.deleteIfExists(reg);
        }
    }

    private static Path safeGuestFile(Path base,String relative) throws IOException {
        Path cursor=base;
        for(Path part:java.nio.file.Paths.get(relative)) {
            cursor=cursor.resolve(part);
            if(Files.isSymbolicLink(cursor))
                throw new IOException("Unsafe Wine wallpaper path: "+relative);
        }
        Files.createDirectories(cursor.getParent());
        if(Files.isSymbolicLink(cursor))throw new IOException("Unsafe wallpaper destination");
        return cursor;
    }

    static String registryText() {
        return "Windows Registry Editor Version 5.00\r\n\r\n"+
            "[HKEY_CURRENT_USER\\Control Panel\\Desktop]\r\n"+
            "\"Wallpaper\"=\"C:\\\\windows\\\\Web\\\\Wallpaper\\\\DRNAS-Waves.bmp\"\r\n"+
            "\"WallpaperStyle\"=\"10\"\r\n"+
            "\"TileWallpaper\"=\"0\"\r\n";
    }

    // Standalone BMP writer: reliably understood by Wine without GDI+ or Mono.
    static void writeBmp(Path dest) throws IOException {
        Path temp=dest.resolveSibling(dest.getFileName()+".tmp");
        try {
            try(OutputStream out=new BufferedOutputStream(Files.newOutputStream(temp),65536)) {
                out.write('B');out.write('M');
                le32(out,BMP_BYTES);
                le16(out,0);le16(out,0);
                le32(out,54);
                le32(out,40);le32(out,WIDTH);le32(out,HEIGHT);
                le16(out,1);le16(out,24);
                le32(out,0);le32(out,ROW_BYTES*HEIGHT);
                le32(out,2835);le32(out,2835);
                le32(out,0);le32(out,0);
                byte[] row=new byte[ROW_BYTES];
                for(int y=HEIGHT-1;y>=0;y--) {
                    double v=y/(double)(HEIGHT-1);
                    for(int x=0;x<WIDTH;x++) {
                        double u=x/(double)(WIDTH-1);
                        // Deep-blue gradient, translucent curved beams, cyan glow.
                        double glow=Math.exp(-((u-.68)*(u-.68)*17+
                                               (v-.48)*(v-.48)*11));
                        double wave=Math.exp(-Math.pow(v-.28-.32*u-
                                .055*Math.sin(u*7.2),2)*67);
                        double wave2=Math.exp(-Math.pow(v-.74+.24*u,2)*100);
                        int r=clamp(8+16*v+18*glow+14*wave);
                        int g=clamp(22+46*(1-v)+102*glow+43*wave+22*wave2);
                        int b=clamp(58+58*(1-v)+113*glow+60*wave+36*wave2);
                        int p=x*3;
                        row[p]=(byte)b;
                        row[p+1]=(byte)g;
                        row[p+2]=(byte)r;
                    }
                    out.write(row);
                }
            }
            Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private static int clamp(double x) {return Math.max(0,Math.min(255,(int)Math.round(x)));}
    private static void le16(OutputStream out,int n) throws IOException {
        out.write(n&255);out.write((n>>>8)&255);
    }
    private static void le32(OutputStream out,int n) throws IOException {
        out.write(n&255);out.write((n>>>8)&255);
        out.write((n>>>16)&255);out.write((n>>>24)&255);
    }
    private WineDesktopWallpaper(){}
}
