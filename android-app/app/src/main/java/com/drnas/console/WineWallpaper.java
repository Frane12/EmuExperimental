package com.drnas.console;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One-time, non-destructive wallpaper candidate for the Wine "shell" desktop.
 * Uses original generated meadow artwork, with a geometric fallback.
 *
 * Wine's PaintDesktop() may ignore bitmap wallpapers on some Proton builds.
 * Do not report this as visually verified until tested on a real device.
 */
final class WineWallpaper {
    static final int WIDTH=1280, HEIGHT=720;
    private static final String FILE="Drnas-Desktop-Meadow.bmp";

    static void ensure(android.content.Context context,File prefix,File linker,File wine,File cwd,
                       Map<String,String> env,Consumer<String> log,RawWineTrace raw)
            throws Exception {
        Path root=prefix.getCanonicalFile().toPath();
        Path dir=root.resolve("drive_c/users/xuser/Pictures");
        if(Files.isSymbolicLink(dir) || !Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Pictures is missing or symlinked; refusing wallpaper overwrite");
        Path bmp=dir.resolve(FILE);
        Path marker=root.resolve(".drnas-wallpaper-meadow-v1-registered");
        if(Files.isSymbolicLink(bmp) || Files.isSymbolicLink(marker))
            throw new IOException("Wallpaper path is a symlink");
        if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS)) {
            log.accept("DRNAS wallpaper already registered (one-time setup).");
            return;
        }
        if(!Files.exists(bmp,LinkOption.NOFOLLOW_LINKS)){
            try {writeMeadowBitmap(context,bmp);}
            catch(Exception artError){log.accept("Meadow asset unavailable: "+artError.getMessage()+"; fallback bitmap");writeBitmap(bmp);}
        }
        else if(Files.size(bmp)<54)throw new IOException("Existing wallpaper file is too small; preserving it");
        Path setup=root.resolve("drive_c/DRNAS-Setup");
        if(Files.isSymbolicLink(setup))throw new IOException("Unsafe Wine setup path");
        Files.createDirectories(setup);
        Path reg=setup.resolve("drnas-desktop-wallpaper.reg");
        if(Files.isSymbolicLink(reg))throw new IOException("Unsafe wallpaper registry file");
        // The REG_SZ value uses escaped backslashes, while actual Wine path has
        // normal drive notation. No user's own wallpapers are overwritten twice.
        String content="Windows Registry Editor Version 5.00\r\n\r\n"+
                "[HKEY_CURRENT_USER\\Control Panel\\Desktop]\r\n"+
                "\"Wallpaper\"=\"C:\\\\users\\\\xuser\\\\Pictures\\\\Drnas-Desktop-Meadow.bmp\"\r\n"+
                "\"WallpaperStyle\"=\"10\"\r\n"+
                "\"TileWallpaper\"=\"0\"\r\n";
        Files.write(reg,content.getBytes(StandardCharsets.UTF_16LE));
        // .reg import requires UTF-16LE BOM for reliable Windows REGEDIT parsing.
        byte[] bytes=Files.readAllBytes(reg);
        try(OutputStream os=new FileOutputStream(reg.toFile())) {
            os.write(0xff);os.write(0xfe);os.write(bytes);
        }
        List<String> cmd=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
        cmd.set(cmd.size()-1,"reg.exe");
        cmd.add("import");
        cmd.add("C:\\DRNAS-Setup\\drnas-desktop-wallpaper.reg");
        ProcessBuilder pb=new ProcessBuilder(cmd).directory(cwd).redirectErrorStream(true);
        pb.environment().clear();pb.environment().putAll(env);
        Process proc=pb.start();
        Thread output=WineServerDiagnostics.pipe(proc,"[wallpaper] ",log,24,raw);
        int result=-1;
        try {
            if(proc.waitFor(12,TimeUnit.SECONDS))result=proc.exitValue();
            else log.accept("Wallpaper registry import timed out. Wine desktop will continue without it.");
        } finally {
            if(proc.isAlive())proc.destroyForcibly();
            output.join(1000);
            proc.getInputStream().close();
        }
        if(result==0) {
            Files.write(marker,new byte[]{'o','k'},StandardOpenOption.CREATE_NEW);
            log.accept("DRNAS BMP wallpaper registered in Wine. Visual PaintDesktop support MUST be checked on device.");
        } else log.accept("Wine reg import returned "+result+"; wallpaper NOT marked active.");
    }

    private static void writeMeadowBitmap(android.content.Context context,Path out) throws IOException {
        android.graphics.Bitmap source;
        try(InputStream in=context.getAssets().open("emudrnas-desktop-meadow-r13.webp")){
            source=android.graphics.BitmapFactory.decodeStream(in);
        }
        if(source==null)throw new IOException("Cannot decode desktop artwork");
        android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(source,WIDTH,HEIGHT,true);
        Path temp=Files.createTempFile(out.getParent(),".drnas-meadow-",".bmp");
        try{
            int stride=((WIDTH*3+3)/4)*4;
            int[] row=new int[WIDTH];
            try(DataOutputStream stream=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))){
                stream.writeByte('B');stream.writeByte('M');
                le32(stream,54+stride*HEIGHT);le16(stream,0);le16(stream,0);le32(stream,54);
                le32(stream,40);le32(stream,WIDTH);le32(stream,HEIGHT);
                le16(stream,1);le16(stream,24);le32(stream,0);le32(stream,stride*HEIGHT);
                le32(stream,2835);le32(stream,2835);le32(stream,0);le32(stream,0);
                for(int y=HEIGHT-1;y>=0;y--){
                    scaled.getPixels(row,0,WIDTH,0,y,WIDTH,1);
                    for(int pixel:row){stream.writeByte(pixel&255);stream.writeByte((pixel>>>8)&255);stream.writeByte((pixel>>>16)&255);}
                    for(int p=WIDTH*3;p<stride;p++)stream.writeByte(0);
                }
            }
            Files.move(temp,out,StandardCopyOption.ATOMIC_MOVE);
        }finally{
            Files.deleteIfExists(temp);
            if(scaled!=source)scaled.recycle();source.recycle();
        }
    }

    static void writeBitmap(Path out) throws IOException {
        if(Files.exists(out,LinkOption.NOFOLLOW_LINKS))return;
        Path tmp=out.resolveSibling("."+FILE+".tmp");
        try {
            int stride=((WIDTH*3+3)/4)*4;
            int size=54+stride*HEIGHT;
            try(DataOutputStream stream=new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tmp.toFile())))) {
                stream.writeByte('B');stream.writeByte('M');
                le32(stream,size);le16(stream,0);le16(stream,0);le32(stream,54);
                le32(stream,40);le32(stream,WIDTH);le32(stream,HEIGHT);
                le16(stream,1);le16(stream,24);le32(stream,0);
                le32(stream,stride*HEIGHT);le32(stream,2835);le32(stream,2835);
                le32(stream,0);le32(stream,0);
                for(int row=HEIGHT-1;row>=0;row--) {
                    double y=row/(double)HEIGHT;
                    for(int col=0;col<WIDTH;col++) {
                        double x=col/(double)WIDTH;
                        double glow=Math.exp(-((x-0.67)*(x-0.67)*13+(y-0.38)*(y-0.38)*20));
                        double sweep=Math.max(0,1-Math.abs(y-(0.72-0.38*x))*5.5)*0.45;
                        int red=clamp(8+29*y+32*glow+25*sweep);
                        int green=clamp(34+38*x+78*glow+44*sweep);
                        int blue=clamp(80+55*(1-y)+78*glow+50*sweep);
                        stream.writeByte(blue);stream.writeByte(green);stream.writeByte(red);
                    }
                    for(int padding=WIDTH*3;padding<stride;padding++)stream.writeByte(0);
                }
            }
            Files.move(tmp,out,StandardCopyOption.ATOMIC_MOVE);
        } catch(IOException | RuntimeException ex) {
            Files.deleteIfExists(tmp);throw ex;
        }
    }
    private static int clamp(double value) {
        return (int)Math.max(0,Math.min(255,Math.round(value)));
    }
    private static void le16(DataOutputStream output,int value)throws IOException {
        output.writeByte(value&255);output.writeByte((value>>>8)&255);
    }
    private static void le32(DataOutputStream output,int value)throws IOException {
        le16(output,value&65535);le16(output,(value>>>16)&65535);
    }
    private WineWallpaper(){}
}
