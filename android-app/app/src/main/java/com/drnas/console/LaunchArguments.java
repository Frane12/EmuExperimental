package com.drnas.console;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parses per-shortcut arguments into argv, never into a shell command.
 * A Windows executable and each argument are separate Wine ProcessBuilder items.
 */
final class LaunchArguments {
    static List<String> parse(String input) throws IOException {
        if(input==null || input.trim().isEmpty())return Collections.emptyList();
        if(input.length()>400)throw new IOException("Launch arguments exceed 400 characters");
        List<String> items=new ArrayList<>();
        StringBuilder token=new StringBuilder();
        boolean quoted=false,started=false;
        for(int i=0;i<input.length();i++){
            char c=input.charAt(i);
            if(c=='\n'||c=='\r'||c==0||Character.isISOControl(c))
                throw new IOException("Control characters are not allowed in launch arguments");
            if(c=='\\' && i+1<input.length() && input.charAt(i+1)=='"'){
                token.append('"');i++;started=true;continue;
            }
            if(c=='"'){quoted=!quoted;started=true;continue;}
            if(Character.isWhitespace(c)&&!quoted){
                if(started){items.add(token.toString());token.setLength(0);started=false;}
            }else{token.append(c);started=true;}
            if(items.size()>16)throw new IOException("Too many launch arguments");
        }
        if(quoted)throw new IOException("Unclosed quote in launch arguments");
        if(started)items.add(token.toString());
        if(items.size()>16)throw new IOException("Too many launch arguments");
        return items;
    }

    /**
     * Blender has to choose its own Vulkan renderer; WineVulkan does not
     * silently change a program's renderer. Do not mutate other apps.
     */
    static List<String> forApp(String executable,String custom,boolean nativeVulkan)
            throws IOException {
        List<String> args=new ArrayList<>(parse(custom));
        if(nativeVulkan && executable!=null &&
                executable.toLowerCase(Locale.ROOT).replace('\\','/')
                    .endsWith("/blender.exe")) {
            boolean explicitlySelected=false;
            for(int i=0;i<args.size();i++)
                if("--gpu-backend".equalsIgnoreCase(args.get(i)) ||
                        args.get(i).toLowerCase(Locale.ROOT).startsWith("--gpu-backend="))
                    explicitlySelected=true;
            if(!explicitlySelected){args.add("--gpu-backend");args.add("vulkan");}
        }
        if(args.size()>18)throw new IOException("Too many launch arguments");
        return args;
    }
    /**
     * Wine explorer.exe can follow a .lnk but is not guaranteed to forward
     * appended command-line arguments to the linked executable. For .lnk
     * profiles with custom flags or Native Vulkan, launch our isolated Win32
     * ShellLink helper instead. That helper uses IShellLinkW to preserve the
     * shortcut's own arguments and working directory.
     *
     * Empty list means use Wine's unmodified/default .lnk handling.
     */
    static List<String> forLink(String guestLink,String custom,boolean nativeVulkan)
            throws IOException {
        List<String> extra=parse(custom);
        if(!nativeVulkan && extra.isEmpty())return Collections.emptyList();
        if(guestLink==null ||
                !guestLink.toLowerCase(Locale.ROOT).endsWith(".lnk") ||
                !(guestLink.startsWith("C:\\")||guestLink.startsWith("R:\\")))
            throw new IOException("Link must be a verified C:/R: guest .lnk");
        List<String> args=new ArrayList<>();
        args.add(guestLink);
        args.add(nativeVulkan?"vulkan":"auto");
        args.addAll(extra);
        return args;
    }
    private LaunchArguments(){}
}
