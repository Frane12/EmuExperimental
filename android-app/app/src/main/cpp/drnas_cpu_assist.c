#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

/*
 * CPU Assist v0.1: JNI-only CPU reference and Android RPC loader probe.
 *
 * IMPORTANT: This file does not execute anything on Hexagon/CDSP/NPU.
 * A successful dlopen of an RPC client library is NOT proof that a DSP
 * user protection domain can start or run a kernel.
 */
#define RUNS 21

static int64_t monotonic_ns(void) {
    struct timespec t;
    if (clock_gettime(CLOCK_MONOTONIC, &t) != 0) return -1;
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}

static int compare_i64(const void *a, const void *b) {
    const int64_t x = *(const int64_t *)a;
    const int64_t y = *(const int64_t *)b;
    return (x > y) - (x < y);
}

/* Small generic 7-tap signal filter: the same kernel can later be ported
 * to Hexagon HVX, enabling exact-output CPU/Hexagon comparison. */
__attribute__((noinline))
static void cpu_filter(const int16_t *in, int16_t *out, size_t count) {
    for (size_t i = 3; i + 3 < count; ++i) {
        int32_t sum = in[i-3] + 2*in[i-2] + 3*in[i-1] +
                      4*in[i] + 3*in[i+1] + 2*in[i+2] + in[i+3];
        out[i] = (int16_t)(sum / 16);
    }
}

JNIEXPORT jstring JNICALL
Java_com_drnas_console_NativeHost_nativeCpuAssistProbe(JNIEnv *env, jclass cls) {
    (void)cls;
    const char *libs[] = {"libcdsprpc.so", "libadsprpc.so"};
    char buf[512];
    int n = snprintf(buf, sizeof(buf),
        "Hexagon FastRPC client loader probe (NOT an NPU execution test): ");
    for (size_t i = 0; i < 2 && n > 0 && (size_t)n < sizeof(buf); ++i) {
        dlerror();
        void *handle = dlopen(libs[i], RTLD_NOW | RTLD_LOCAL);
        n += snprintf(buf + n, sizeof(buf) - (size_t)n, "%s=%s; ",
                      libs[i], handle ? "loadable" : "unavailable/inaccessible");
        if (handle) dlclose(handle);
    }
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_drnas_console_NativeHost_nativeCpuAssistBaseline(JNIEnv *env, jclass cls) {
    (void)cls;
    const size_t sizes[] = {4096, 65536, 262144};
    char result[900];
    int len = snprintf(result, sizeof(result),
        "CPU-only baseline (7-tap int16); NPU=NOT CONNECTED. "
        "Native monotonic clock; p50/p95 in microseconds.\n");
    if (len < 0 || (size_t)len >= sizeof(result)) {
        return (*env)->NewStringUTF(env, "Benchmark formatting failed");
    }
    for (size_t s = 0; s < 3; ++s) {
        const size_t count = sizes[s];
        int16_t *in = (int16_t *)malloc(count * sizeof(int16_t));
        int16_t *out = (int16_t *)calloc(count, sizeof(int16_t));
        if (!in || !out) {
            free(in); free(out);
            return (*env)->NewStringUTF(env, "CPU Assist: allocation failed");
        }
        uint32_t state = 0x52a17019u;
        for (size_t i = 0; i < count; ++i) {
            state = state * 1664525u + 1013904223u;
            in[i] = (int16_t)(state >> 16);
        }
        /* Warm up CPU/cache before collecting 21 runs. */
        for (int i = 0; i < 3; ++i) cpu_filter(in, out, count);
        int64_t samples[RUNS];
        uint64_t checksum = 0;
        int failed = 0;
        for (int i = 0; i < RUNS; ++i) {
            in[(size_t)i % count] ^= (int16_t)(i + 1);
            const int64_t before = monotonic_ns();
            cpu_filter(in, out, count);
            const int64_t after = monotonic_ns();
            if (before < 0 || after < before) { failed = 1; break; }
            samples[i] = after - before;
            checksum += (uint16_t)out[count/3] + (uint16_t)out[count/2];
        }
        free(in); free(out);
        if (failed) return (*env)->NewStringUTF(env, "CPU Assist: clock failed");
        qsort(samples, RUNS, sizeof(samples[0]), compare_i64);
        const double p50 = (double)samples[RUNS/2] / 1000.0;
        const double p95 = (double)samples[((RUNS*95+99)/100)-1] / 1000.0;
        int added = snprintf(result + len, sizeof(result) - (size_t)len,
            "%zu samples: p50=%.2f us, p95=%.2f us, check=%llu\n",
            count, p50, p95, (unsigned long long)checksum);
        if (added < 0 || (size_t)added >= sizeof(result) - (size_t)len)
            return (*env)->NewStringUTF(env, "CPU Assist: formatting overflow");
        len += added;
    }
    return (*env)->NewStringUTF(env, result);
}
