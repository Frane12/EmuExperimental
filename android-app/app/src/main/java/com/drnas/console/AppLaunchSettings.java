package com.drnas.console;

import android.content.Context;
import org.json.JSONObject;

/** Immutable per-icon settings. Drafts become persistent only on Accept. */
final class AppLaunchSettings {
    static final class Profile {
        final String title,dxvk;
        final int fps,stretch;
        final float sensitivity;
        Profile(String title,int fps,String dxvk,int stretch,float sensitivity){
            this.title=title==null?"":title.trim();
            this.fps=fps<0?-1:Math.min(240,fps);
            this.dxvk="async".equals(dxvk)||"standard".equals(dxvk)?dxvk:"default";
            this.stretch=stretch==0||stretch==1?stretch:-1;
            this.sensitivity=Float.isFinite(sensitivity)&&sensitivity>=.5f?
                    Math.min(10f,sensitivity):-1f;
        }
        static Profile defaults(){return new Profile("",-1,"default",-1,-1);}
        static Profile parse(String text){
            try{
                JSONObject o=new JSONObject(text);
                return new Profile(o.optString("title",""),o.optInt("fps",-1),
                        o.optString("dxvk","default"),o.optInt("stretch",-1),
                        (float)o.optDouble("sensitivity",-1));
            }catch(Exception error){return defaults();}
        }
        String json() throws Exception {
            return new JSONObject().put("title",title).put("fps",fps).put("dxvk",dxvk)
                    .put("stretch",stretch).put("sensitivity",sensitivity).toString();
        }
        int fps(int global){return fps<0?global:fps;}
        String dxvk(String global){return "default".equals(dxvk)?global:dxvk;}
        boolean stretch(boolean global){return stretch<0?global:stretch==1;}
        float sensitivity(float global){return sensitivity<0?global:sensitivity;}
        String title(String original){return title.isEmpty()?original:title;}
        Profile withFps(int value){return new Profile(title,value,dxvk,stretch,sensitivity);}
        Profile withDxvk(String value){return new Profile(title,fps,value,stretch,sensitivity);}
        Profile withStretch(boolean value){return new Profile(title,fps,dxvk,value?1:0,sensitivity);}
        Profile withSensitivity(float value){return new Profile(title,fps,dxvk,stretch,value);}
    }
    private static boolean valid(String id){return WineShortcuts.isLaunchRequest(id);}
    static Profile load(Context context,String id){
        if(!valid(id))return Profile.defaults();
        return Profile.parse(context.getSharedPreferences("drnas_app_profiles",Context.MODE_PRIVATE)
                .getString(id,""));
    }
    static void save(Context context,String id,Profile profile) throws Exception {
        if(!valid(id))throw new IllegalArgumentException("Nepoznata ikona");
        if(!context.getSharedPreferences("drnas_app_profiles",Context.MODE_PRIVATE).edit()
                .putString(id,profile.json()).commit())
            throw new java.io.IOException("Postavke nisu spremljene");
    }
    static void remove(Context context,String id){
        if(valid(id))context.getSharedPreferences("drnas_app_profiles",Context.MODE_PRIVATE)
                .edit().remove(id).apply();
    }
    private AppLaunchSettings(){}
}
