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
#include <inttypes.h>
#include <limits.h>

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
