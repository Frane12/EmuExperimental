"""Fit only Blender's primary window; keep auxiliary window frames accessible.

Runs inside Windows ARM64 Blender. No hooks into rendering or scene data.
"""
import os

CAPTION = 0x00C00000
SYSMENU = 0x00080000
THICKFRAME = 0x00040000
MINIMIZEBOX = 0x00020000
MAXIMIZEBOX = 0x00010000
FRAME = CAPTION | SYSMENU | THICKFRAME | MINIMIZEBOX | MAXIMIZEBOX
WINDOW_STATE = 0x21000000  # WS_MINIMIZE | WS_MAXIMIZE
POPUP = 0x80000000


def primary_title(title):
    return title == 'Blender' or ' - Blender' in title


def auxiliary_rect(rect, desktop):
    sw, sh = desktop
    margin = max(4, min(16, sw // 40, sh // 40))
    x, y, width, height = rect
    # A full-size inherited render window needs space around its caption.
    width = max(1, min(width, int(sw * .90), sw - margin * 2))
    height = max(1, min(height, int(sh * .86), sh - margin * 2))
    return (max(margin, min(x, sw - width - margin)),
            max(margin, min(y, sh - height - margin)), width, height)


class LayoutController:
    def __init__(self):
        self.main = None
        self.applied = set()

    def project_loaded(self):
        # A .blend can restore the existing main window's saved geometry.
        self.applied.discard(self.main)

    def update(self, windows, desktop, apply):
        live = {w['handle'] for w in windows}
        self.applied.intersection_update(live)
        if self.main not in live:
            self.main = None
        visible = [w for w in windows if w['visible'] and not w['iconic']]
        if self.main is None:
            candidates = [w for w in visible if not w['owner'] and
                          primary_title(w['title']) and
                          w['rect'][2] >= 240 and w['rect'][3] >= 140]
            if not candidates:
                return
            self.main = max(candidates, key=lambda w: w['rect'][2] * w['rect'][3])['handle']
        for w in visible:
            handle = w['handle']
            if handle in self.applied:
                continue
            if handle == self.main:
                style = w['style'] & ~(FRAME | WINDOW_STATE | POPUP)
                rect = (0, 0, desktop[0], desktop[1])
                kind = 'primary'
            else:
                # Never restyle menu/tooltip windows or a nameless popup.
                if not w['title'] or w['rect'][2] < 240 or w['rect'][3] < 140:
                    continue
                style = (w['style'] & ~(POPUP | WINDOW_STATE)) | FRAME
                rect = auxiliary_rect(w['rect'], desktop)
                kind = 'auxiliary'
            if apply(handle, style, rect, kind):
                self.applied.add(handle)


def install():
    if os.name != 'nt':
        return
    import bpy
    import ctypes
    from ctypes import wintypes as wt
    user = ctypes.WinDLL('user32', use_last_error=True)
    callback_type = ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
    bindings = {
        'EnumWindows': ([callback_type, wt.LPARAM], wt.BOOL),
        'GetWindowThreadProcessId': ([wt.HWND, ctypes.POINTER(wt.DWORD)], wt.DWORD),
        'GetClassNameW': ([wt.HWND, wt.LPWSTR, ctypes.c_int], ctypes.c_int),
        'GetWindowTextW': ([wt.HWND, wt.LPWSTR, ctypes.c_int], ctypes.c_int),
        'GetWindowRect': ([wt.HWND, ctypes.POINTER(wt.RECT)], wt.BOOL),
        'GetWindow': ([wt.HWND, ctypes.c_uint], wt.HWND),
        'IsWindowVisible': ([wt.HWND], wt.BOOL),
        'IsIconic': ([wt.HWND], wt.BOOL),
        'IsZoomed': ([wt.HWND], wt.BOOL),
        'ShowWindow': ([wt.HWND, ctypes.c_int], wt.BOOL),
        'GetSystemMetrics': ([ctypes.c_int], ctypes.c_int),
        'GetWindowLongPtrW': ([wt.HWND, ctypes.c_int], ctypes.c_ssize_t),
        'SetWindowLongPtrW': ([wt.HWND, ctypes.c_int, ctypes.c_ssize_t], ctypes.c_ssize_t),
        'SetWindowPos': ([wt.HWND, wt.HWND, ctypes.c_int, ctypes.c_int,
                          ctypes.c_int, ctypes.c_int, ctypes.c_uint], wt.BOOL),
    }
    for name, (args, result) in bindings.items():
        fn = getattr(user, name)
        fn.argtypes, fn.restype = args, result
    controller = LayoutController()
    pid = os.getpid()

    def snapshot():
        windows = []

        @callback_type
        def visit(handle, unused):
            process = wt.DWORD()
            user.GetWindowThreadProcessId(handle, ctypes.byref(process))
            if process.value != pid:
                return True
            name = ctypes.create_unicode_buffer(128)
            user.GetClassNameW(handle, name, len(name))
            if name.value != 'GHOST_WindowClass':
                return True
            title = ctypes.create_unicode_buffer(1024)
            user.GetWindowTextW(handle, title, len(title))
            rect = wt.RECT()
            if user.GetWindowRect(handle, ctypes.byref(rect)):
                windows.append(dict(handle=handle, title=title.value,
                    owner=user.GetWindow(handle, 4),
                    style=user.GetWindowLongPtrW(handle, -16) & 0xffffffff,
                    visible=bool(user.IsWindowVisible(handle)),
                    iconic=bool(user.IsIconic(handle)),
                    rect=(rect.left, rect.top, rect.right-rect.left, rect.bottom-rect.top)))
            return True

        if not user.EnumWindows(visit, 0):
            raise ctypes.WinError(ctypes.get_last_error())
        return windows

    def apply(handle, style, rect, kind):
        if user.IsZoomed(handle):
            user.ShowWindow(handle, 9)  # SW_RESTORE, only on first encounter.
        ctypes.set_last_error(0)
        user.SetWindowLongPtrW(handle, -16, style)
        if ctypes.get_last_error():
            raise ctypes.WinError(ctypes.get_last_error())
        # Recalculate non-client borders without changing focus, owner or Z order.
        if not user.SetWindowPos(handle, None, *rect, 0x0234):
            raise ctypes.WinError(ctypes.get_last_error())
        print('DRNAS_BLENDER_WINDOW_LAYOUT', kind, handle, rect, flush=True)
        return True

    last_error = [None]

    def poll():
        try:
            desktop = (user.GetSystemMetrics(0), user.GetSystemMetrics(1))
            if min(desktop) > 0:
                controller.update(snapshot(), desktop, apply)
        except Exception as error:
            message = str(error)
            if message != last_error[0]:
                print('DRNAS_BLENDER_WINDOW_LAYOUT_FAILED', message, flush=True)
                last_error[0] = message
        return 0.5

    @bpy.app.handlers.persistent
    def project_loaded(unused):
        controller.project_loaded()

    bpy.app.handlers.load_post.append(project_loaded)
    bpy.app.timers.register(poll, first_interval=0.1, persistent=True)


if __name__ == '__main__':
    install()
