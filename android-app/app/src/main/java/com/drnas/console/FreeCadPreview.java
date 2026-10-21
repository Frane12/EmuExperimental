package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;

/** Read-only FreeCAD Windows executable discovery. No installation or file mutation. */
final class FreeCadPreview {
    static final String ID = "freecad-lab";
    private FreeCadPreview() {}

    /** Rechecks the installed binary when the Wine process is actually launched. */
    static String launchWinePath(File prefix) throws IOException {
        File cDrive = new File(prefix, "drive_c");
        File exe = findExecutable(cDrive);
        if (exe == null) throw new IOException("FreeCAD Windows EXE is not installed on C:");
        String pe = architecture(exe);
        if (!"x64".equals(pe) && !"arm64".equals(pe))
            throw new IOException("Unsupported FreeCAD PE type: " + pe);
        return WineGuiSession.windowsPath(cDrive, exe, 'C');
    }

    static File findExecutable(File cDrive) throws IOException {
        File root = cDrive.getCanonicalFile();
        if (!root.isDirectory()) return null;
        for (File folder : new File[] {new File(root, "FreeCAD"),
                new File(root, "Apps/FreeCAD"),new File(root, "FreeCAD-portable")}) {
            File f = findIn(root, folder);
            if (f != null) return f;
        }
        for (String dir : new String[] {"Program Files", "Program Files (x86)", "Apps"}) {
            File[] folders = new File(root, dir).listFiles();
            if (folders == null) continue;
            for (File folder : folders) {
                if (folder.isDirectory() && folder.getName().toLowerCase(Locale.ROOT).startsWith("freecad")) {
                    File f = findIn(root, folder);
                    if (f != null) return f;
                }
            }
        }
        return null;
    }

    private static File findIn(File root, File folder) throws IOException {
        for (String rel : new String[]{"bin/FreeCAD.exe", "bin/freecad.exe",
                "FreeCAD.exe", "freecad.exe"}) {
            File f = new File(folder, rel).getCanonicalFile();
            if (f.toPath().startsWith(root.toPath()) && f.isFile()) return f;
        }
        return null;
    }

    /** PE machine detection only; never evidence of application compatibility. */
    static String architecture(File exe) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(exe, "r")) {
            if (f.length() < 0x46 || f.readUnsignedByte() != 'M' ||
                    f.readUnsignedByte() != 'Z') return "invalid";
            f.seek(0x3c);
            long offset = u32(f);
            if (offset < 0x40 || offset > f.length() - 6) return "invalid";
            f.seek(offset);
            if (u32(f) != 0x4550L) return "invalid";
            int machine = f.readUnsignedByte() | (f.readUnsignedByte() << 8);
            if (machine == 0x8664) return "x64";
            if (machine == 0xaa64) return "arm64";
            if (machine == 0x014c) return "x86";
            return "unsupported";
        }
    }

    private static long u32(RandomAccessFile f) throws IOException {
        return ((long) f.readUnsignedByte()) | ((long) f.readUnsignedByte() << 8) |
                ((long) f.readUnsignedByte() << 16) | ((long) f.readUnsignedByte() << 24);
    }
}
