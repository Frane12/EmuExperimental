#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define COBJMACROS
#define _WIN32_WINNT 0x0600
#include <windows.h>
#include <shellapi.h>
#include <shobjidl.h>
#include <objbase.h>
#include <stdio.h>
#include <wchar.h>
#include <wctype.h>

/*
 * DRNAS Link Launcher: resolve a genuine Wine/Windows .lnk through IShellLinkW,
 * preserve its stored arguments and working directory, and append the extra
 * tokens configured in the Android shortcut editor. Never run through cmd.exe.
 * This tool is used only when a .lnk has explicit arguments or Native Vulkan.
 * Pure C Win32 guest application, no injection or modification of user links.
 */
static void evidence(const char *message)
{
    FILE *out = _wfopen(L"C:\\DRNAS\\Tools\\LinkLaunch.log", L"a");
    if (out) { fprintf(out, "%lu %s\n", (unsigned long)GetTickCount(), message); fclose(out); }
}
static BOOL push(wchar_t *dst, size_t capacity, size_t *used, wchar_t ch)
{
    if (*used + 1 >= capacity) return FALSE;
    dst[(*used)++] = ch;
    dst[*used] = 0;
    return TRUE;
}
/* Windows command-line quoting rules: quote every argument, double trailing
 * backslashes, and protect a quote preceded by a run of backslashes. */
static BOOL append_arg(wchar_t *dst, size_t capacity, size_t *used, const wchar_t *arg)
{
    size_t slashes = 0;
    if (*used && !push(dst, capacity, used, L' ')) return FALSE;
    if (!push(dst, capacity, used, L'"')) return FALSE;
    for (const wchar_t *p = arg; *p; ++p) {
        if (*p == L'\\') { ++slashes; continue; }
        if (*p == L'"') {
            for (size_t i = 0; i < slashes * 2 + 1; ++i)
                if (!push(dst, capacity, used, L'\\')) return FALSE;
        } else {
            for (size_t i = 0; i < slashes; ++i)
                if (!push(dst, capacity, used, L'\\')) return FALSE;
        }
        slashes = 0;
        if (!push(dst, capacity, used, *p)) return FALSE;
    }
    for (size_t i = 0; i < slashes * 2; ++i)
        if (!push(dst, capacity, used, L'\\')) return FALSE;
    return push(dst, capacity, used, L'"');
}
static BOOL has_backend(const wchar_t *s)
{
    for (; *s; ++s) {
        if (s[0] == L'-' && s[1] == L'-') {
            const wchar_t *option = L"--gpu-backend";
            size_t j = 0;
            while (option[j] && s[j] && towlower(s[j]) == towlower(option[j])) ++j;
            if (!option[j] && (s[j] == 0 || iswspace(s[j]) || s[j] == L'='))
                return TRUE;
        }
    }
    return FALSE;
}
static BOOL blender_exe(const wchar_t *path)
{
    const wchar_t *base = path;
    for (const wchar_t *p = path; *p; ++p)
        if (*p == L'\\' || *p == L'/') base = p + 1;
    return _wcsicmp(base, L"blender.exe") == 0;
}
int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR command, int show)
{
    int argc = 0, rc = 1;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    IShellLinkW *link = NULL;
    IPersistFile *persist = NULL;
    wchar_t target[MAX_PATH] = {0}, original[2048] = {0}, work[MAX_PATH] = {0};
    wchar_t params[4096] = {0};
    size_t used = 0;
    HRESULT hr;
    BOOL com_ready = FALSE;
    (void)instance; (void)previous; (void)command; (void)show;
    if (!argv || argc < 3 || argc > 21 ||
        (wcscmp(argv[2], L"vulkan") && wcscmp(argv[2], L"auto"))) {
        evidence("LINK_BAD_ARGUMENTS");
        goto done;
    }
    if (wcslen(argv[1]) < 5 || _wcsicmp(argv[1] + wcslen(argv[1]) - 4, L".lnk")) {
        evidence("LINK_INVALID_EXTENSION");
        goto done;
    }
    hr = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    if (FAILED(hr)) { evidence("LINK_COM_INIT_FAILED"); goto done; }
    com_ready = TRUE;
    hr = CoCreateInstance(&CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER,
                          &IID_IShellLinkW, (void **)&link);
    if (FAILED(hr) || !link) { evidence("LINK_COM_SHELLLINK_FAILED"); goto done; }
    hr = IShellLinkW_QueryInterface(link, &IID_IPersistFile, (void **)&persist);
    if (FAILED(hr) || !persist || FAILED(IPersistFile_Load(persist, argv[1], STGM_READ))) {
        evidence("LINK_LOAD_FAILED");
        goto done;
    }
    if (FAILED(IShellLinkW_GetPath(link, target, MAX_PATH, NULL, SLGP_RAWPATH)) ||
        !target[0] || FAILED(IShellLinkW_GetArguments(link, original, 2048)) ||
        FAILED(IShellLinkW_GetWorkingDirectory(link, work, MAX_PATH))) {
        evidence("LINK_TARGET_READ_FAILED");
        goto done;
    }
    /* No silently ignored flags on a directory/document .lnk. */
    size_t target_len = wcslen(target);
    if (target_len < 4 || _wcsicmp(target + target_len - 4, L".exe") != 0) {
        evidence("LINK_NOT_AN_EXE");
        goto done;
    }
    if (wcslen(original) >= sizeof(params)/sizeof(params[0]) - 1) {
        evidence("LINK_STORED_ARGUMENTS_TOO_LONG");
        goto done;
    }
    wcscpy(params, original);
    used = wcslen(params);
    BOOL backend_chosen = has_backend(original);
    for (int i = 3; i < argc; ++i) {
        if (has_backend(argv[i])) backend_chosen = TRUE;
        if (!append_arg(params, sizeof(params)/sizeof(params[0]), &used, argv[i])) {
            evidence("LINK_ARGUMENTS_TOO_LONG");
            goto done;
        }
    }
    if (!wcscmp(argv[2], L"vulkan") && blender_exe(target) && !backend_chosen) {
        if (!append_arg(params, sizeof(params)/sizeof(params[0]), &used, L"--gpu-backend") ||
            !append_arg(params, sizeof(params)/sizeof(params[0]), &used, L"vulkan")) {
            evidence("LINK_BACKEND_ARGUMENTS_TOO_LONG");
            goto done;
        }
        evidence("LINK_BLENDER_VULKAN_AUTO");
    }
    SHELLEXECUTEINFOW launch = {0};
    launch.cbSize = sizeof(launch);
    launch.fMask = SEE_MASK_FLAG_NO_UI;
    launch.lpVerb = L"open";
    launch.lpFile = target;
    launch.lpParameters = params[0] ? params : NULL;
    launch.lpDirectory = work[0] ? work : NULL;
    launch.nShow = SW_SHOWNORMAL;
    if (!ShellExecuteExW(&launch)) {
        evidence("LINK_SHELLEXECUTE_FAILED");
        goto done;
    }
    evidence("LINK_EXECUTE_ACCEPTED_GPU_PRESENT_NOT_VERIFIED");
    rc = 0;
done:
    if (persist) IPersistFile_Release(persist);
    if (link) IShellLinkW_Release(link);
    if (com_ready) CoUninitialize();
    if (argv) LocalFree(argv);
    return rc;
}
