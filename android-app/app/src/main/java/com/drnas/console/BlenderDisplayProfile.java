package com.drnas.console;

/** Guest framebuffer only; does not change a project's render output size. */
final class BlenderDisplayProfile {
    static final class Resolution {
        final int width,height;
        Resolution(int width,int height){this.width=width;this.height=height;}
        String id(){return width+"x"+height;}
    }
    static final Resolution LOW=new Resolution(960,544);
    static final Resolution HD=new Resolution(1280,720);
    static Resolution choose(String gpuName,String requested) {
        if("960x544".equals(requested))return LOW;
        if("1280x720".equals(requested))return HD;
        return "A810".equals(TurnipDevicePolicy.target(gpuName))?LOW:HD;
    }
    private BlenderDisplayProfile(){}
}
