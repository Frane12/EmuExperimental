package com.drnas.console;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Blender-only Wine frame policy: our X server has no external window manager. */
final class BlenderWindowSetup {
    static void ensure(File prefix,File linker,File wine,File cwd,Map<String,String> env,
                       Consumer<String> log,RawWineTrace raw) throws Exception {
        Path root=prefix.getCanonicalFile().toPath();
        Path marker=root.resolve(".drnas-blender-window-r38-restore-v1");
        if(Files.isSymbolicLink(marker))throw new IOException("Unsafe Blender frame marker");
        if(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))return;
        Path setup=root.resolve("drive_c/DRNAS-Setup");
        if(Files.isSymbolicLink(setup))throw new IOException("Unsafe Blender registry folder");
        Files.createDirectories(setup);
        Path file=setup.resolve("blender-window-frames.reg");
        if(Files.isSymbolicLink(file))throw new IOException("Unsafe Blender frame policy file");
        // A/B r48: explicitly undo the persistent r46 Wine per-app overrides.
        // Remove only these two values; keep all other user/prefix settings.
        String registry="\ufeffWindows Registry Editor Version 5.00\r\n\r\n"+
                "[HKEY_CURRENT_USER\\Software\\Wine\\AppDefaults\\blender.exe\\X11 Driver]\r\n"+
                "\"Managed\"=-\r\n\"Decorated\"=-\r\n";
        Files.write(file,registry.getBytes(StandardCharsets.UTF_16LE));
        List<String> args=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
        args.set(args.size()-1,"reg.exe");
        args.add("import");args.add("C:\\DRNAS-Setup\\blender-window-frames.reg");
        ProcessBuilder builder=new ProcessBuilder(args).directory(cwd).redirectErrorStream(true);
        builder.environment().clear();builder.environment().putAll(env);
        Process process=builder.start();
        Thread output=WineServerDiagnostics.pipe(process,"[blender-frames] ",log,16,raw);
        int result=-1;
        try {
            if(process.waitFor(12,TimeUnit.SECONDS))result=process.exitValue();
        } finally {
            if(process.isAlive())process.destroyForcibly();
            output.join(1000);process.getInputStream().close();
        }
        if(result!=0)throw new IOException("Blender window-frame setup failed: "+result);
        Files.write(marker,new byte[]{'o','k'},StandardOpenOption.CREATE_NEW);
        log.accept("DRNAS_BLENDER_AB_R38_POLICY: removed managed/decorated overrides; fullscreen without Win32 layout hook");
    }
    private BlenderWindowSetup(){}
}
