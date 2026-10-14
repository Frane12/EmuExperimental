#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>

/*
 * Android accelerator discovery — read-only capabilities probe.
 * No DSP FastRPC calls, QNN graphs, or NNAPI models are executed.
 *
 * NNAPI is deprecated on recent Android. A listed NNAPI ACCELERATOR
 * is evidence of a registered device, not proof of Hexagon execution.
 */
static void append(char *out, size_t cap, size_t *used, const char *fmt, ...) {
    if (*used >= cap) return;
    va_list args;
    va_start(args, fmt);
    int n = vsnprintf(out + *used, cap - *used, fmt, args);
    va_end(args);
    if (n < 0) return;
    if ((size_t)n >= cap - *used) *used = cap - 1;
    else *used += (size_t)n;
}

static void library_probe(char *out, size_t cap, size_t *used,
                          const char *label, const char *library) {
    dlerror();
    void *handle = dlopen(library, RTLD_NOW | RTLD_LOCAL);
    if (handle) {
        append(out,cap,used,"%s: loadable (execution NOT tested)\n",label);
        dlclose(handle);
    } else {
        const char *error = dlerror();
        append(out,cap,used,"%s: unavailable/inaccessible\n",label);
        if (error) {
            /* Restrict message length; diagnostic is untrusted system text. */
            append(out,cap,used,"  loader: %.180s\n",error);
        }
    }
}

typedef int (*nn_count_t)(uint32_t *);
typedef int (*nn_device_t)(uint32_t, void **);
typedef int (*nn_name_t)(const void *, const char **);
typedef int (*nn_type_t)(const void *, int32_t *);

static const char *nn_type_label(int32_t type) {
    switch (type) {
        case 1: return "OTHER";
        case 2: return "CPU";
        case 3: return "GPU";
        case 4: return "ACCELERATOR";
        default: return "UNKNOWN";
    }
}

JNIEXPORT jstring JNICALL
Java_com_drnas_console_NativeHost_nativeAcceleratorProbe(JNIEnv *env, jclass cls) {
    (void)cls;
    char out[3500] = {0};
    size_t used = 0;
    append(out,sizeof(out),&used,
        "Drnas CPU Assist v0.2: Android accelerator discovery; "
        "NO NPU execution.\n");
    library_probe(out,sizeof(out),&used,"FastRPC CDSP","libcdsprpc.so");
    library_probe(out,sizeof(out),&used,"FastRPC ADSP","libadsprpc.so");
    library_probe(out,sizeof(out),&used,"QNN HTP backend","libQnnHtp.so");

    dlerror();
    void *lib = dlopen("libneuralnetworks.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        append(out,sizeof(out),&used,
            "NNAPI: not accessible from this app (NO NPU inference).\n");
        const char *error = dlerror();
        if (error) append(out,sizeof(out),&used,"  loader: %.180s\n",error);
    } else {
        nn_count_t get_count = (nn_count_t)dlsym(lib,"ANeuralNetworks_getDeviceCount");
        nn_device_t get_device = (nn_device_t)dlsym(lib,"ANeuralNetworks_getDevice");
        nn_name_t get_name = (nn_name_t)dlsym(lib,"ANeuralNetworksDevice_getName");
        nn_type_t get_type = (nn_type_t)dlsym(lib,"ANeuralNetworksDevice_getType");
        if (!get_count || !get_device || !get_name || !get_type) {
            append(out,sizeof(out),&used,"NNAPI: enumeration unavailable.\n");
        } else {
            uint32_t count = 0;
            int rc = get_count(&count);
            append(out,sizeof(out),&used,
                   "NNAPI enumeration: return=%d, device_count=%u\n",rc,count);
            if (rc == 0) {
                for (uint32_t i=0; i<count && i<12; ++i) {
                    void *device = NULL;
                    int d_rc=get_device(i,&device);
                    if (d_rc != 0 || !device) {
                        append(out,sizeof(out),&used,
                               " device[%u]: getDevice returned %d\n",i,d_rc);
                        continue;
                    }
                    const char *name=NULL;
                    int32_t type=0;
                    int n_rc=get_name(device,&name);
                    int t_rc=get_type(device,&type);
                    append(out,sizeof(out),&used,
                           " device[%u]: %s type=%s (%d), status=%d/%d\n",
                           i,(n_rc==0 && name)?name:"(unnamed)",
                           t_rc==0?nn_type_label(type):"unknown",type,n_rc,t_rc);
                }
                if (count>12) append(out,sizeof(out),&used,
                    "  only first 12 devices displayed.\n");
            }
        }
        dlclose(lib);
    }
    append(out,sizeof(out),&used,
        "NOTE: No accelerator call was made. "
        "NNAPI registration / loadability != verified Hexagon offload.");
    return (*env)->NewStringUTF(env,out);
}
