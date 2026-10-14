package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Consumer;

/**
 * Read-only installed Wine PE inventory for the verified GameNative
 * Android/Bionic ARM64EC component. File presence ≠ load success:
 * STATUS_DLL_NOT_FOUND may indicate a dependency failure or a DLL path error.
 */
final class WineDependencyProbe {
    private static final String[] WINE_DLLS={
        "wineboot.exe","ntdll.dll","kernel32.dll","kernelbase.dll",
        "wow64.dll","wow64win.dll","ucrtbase.dll","advapi32.dll"
    };
    private static final String[] ARCH_DIRS={
        "aarch64-windows","arm64ec-windows","x86_64-windows"
    };
    private static int unsignedShort(byte[] buf,int i) {
        return (buf[i]&255)|((buf[i+1]&255)<<8);
    }
    private static long unsignedInt(byte[] buf,int i){
        return Integer.toUnsignedLong((buf[i]&255)|((buf[i+1]&255)<<8)|
                ((buf[i+2]&255)<<16)|((buf[i+3]&255)<<24));
    }
    static String describe(File source) throws IOException {
        if(!source.isFile())return "MISSING";
        if(java.nio.file.Files.isSymbolicLink(source.toPath()))return "SYMLINK (not inspected)";
        long size=source.length();
        if(size<128)return "INVALID size="+size;
        if(size>96L*1024*1024)return "TOO_LARGE size="+size;
        try(RandomAccessFile f=new RandomAccessFile(source,"r")){
            byte[] dos=new byte[64];
            f.readFully(dos);
            if(dos[0]!='M'||dos[1]!='Z')return "NOT_PE size="+size;
            long peOffset=unsignedInt(dos,60);
            if(peOffset<64 || peOffset>size-24 || peOffset>2L*1024*1024)
                return "INVALID_PE_OFFSET size="+size;
            f.seek(peOffset);
            byte[] hdr=new byte[24];
            f.readFully(hdr);
            if(hdr[0]!='P'||hdr[1]!='E'||hdr[2]!=0||hdr[3]!=0)
                return "INVALID_PE_SIGNATURE size="+size;
            int machine=unsignedShort(hdr,4);
            String arch;
            switch(machine) {
                case 0xaa64: arch="ARM64";break;
                case 0xa641: arch="ARM64EC";break;
                case 0x8664: arch="AMD64";break;
                case 0x14c: arch="I386";break;
                default:arch="OTHER";break;
            }
            return "PE "+arch+" machine=0x"+Integer.toHexString(machine)+
                    " size="+size;
        }
    }
    static void inventory(File wineLib,File prefix,Consumer<String> report)
            throws IOException {
        for(String folder:ARCH_DIRS) {
            int present=0,missing=0;
            for(String name:WINE_DLLS) {
                File f=new File(new File(wineLib,folder),name);
                String kind=describe(f);
                if(kind.startsWith("PE ")) present++; else missing++;
                report.accept("[pe inventory] "+folder+"/"+name+" = "+kind);
            }
            report.accept("[pe inventory] "+folder+" complete: "+
                    present+" PE entries, "+missing+" other/absent");
        }
        File sys32=new File(prefix,"drive_c/windows/system32");
        for(String name:new String[]{"kernel32.dll","kernelbase.dll","ntdll.dll","wineboot.exe"}) {
            report.accept("[pe inventory] prefix/system32/"+name+" = "+
                    describe(new File(sys32,name)));
        }
        report.accept("Wine builtin PE candidates alone do not prove that Wine loaded kernel32.dll.");
    }
    private WineDependencyProbe(){}
}
