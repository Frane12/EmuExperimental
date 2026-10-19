package com.drnas.console;

import java.util.ArrayList;
import java.util.List;

/** Immutable APK driver choices. Never offer another chip's tuned driver. */
final class GraphicsDriverCatalog {
    static final class Entry {
        final String id,label,target,asset,source,sha;
        final int minApi;
        Entry(String id,String label,String target,String asset,String source,String sha,int minApi) {
            this.id=id;this.label=label;this.target=target;this.asset=asset;
            this.source=source;this.sha=sha;this.minApi=minApi;
        }
        boolean supports(String gpu,int api){return api>=minApi&&TurnipDevicePolicy.matches(target,gpu);}
    }
    static final Entry[] ENTRIES={
        new Entry("A6XX_A7XX_R6","Mesa Turnip R6 · A6xx/A7xx","A6XX_A7XX","Turnip-A6xx-A7xx-R6.zip","Turnip_v26.3.0-R6.zip","5fa2305eb9eff64d9347a670e6ca5ef7c518f60e9e782a4db7ee5588c9852d81",28),
        new Entry("A810_ULTIMATE","Drnas S Ultimate · A810","A810","Drnas-Turnip-A810.zip","Drnas-Turnip-A810-S-Ultimate-v1.0.zip","b32c541e76556f06f8ec36e7cafe5034cefe2ae8c3d4f38939d2832a963bc8c0",26),
        new Entry("A810_H2.7","NeuroFlow H2.7 · A810 · experimental","A810","Drnas-Turnip-A810-H2.7.zip","Drnas-Turnip-NeuroFlow-A810-H2.7-TESTING-ONLY.zip","90f047876870c70b986e01ed4784fbafb183ad805dcccabdd6cd695502f1d82d",26),
        new Entry("A830_SMART","Drnas S1 Smart · A830","A830","Drnas-Turnip-A830.zip","Drnas-Turnip-S1-A830.zip","c5660b57f39b4334d64811b51f496d19327de975f1368b7f936941c00eb577d5",26),
        new Entry("A830_H2.4","NeuroFlow H2.4 · A830 · experimental","A830","Drnas-Turnip-A830-H2.4.zip","Drnas-Turnip-NeuroFlow-A830-H2.4-TESTING-ONLY.zip","36676a9588d41eb1e8fb016e54c0ec07611a8a3369fd13782bc456c8e6b0f79c",26),
        new Entry("A840_H2.9","NeuroFlow H2.9 Aggressive · A840 · experimental","A840","Drnas-Turnip-A840.zip","Drnas-Turnip-NeuroFlow-A840-H2.9-TESTING-ONLY.zip","6b67ba723b2a4881214591055c637e2e513363c44874559d16e2a13b1019bb3d",26),
        new Entry("A840_H2.8","NeuroFlow H2.8 · A840 · experimental","A840","Drnas-Turnip-A840-H2.8.zip","Drnas-Turnip-NeuroFlow-A840-H2.8-TESTING-ONLY.zip","033039c5c9843ced2f119387bdc0b21af3fa359f97d8fabaa3189dbc411510bc",26)
    };
    static Entry find(String id){for(Entry e:ENTRIES)if(e.id.equals(id))return e;return null;}
    static List<Entry> compatible(String gpu,int api){
        List<Entry> result=new ArrayList<>();for(Entry e:ENTRIES)if(e.supports(gpu,api))result.add(e);return result;
    }
    private GraphicsDriverCatalog(){}
}
