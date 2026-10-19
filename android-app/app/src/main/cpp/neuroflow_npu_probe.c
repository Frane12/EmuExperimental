#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <dlfcn.h>

/* NNAPI is deprecated on recent Android. Its device list is merely a
 * diagnostic hint, never a claim that actual NPU inference works.
 * dlopen/dlsym avoids requiring a newer NNAPI library on old Android.
 */
typedef int (*get_count_t)(uint32_t *);
typedef int (*get_device_t)(uint32_t,void **);
typedef int (*get_type_t)(const void *,int32_t *);
typedef int (*get_name_t)(const void *,const char **);

JNIEXPORT jstring JNICALL
Java_com_drnas_console_NeuroFlowHardwareProbe_queryNnapiDevices(
        JNIEnv *env,jclass clazz) {
    (void)clazz;
    void *handle=dlopen("libneuralnetworks.so",RTLD_NOW|RTLD_LOCAL);
    if(!handle)return (*env)->NewStringUTF(env,"NNAPI runtime unavailable");
    get_count_t count_fn=(get_count_t)dlsym(handle,"ANeuralNetworks_getDeviceCount");
    get_device_t dev_fn=(get_device_t)dlsym(handle,"ANeuralNetworks_getDevice");
    get_type_t type_fn=(get_type_t)dlsym(handle,"ANeuralNetworksDevice_getType");
    get_name_t name_fn=(get_name_t)dlsym(handle,"ANeuralNetworksDevice_getName");
    char report[768]="NNAPI unavailable or missing device enumeration";
    if(count_fn && dev_fn && type_fn && name_fn) {
        uint32_t count=0,found=0;
        if(count_fn(&count)==0) {
            snprintf(report,sizeof(report),"NNAPI devices=%u accelerator_candidates=",count);
            size_t used=strlen(report);
            for(uint32_t i=0;i<count && i<32;i++) {
                void *device=NULL;int32_t type=-1;const char *name=NULL;
                if(dev_fn(i,&device)!=0 || !device || type_fn(device,&type)!=0 ||
                        type!=3 || name_fn(device,&name)!=0 || !name)continue;
                int n=snprintf(report+used,sizeof(report)-used,
                        "%s%.96s",found?",":"",name);
                if(n<0 || (size_t)n>=sizeof(report)-used)break;
                used+=(size_t)n;
                found++;
            }
            if(!found)strncat(report,"none_exposed",sizeof(report)-strlen(report)-1);
        } else snprintf(report,sizeof(report),"NNAPI device enumeration failed");
    }
    dlclose(handle);
    return (*env)->NewStringUTF(env,report);
}
