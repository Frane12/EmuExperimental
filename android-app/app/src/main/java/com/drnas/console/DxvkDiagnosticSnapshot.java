package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

/** Incremental, bounded DXVK logs. No game files are touched. */
final class DxvkDiagnosticSnapshot {
    static void report(File directory,Map<String,String> seen,Consumer<String> log) {
        File[] files=directory.listFiles((dir,name)->name.endsWith(".log") &&
                (name.contains("d3d") || name.contains("dxgi")));
        if(files==null)return;
        Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
        int checked=0;
        for(File file:files) {
            if(++checked>24)break;
            String id=file.getName();
            String signature=file.length()+":"+file.lastModified();
            if(signature.equals(seen.get(id)))continue;
            seen.put(id,signature);
            log.accept("DRNAS_DXVK_AUDIT UPDATED "+id+" bytes="+file.length());
            if(file.length()==0)continue;
            try(RandomAccessFile input=new RandomAccessFile(file,"r")) {
                long len=input.length();
                int size=(int)Math.min(len,32768);
                input.seek(len-size);
                byte[] data=new byte[size];
                input.readFully(data);
                String[] lines=new String(data,StandardCharsets.UTF_8).split("\\r?\\n");
                int emitted=0;
                for(int i=lines.length-1;i>=0&&emitted<7;i--){
                    String line=lines[i].trim();
                    String lower=line.toLowerCase(java.util.Locale.ROOT);
                    if(lower.startsWith("err:") || lower.startsWith("warn:") ||
                            lower.contains("swapchain") || lower.contains("present") ||
                            lower.contains("dxvk: v") || lower.contains("adapter")) {
                        log.accept("DRNAS_DXVK_AUDIT "+id+" | "+line.substring(0,Math.min(line.length(),400)));
                        emitted++;
                    }
                }
                if(emitted==0)log.accept("DRNAS_DXVK_AUDIT "+id+" has no matching errors or present markers in last "+size+" bytes");
            } catch(IOException error) {
                log.accept("DRNAS_DXVK_AUDIT READ_FAILED "+id+": "+error.getMessage());
            }
        }
    }
    private DxvkDiagnosticSnapshot(){}
}
