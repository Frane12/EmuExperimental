#define UNICODE
#define _UNICODE
#define _WIN32_WINNT 0x0600
#include <windows.h>
#include <math.h>
#include <stdio.h>
#include <wchar.h>

/* Own Win32 desktop companion. No visible/input window, DLL injection or app hooks.
 * PaintDesktop remains Explorer's job, including its launchers. Sparse pollen is
 * painted only into the desktop DC, with children clipped, after Explorer paints.
 */
static HWND desktop, tray;
static BOOL live = TRUE, large = TRUE;
static RECT previous_work;
static BOOL changed_work;
static DWORD started;
static const wchar_t *art = L"C:\\users\\xuser\\Pictures\\Drnas-Desktop-Meadow.bmp";

static void evidence(const char *line)
{
    FILE *out = _wfopen(L"C:\\DRNAS\\Tools\\DesktopStyle.log", L"a");
    if (out) { fprintf(out, "%lu %s\n", (unsigned long)GetTickCount(), line); fclose(out); }
}
static BOOL CALLBACK resize_button(HWND child, LPARAM value)
{
    wchar_t name[32];
    RECT rect;
    if (GetClassNameW(child, name, 32) && !lstrcmpiW(name, L"Button") &&
        GetClientRect(child, &rect) && rect.bottom != (LONG)value)
        SetWindowPos(child, NULL, 0, 0, rect.right, (int)value,
                     SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
    return TRUE;
}
static void size_taskbar(void)
{
    RECT rect;
    int w = GetSystemMetrics(SM_CXSCREEN), h = GetSystemMetrics(SM_CYSCREEN);
    if (!large) return;
    tray = FindWindowW(L"Shell_TrayWnd", NULL);
    if (!tray || !GetWindowRect(tray, &rect)) return;
    if (rect.bottom - rect.top != 44 || rect.top != h - 44 || rect.right - rect.left != w) {
        SetWindowPos(tray, NULL, 0, h - 44, w, 44, SWP_NOZORDER | SWP_NOACTIVATE);
        evidence("TASKBAR_REQUEST height=44 guest_pixels; click rectangles enlarged");
    }
    EnumChildWindows(tray, resize_button, 44);
    if (!changed_work) {
        RECT work = {0, 0, w, h - 44};
        if (SystemParametersInfoW(SPI_GETWORKAREA, 0, &previous_work, 0) &&
            SystemParametersInfoW(SPI_SETWORKAREA, 0, &work, 0))
            changed_work = TRUE;
    }
}
static BOOL covered(void)
{
    HWND foreground = GetForegroundWindow();
    RECT rect;
    if (!foreground || foreground == desktop || foreground == tray || IsIconic(foreground))
        return FALSE;
    if (!GetWindowRect(foreground, &rect)) return TRUE;
    return rect.right - rect.left >= GetSystemMetrics(SM_CXSCREEN) * 9 / 10 &&
           rect.bottom - rect.top >= (GetSystemMetrics(SM_CYSCREEN) - (large ? 44 : 24)) * 9 / 10;
}
static void animate(void)
{
    DWORD_PTR result;
    HDC dc;
    int w = GetSystemMetrics(SM_CXSCREEN), h = GetSystemMetrics(SM_CYSCREEN);
    double t = (GetTickCount() - started) / 1000.0;
    /* A timeout affects this low-priority helper only, never waits on Android UI. */
    if (!InvalidateRect(desktop, NULL, TRUE) ||
        !SendMessageTimeoutW(desktop, WM_PAINT, 0, 0,
            SMTO_ABORTIFHUNG | SMTO_BLOCK, 80, &result)) return;
    dc = GetDCEx(desktop, NULL, DCX_CACHE | DCX_CLIPCHILDREN | DCX_CLIPSIBLINGS);
    if (!dc) return;
    for (int i = 0; i < 20; i++) {
        /* Keep the usual left desktop-icon columns free of decorative dots. */
        int x = 220 + (int)fmod(i * 73.1 + t * (5 + i % 4), w > 240 ? w - 220 : 1);
        int y = (int)(h * .22 + fmod(i * 37.7 + sin(t * .45 + i) * 6 + h, h * .62));
        COLORREF color = i % 3 ? RGB(244, 244, 192) : RGB(176, 240, 207);
        SetPixelV(dc, x, y, color);
        if (i % 4 == 0) SetPixelV(dc, x + 1, y, RGB(216, 236, 187));
    }
    ReleaseDC(desktop, dc);
}
static LRESULT CALLBACK helper_proc(HWND window, UINT message, WPARAM wp, LPARAM lp)
{
    if (message == WM_TIMER) {
        if (wp == 2) size_taskbar();
        else if (wp == 1 && live) {
            BOOL paused = covered();
            SetTimer(window, 1, paused ? 500 : 125, NULL);
            if (!paused) animate();
        }
        return 0;
    }
    if (message == WM_DESTROY) {
        if (changed_work) SystemParametersInfoW(SPI_SETWORKAREA, 0, &previous_work, 0);
        PostQuitMessage(0); return 0;
    }
    return DefWindowProcW(window, message, wp, lp);
}
int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR command, int show)
{
    WNDCLASSW cls = {0};
    MSG message;
    HWND window;
    HANDLE mutex;
    (void)previous; (void)show;
    mutex = CreateMutexW(NULL, FALSE, L"Local\\DRNAS-Desktop-Meadow-v1");
    if (!mutex || GetLastError() == ERROR_ALREADY_EXISTS) {
        if (mutex) CloseHandle(mutex);
        return 0;
    }
    live = !wcsstr(command, L"/live:0");
    large = !wcsstr(command, L"/taskbar:0");
    started = GetTickCount();
    desktop = GetDesktopWindow();
    SetPriorityClass(GetCurrentProcess(), IDLE_PRIORITY_CLASS);
    evidence("START desktop companion; no visible window and no input interception");
    if (SystemParametersInfoW(SPI_SETDESKWALLPAPER, 0, (void *)art, SPIF_SENDCHANGE)) {
        evidence("WALLPAPER_API_SUCCESS bitmap accepted; visual confirmation requires device");
        InvalidateRect(desktop, NULL, TRUE);
    } else evidence("WALLPAPER_API_FAILED");
    cls.hInstance = instance; cls.lpfnWndProc = helper_proc;
    cls.lpszClassName = L"DRNASDesktopCompanion";
    if (!RegisterClassW(&cls)) { CloseHandle(mutex); return 1; }
    window = CreateWindowExW(0, cls.lpszClassName, L"", 0, 0, 0, 0, 0,
                             HWND_MESSAGE, NULL, instance, NULL);
    if (!window) { CloseHandle(mutex); return 2; }
    size_taskbar();
    SetTimer(window, 2, 1000, NULL);
    if (live) SetTimer(window, 1, 125, NULL);
    while (GetMessageW(&message, NULL, 0, 0) > 0) {
        TranslateMessage(&message); DispatchMessageW(&message);
    }
    CloseHandle(mutex);
    return 0;
}
