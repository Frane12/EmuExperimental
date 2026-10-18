/*
 * DRNAS independent GPU diagnostic: a real offscreen OpenGL ES 3 context
 * using Android EGL. NOT a Wine WGL or desktop OpenGL implementation.
 * Runs only as an isolated native child on explicit menu selection.
 */
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <stdio.h>
#include <stdlib.h>

#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

int main(void) {
    setvbuf(stdout,NULL,_IONBF,0);
    EGLDisplay display=EGL_NO_DISPLAY;
    EGLContext context=EGL_NO_CONTEXT;
    EGLSurface surface=EGL_NO_SURFACE;
    EGLConfig config=NULL;
    EGLint count=0,major=0,minor=0;
    int result=1;
    const EGLint config_attrs[]={
        EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT_KHR,
        EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,
        EGL_ALPHA_SIZE,8,EGL_NONE};
    const EGLint pbuffer_attrs[]={EGL_WIDTH,2,EGL_HEIGHT,2,EGL_NONE};
    const EGLint context_attrs[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE};
    printf("DRNAS_GLES_BACKEND Android native EGL + OpenGL ES (not Wine WGL)\n");
    display=eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if(display==EGL_NO_DISPLAY || !eglInitialize(display,&major,&minor)) {
        printf("DRNAS_GLES_ERROR eglInitialize=0x%x\n",eglGetError());
        goto done;
    }
    printf("DRNAS_GLES_EGL version=%d.%d\n",major,minor);
    if(!eglBindAPI(EGL_OPENGL_ES_API) ||
        !eglChooseConfig(display,config_attrs,&config,1,&count) || count!=1) {
        printf("DRNAS_GLES_ERROR EGL_ES3_CONFIG=0x%x candidates=%d\n",
                eglGetError(),count);
        goto done;
    }
    surface=eglCreatePbufferSurface(display,config,pbuffer_attrs);
    if(surface==EGL_NO_SURFACE) {
        printf("DRNAS_GLES_ERROR eglCreatePbufferSurface=0x%x\n",eglGetError());
        goto done;
    }
    context=eglCreateContext(display,config,EGL_NO_CONTEXT,context_attrs);
    if(context==EGL_NO_CONTEXT) {
        printf("DRNAS_GLES_ERROR eglCreateContext=0x%x\n",eglGetError());
        goto done;
    }
    if(!eglMakeCurrent(display,surface,surface,context)) {
        printf("DRNAS_GLES_ERROR eglMakeCurrent=0x%x\n",eglGetError());
        goto done;
    }
    const char *version=(const char *)glGetString(GL_VERSION);
    const char *renderer=(const char *)glGetString(GL_RENDERER);
    const char *vendor=(const char *)glGetString(GL_VENDOR);
    printf("DRNAS_GLES_DEVICE version=%s renderer=%s vendor=%s\n",
           version?version:"UNKNOWN",renderer?renderer:"UNKNOWN",
           vendor?vendor:"UNKNOWN");
    glViewport(0,0,2,2);
    glClearColor(0.25f,0.5f,0.75f,1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    GLubyte pixels[4]={0};
    glReadPixels(0,0,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
    GLenum gl_error=glGetError();
    printf("DRNAS_GLES_READBACK rgba=%u,%u,%u,%u gl_error=0x%x\n",
           pixels[0],pixels[1],pixels[2],pixels[3],gl_error);
    if(gl_error!=GL_NO_ERROR ||
       abs((int)pixels[0]-64)>8 ||
       abs((int)pixels[1]-128)>8 ||
       abs((int)pixels[2]-191)>8 ||
       pixels[3]<240) {
        printf("DRNAS_GLES_RESULT FAIL framebuffer readback mismatch\n");
        goto done;
    }
    puts("DRNAS_GLES_RESULT PASS real EGL ES3 clear/readback; Wine OpenGL unsupported");
    result=0;
done:
    if(display!=EGL_NO_DISPLAY) {
        eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
        if(context!=EGL_NO_CONTEXT)eglDestroyContext(display,context);
        if(surface!=EGL_NO_SURFACE)eglDestroySurface(display,surface);
        eglTerminate(display);
    }
    return result;
}
