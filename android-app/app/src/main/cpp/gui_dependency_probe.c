#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Separate bounded Bionic process. dlerror belongs to the same loader namespace
 * and LD_LIBRARY_PATH as Wine. Never load guest modules into the Android UI. */
/* Wine's WGL uses dlopen by SONAME. Probe in a NEW Bionic process with
 * exactly Wine's LD_LIBRARY_PATH; never preload absolute path before SONAME. */
static int blender_libgl_probe(const char *absolute_file) {
    const char *search=getenv("LD_LIBRARY_PATH");
    printf("DRNAS_BLENDER_LIBGL_PROBE_ENV=%s\n",
      search && strstr(search,"/opengl-runtime/v1/lib") ?
      "MESA_PATH_PRESENT" : "MESA_PATH_MISSING");
    dlerror();
    void *named=dlopen("libGL.so.1",RTLD_NOW|RTLD_LOCAL);
    if(named) {
        printf("DRNAS_BLENDER_LIBGL_PROBE_SONAME=OK (Wine WGL context not verified)\n");
        dlclose(named);
        return 0;
    }
    const char *why=dlerror();
    printf("DRNAS_BLENDER_LIBGL_PROBE_SONAME=FAILED reason=%s\n",
        why?why:"unknown");
    dlerror();
    void *absolute=dlopen(absolute_file,RTLD_NOW|RTLD_LOCAL);
    if(absolute) {
        printf("DRNAS_BLENDER_LIBGL_PROBE_ABSOLUTE=OK (SONAME path not resolved)\n");
        dlclose(absolute);
        return 31;
    }
    why=dlerror();
    printf("DRNAS_BLENDER_LIBGL_PROBE_ABSOLUTE=FAILED reason=%s\n",
        why?why:"unknown");
    return 32;
}

int main(int argc,char **argv) {
    setvbuf(stdout,NULL,_IONBF,0);
    if(argc==3 && strcmp(argv[1],"--blender-libgl-probe")==0)
        return blender_libgl_probe(argv[2]);
    const char *names[]={"libX11.so.6","libXext.so.6","libXrender.so.1","libXrandr.so.2",
        "libXcursor.so.1","libXi.so.6","libXinerama.so.1","libXfixes.so.3",
        "libXcomposite.so.1","libXxf86vm.so.1","libfreetype.so.6","libfontconfig.so.1","libvulkan.so.1",
        "libX11.so","libXext.so","libXrender.so","libXrandr.so","libXcursor.so",
        "libXi.so","libXinerama.so","libXfixes.so","libXcomposite.so","libXxf86vm.so",
        "libfreetype.so","libfontconfig.so"};
    setvbuf(stdout,NULL,_IONBF,0);int errors=0;void *xlib=NULL;
    for(size_t i=0;i<sizeof(names)/sizeof(names[0]);i++) {
        dlerror();void *h=dlopen(names[i],RTLD_NOW|RTLD_GLOBAL);
        if(!h){const char *e=dlerror();printf("FAIL %s: %s\n",names[i],e?e:"unknown");errors++;}
        else {printf("OK %s\n",names[i]);if(i==0)xlib=h;}
    }
    if(xlib){
        typedef void *(*open_fn)(const char *);typedef int (*close_fn)(void *);
        open_fn open_display=(open_fn)dlsym(xlib,"XOpenDisplay");
        close_fn close_display=(close_fn)dlsym(xlib,"XCloseDisplay");
        void *display=open_display?open_display(getenv("DISPLAY")):NULL;
        printf("Xlib XOpenDisplay=%s\n",display?"CONNECTED":"FAILED");
        if(display&&close_display)close_display(display);
    }
    if(argc>1){
        void *module=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);
        if(module)printf("winex11.so dlopen=OK (not Wine initialization)\n");
        else {const char *e=dlerror();printf("winex11.so standalone dlerror: %s (Wine exports may require Wine process)\n",e?e:"unknown");
            if(e && strstr(e,"library \"") && strstr(e,"not found"))errors++;
        }
    }
    /* Optional for DXVK/Vulkan, but required by Wine's OpenGL/media backends.
     * Run loader checks in this isolated helper, never in the Android UI. */
    const char *optional[]={"libEGL.so.1","libGL.so.1","libavutil.so.60","libgstgl-1.0.so"};
    const char *purpose[]={"Wine 11 EGL/Zink (no X11 context attempted)","Wine GLX fallback","Wine winedmo media","Wine winegstreamer media"};
    int missing=0;
    for(size_t i=0;i<sizeof(optional)/sizeof(optional[0]);i++){
        dlerror();void *h=dlopen(optional[i],RTLD_NOW|RTLD_LOCAL);
        if(h){
            printf("DRNAS_DEP_AUDIT OPTIONAL_LOAD_OK %s (%s)\n",optional[i],purpose[i]);
            if(i==0) {
                void *egl_get=(void *)dlsym(h,"eglGetDisplay");
                void *egl_init=(void *)dlsym(h,"eglInitialize");
                void *egl_proc=(void *)dlsym(h,"eglGetProcAddress");
                printf("DRNAS_EGL_LOADER_PROBE eglGetDisplay=%s eglInitialize=%s eglGetProcAddress=%s (no display/context)\n",
                        egl_get?"OK":"MISSING",egl_init?"OK":"MISSING",egl_proc?"OK":"MISSING");
            }
            dlclose(h);
        } else {
            const char *e=dlerror();
            printf("DRNAS_DEP_AUDIT OPTIONAL_LOAD_MISSING %s (%s): %s\n",
                   optional[i],purpose[i],e?e:"unknown");
            missing++;
        }
    }
    printf("DRNAS_DEP_AUDIT RESULT essential_failures=%d optional_loader_failures=%d; optional missing libs do not prove Direct3D failure\n",errors,missing);
    return errors?1:0;
}
