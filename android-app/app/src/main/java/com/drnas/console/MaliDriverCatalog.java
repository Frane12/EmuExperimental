package com.drnas.console;

import java.util.*;
import java.util.regex.*;

/** Published, checksum-pinned candidates; G52-specific builds never run on G72/G76. */
final class MaliDriverCatalog {
    static final class Entry {
        final String id,archive,sha;
        final int minApi;
        final boolean g52Only;
        Entry(String id,String archive,String sha,int minApi,boolean g52Only) {
            this.id=id;this.archive=archive;this.sha=sha;this.minApi=minApi;this.g52Only=g52Only;
        }
    }
    static final Entry[] ENTRIES={
        new Entry("PANVK_JIM_R4","mali-panvk-26.3.0-devel-r4.zip","1bf5fdbc6fa7134100f747692436eeee5e112e640a8b5064345facbfac918787",31,false),
        new Entry("PANVK_JIM_R3","mali-panvk-26.3.0-devel-r3.zip","a53aa1747e19e8471bb839de21b570c70a34d868206ffac852e3e684cd284167",31,false),
        new Entry("PANVK_G52_V7","PanVk-V7-0.0.2-alpha.zip","a5da75ae597b23337825c5a35d480c7a7cc0c8655d35850ca5fd25b37ac78289",28,true)
    };
    static String gpu(String model,String name) {
        Matcher m=Pattern.compile("(?i)mali\\s*[- ]?g(52|72|76)(?![0-9])").matcher(name==null?"":name);
        if(m.find())return "G"+m.group(1);
        return "24040RN64Y".equalsIgnoreCase(model)?"G52":"";
    }
    static List<Entry> compatible(String model,String name,int api,String remembered) {
        String gpu=gpu(model,name);
        List<Entry> result=new ArrayList<>();
        if(gpu.isEmpty())return result;
        for(Entry e:ENTRIES)if(api>=e.minApi&&(!e.g52Only||gpu.equals("G52")))result.add(e);
        for(int i=0;i<result.size();i++)if(result.get(i).id.equals(remembered)) {
            result.add(0,result.remove(i));break;
        }
        return result;
    }
    private MaliDriverCatalog(){}
}
