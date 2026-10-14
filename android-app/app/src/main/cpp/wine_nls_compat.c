#define _GNU_SOURCE
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <errno.h>
#include <limits.h>

/*
 * GameNative Wine 11 server/unicode.c:
 * get_nls_dir() uses realpath("/proc/self/exe") on Linux/Android to derive
 * share/wine/nls relative to the Wine server's original installed bin path.
 *
 * Our user-supplied Wine server was loaded as data by /system/bin/linker64.
 * In that process /proc/self/exe refers to the Android linker instead.
 *
 * The interposer changes exactly the one realpath query from a wineserver
 * process to the already-validated packaged Wine server path. All other
 * realpath calls delegate to Android Bionic unmodified. This does not grant
 * privileges or allow executing files from writable app storage.
 *
 * This experiment must only be LD_PRELOADed into the wineserver ProcessBuilder
 * child, never the DRNAS app globally or a Wine guest process.
 */
char *realpath(const char *pathname, char *resolved_path)
{
    typedef char *(*realpath_fn)(const char *,char *);
    static realpath_fn original = NULL;
    if (!original) original = (realpath_fn)dlsym(RTLD_NEXT, "realpath");
    if (pathname && strcmp(pathname, "/proc/self/exe") == 0) {
        const char *wine = getenv("DRNAS_WINESERVER_BIN");
        if (wine && wine[0] == '/' && strlen(wine) < PATH_MAX) {
            if (resolved_path) {
                /* realpath's supplied result buffer must be >= PATH_MAX. */
                strcpy(resolved_path,wine);
                return resolved_path;
            }
            return strdup(wine);
        }
    }
    if (!original) { errno=ENOSYS; return NULL; }
    return original(pathname,resolved_path);
}
