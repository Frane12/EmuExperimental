#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <limits.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/*
 * Scoped Android/Bionic bridge for GameNative Wine 11's child PE loader.
 *
 * Upstream dlls/ntdll/unix/loader.c -> preloader_exec():
 *     execv(wineloader, argv+1)
 * Android 16 denies exec of Wine ELFs stored in writable app-private files.
 * We launch Wine's already verified Bionic AArch64 ELF through the platform's
 * system linker64, preserving all of Wine's original child arguments, fd
 * inheritance and WINELOADERNOEXEC second-stage environment.
 *
 * Only the EXACT canonical Wine entrypoint given by the app via environment
 * is eligible. All unrelated execv calls delegate to Bionic untouched.
 * This library is LD_PRELOADed ONLY into disposable Wineboot test process.
 */
typedef int (*execv_func)(const char *, char *const []);

int execv(const char *path, char *const argv[])
{
    static execv_func real_execv = NULL;
    if (!real_execv) real_execv = (execv_func)dlsym(RTLD_NEXT, "execv");
    if (!real_execv) { errno=ENOSYS; return -1; }

    const char *approved = getenv("DRNAS_WINE_CHILD_EXE");
    if (!path || !approved || approved[0] != '/' ||
        strlen(approved) >= PATH_MAX || strcmp(path, approved) != 0)
        return real_execv(path, argv);

    /* Never reinterpret an arbitrary executable/command through linker64. */
    if (!argv || !argv[0]) { errno=EINVAL; return -1; }
    size_t n=0;
    for (; n<127 && argv[n]; ++n) {}
    if (n>=127) { errno=E2BIG; return -1; }

    char **args=calloc(n+2,sizeof(char*));
    if (!args) {errno=ENOMEM;return -1;}
    args[0]=(char*)"/system/bin/linker64";
    args[1]=(char*)approved;
    for(size_t i=1;i<n;i++)args[i+1]=argv[i];
    args[n+1]=NULL;

    static const char begin[]="[DRNAS child] execv Wine -> Android linker64\n";
    (void)write(STDERR_FILENO,begin,sizeof(begin)-1);
    int rc=real_execv("/system/bin/linker64",args);
    int saved=errno;
    static const char failed[]="[DRNAS child] linker64 execv failed\n";
    (void)write(STDERR_FILENO,failed,sizeof(failed)-1);
    free(args);
    errno=saved;
    return rc;
}
