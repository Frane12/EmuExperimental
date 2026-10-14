#include <jni.h>
#include <sys/utsname.h>
#include <sys/types.h>
#include <unistd.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <elf.h>
#include <dlfcn.h>
#include <vulkan/vulkan.h>
#include <inttypes.h>
#include <limits.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <stdarg.h>

/* Native Android/Bionic host ABI diagnostics. No untrusted guest execution. */
JNIEXPORT jstring JNICALL
Java_com_drnas_console_NativeHost_nativeHostInfo(JNIEnv *env, jclass clazz) {
    (void)clazz;
    struct utsname sys;
    char out[512];
    if (uname(&sys) != 0) {
        snprintf(out, sizeof(out), "NDK host uname failed (errno %d)", errno);
    } else {
        snprintf(out, sizeof(out),
            "DRNAS_NATIVE_HOST_OK pid=%ld uid=%ld arch=%s kernel=%s / Android Bionic",
            (long)getpid(), (long)getuid(), sys.machine, sys.release);
    }
    return (*env)->NewStringUTF(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_drnas_console_NativeHost_nativeVulkanInfo(JNIEnv *env, jclass clazz) {
    (void)clazz;
    void *vk = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!vk) return (*env)->NewStringUTF(env,
        "System libvulkan.so unavailable (this does not test Turnip)");
    typedef int32_t (*get_version_t)(uint32_t *);
    get_version_t fn = (get_version_t)dlsym(vk, "vkEnumerateInstanceVersion");
    char out[192];
    if (fn) {
        uint32_t version = 0;
        int32_t res = fn(&version);
        if (res == 0) snprintf(out, sizeof(out),
                "Android system Vulkan loader: API %u.%u.%u; custom Turnip NOT loaded",
                version >> 22, (version >> 12) & 1023, version & 4095);
        else snprintf(out, sizeof(out),
                "System Vulkan loader responded with VkResult %d", res);
    } else snprintf(out, sizeof(out),
        "System Vulkan library loaded; vkEnumerateInstanceVersion not exported");
    dlclose(vk);
    return (*env)->NewStringUTF(env, out);
}

/* Probe the system Vulkan DEVICE name, never an assumed GPU ID.
 * Unsupported chip -> fail closed to stock Vulkan, not a random Turnip ZIP. */
JNIEXPORT jstring JNICALL
Java_com_drnas_console_NativeHost_nativeVulkanDeviceName(JNIEnv *env,jclass clazz) {
    (void)clazz;
    void *vk=dlopen("libvulkan.so",RTLD_NOW|RTLD_LOCAL);
    if (!vk)return (*env)->NewStringUTF(env,"");
    PFN_vkCreateInstance make=(PFN_vkCreateInstance)dlsym(vk,"vkCreateInstance");
    PFN_vkDestroyInstance destroy=(PFN_vkDestroyInstance)dlsym(vk,"vkDestroyInstance");
    PFN_vkEnumeratePhysicalDevices enumerate=(PFN_vkEnumeratePhysicalDevices)dlsym(vk,"vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties props=(PFN_vkGetPhysicalDeviceProperties)dlsym(vk,"vkGetPhysicalDeviceProperties");
    char name[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE]={0};
    if(make && destroy && enumerate && props) {
        VkApplicationInfo app={.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO,.pApplicationName="DRNAS probe",.apiVersion=VK_API_VERSION_1_0};
        VkInstanceCreateInfo ci={.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,.pApplicationInfo=&app};
        VkInstance instance=VK_NULL_HANDLE;
        if(make(&ci,NULL,&instance)==VK_SUCCESS) {
            uint32_t count=1;
            VkPhysicalDevice device=VK_NULL_HANDLE;
            if(enumerate(instance,&count,&device)==VK_SUCCESS && count>0 && device) {
                VkPhysicalDeviceProperties p;
                memset(&p,0,sizeof(p));
                props(device,&p);
                snprintf(name,sizeof(name),"%s",p.deviceName);
            }
            destroy(instance,NULL);
        }
    }
    dlclose(vk);
    return (*env)->NewStringUTF(env,name);
}

static const char *wine_kind(uint16_t type) {
    switch (type) {
        case ET_EXEC: return "EXEC";
        case ET_DYN: return "DYN/PIE";
        default: return "OTHER";
    }
}

JNIEXPORT jstring JNICALL
Java_com_drnas_console_NativeHost_nativeProbeFile(JNIEnv *env, jclass clazz,
                                                   jstring jpath) {
    (void)clazz;
    if (!jpath) return (*env)->NewStringUTF(env, "Wine path is null");
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (!path) return NULL;
    char out[768];
    FILE *f = fopen(path, "rb");
    if (!f) {
        snprintf(out, sizeof(out), "Cannot open Wine file (errno %d)", errno);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return (*env)->NewStringUTF(env, out);
    }
    unsigned char hdr[sizeof(Elf64_Ehdr)]={0};
    size_t n = fread(hdr, 1, sizeof(hdr), f);
    if (n >= 2 && hdr[0] == '#' && hdr[1] == '!') {
        rewind(f);
        char line[256];
        if (!fgets(line, sizeof(line), f)) line[0]=0;
        line[strcspn(line, "\r\n")] = 0;
        snprintf(out, sizeof(out),
            "Wine entry is a script (%s); script is NOT executable by our native guest loader",
            line);
    } else if (n < sizeof(Elf64_Ehdr) ||
               memcmp(hdr, ELFMAG, SELFMAG) != 0) {
        snprintf(out, sizeof(out), "Wine entry is not ELF64 or a #! script");
    } else if (hdr[EI_CLASS] != ELFCLASS64 ||
               hdr[EI_DATA] != ELFDATA2LSB) {
        snprintf(out, sizeof(out), "Unsupported ELF class/data (class=%d data=%d)",
            hdr[EI_CLASS], hdr[EI_DATA]);
    } else {
        Elf64_Ehdr h;
        memcpy(&h, hdr, sizeof(h));
        if (h.e_machine != EM_AARCH64) {
            snprintf(out, sizeof(out), "Wine ELF machine %u is not ARM64 (%u)",
                (unsigned)h.e_machine, (unsigned)EM_AARCH64);
        } else if (h.e_phnum > 512 || h.e_phentsize != sizeof(Elf64_Phdr) ||
                   h.e_phoff > (uint64_t)LONG_MAX ||
                   h.e_phoff + (uint64_t)h.e_phnum * sizeof(Elf64_Phdr) >
                     (uint64_t)LONG_MAX) {
            snprintf(out, sizeof(out), "Wine ELF has invalid program-header offsets");
        } else {
            const char *interpreter = "(none/static)";
            char interpreterBuf[256];
            for (size_t i=0; i<h.e_phnum; i++) {
                uint64_t pos=h.e_phoff + i*sizeof(Elf64_Phdr);
                if (fseek(f, (long)pos, SEEK_SET) != 0) break;
                Elf64_Phdr ph;
                if (fread(&ph, 1, sizeof(ph), f) != sizeof(ph)) break;
                if (ph.p_type != PT_INTERP) continue;
                if (ph.p_filesz < 2 || ph.p_filesz >= sizeof(interpreterBuf)
                    || ph.p_offset > LONG_MAX ||
                    fseek(f, (long)ph.p_offset, SEEK_SET) != 0 ||
                    fread(interpreterBuf, 1, (size_t)ph.p_filesz, f) != (size_t)ph.p_filesz) {
                    interpreter="(invalid PT_INTERP)";
                    break;
                }
                interpreterBuf[ph.p_filesz]=0;
                interpreterBuf[ph.p_filesz-1]=0;
                interpreter=interpreterBuf;
                break;
            }
            snprintf(out, sizeof(out),
                "Wine ELF64 AArch64 type=%s, PT_INTERP=%s; guest dynamic linker NOT launched",
                wine_kind(h.e_type), interpreter);
        }
    }
    fclose(f);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (*env)->NewStringUTF(env, out);
}

/*
 * Safe no-exec memory-permission diagnostics. mmap/mprotect never executes the
 * mapped bytes, patches a Wine library, changes SELinux policy or elevates
 * permissions. The Android app and Wine child may have different seccomp
 * limits, so findings are strong indicators rather than definitive proof.
 */
static void append_map(char *out,size_t cap,const char *fmt,...) {
    size_t len=strlen(out);
    if(len>=cap-1)return;
    va_list args;va_start(args,fmt);
    vsnprintf(out+len,cap-len,fmt,args);
    va_end(args);
}

static void probe_file_map(char *out,size_t cap,int fd,size_t page,
                           int writable_first) {
    int prot=writable_first?(PROT_READ|PROT_WRITE):PROT_READ;
    void *p=mmap(NULL,page,prot,MAP_PRIVATE,fd,0);
    if(p==MAP_FAILED) {
        int error=errno;
        append_map(out,cap,"%s_MAP_ERR=%d (%s); ",
                   writable_first?"PRIVATE_RW":"PRIVATE_R",error,strerror(error));
        return;
    }
    if(writable_first) {
        /* Explicitly dirty a MAP_PRIVATE page; the file itself is untouched. */
        unsigned char *b=(unsigned char *)p;
        b[0]=b[0];
    }
    if(mprotect(p,page,PROT_READ|PROT_EXEC)<0) {
        int error=errno;
        append_map(out,cap,"%s_TO_RX_DENIED=%d (%s); ",
                   writable_first?"PRIVATE_RW":"PRIVATE_R",error,strerror(error));
    } else {
        append_map(out,cap,"%s_TO_RX_OK; ",writable_first?"PRIVATE_RW":"PRIVATE_R");
    }
    munmap(p,page);
}

JNIEXPORT jstring JNICALL
Java_com_drnas_console_NativeHost_nativeExecMappingProbe(JNIEnv *env,jclass clazz,
                                                          jstring jpath) {
    (void)clazz;
    char out[1024]={0};
    long raw_page=sysconf(_SC_PAGESIZE);
    if(raw_page<=0 || raw_page>65536) {
        return (*env)->NewStringUTF(env,"EXECMAP_INVALID_PAGE_SIZE");
    }
    size_t page=(size_t)raw_page;
    /* Anonymous RW -> RX is a control experiment, not executable code. */
    void *anon=mmap(NULL,page,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if(anon==MAP_FAILED) {
        append_map(out,sizeof(out),"ANON_RW_MAP_ERR=%d; ",errno);
    } else {
        if(mprotect(anon,page,PROT_READ|PROT_EXEC)<0)
            append_map(out,sizeof(out),"ANON_RW_TO_RX_DENIED=%d (%s); ",errno,strerror(errno));
        else append_map(out,sizeof(out),"ANON_RW_TO_RX_OK; ");
        munmap(anon,page);
    }
    if(!jpath) {
        append_map(out,sizeof(out),"FILE_PATH_NULL");
        return (*env)->NewStringUTF(env,out);
    }
    const char *path=(*env)->GetStringUTFChars(env,jpath,NULL);
    if(!path)return NULL;
    int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);
    if(fd<0) {
        append_map(out,sizeof(out),"FILE_OPEN_ERR=%d (%s)",errno,strerror(errno));
    } else {
        struct stat st;
        if(fstat(fd,&st)<0 || !S_ISREG(st.st_mode) || st.st_size<=0) {
            append_map(out,sizeof(out),"FILE_NOT_REGULAR_OR_EMPTY");
        } else {
            append_map(out,sizeof(out),"FILE_BYTES=%lld; ",(long long)st.st_size);
            probe_file_map(out,sizeof(out),fd,page,0);
            probe_file_map(out,sizeof(out),fd,page,1);
        }
        close(fd);
    }
    (*env)->ReleaseStringUTFChars(env,jpath,path);
    return (*env)->NewStringUTF(env,out);
}
