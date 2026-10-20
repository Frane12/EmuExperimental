"""Borderless fullscreen for the *first primary* Blender HWND only.

Keep Wine's r50 native frames for Preferences, Render, file dialogs, menus and
tooltips. Do not resize, restyle, raise, minimize or focus any auxiliary window.
This is a lightweight guest-side Win32 policy, independent of Vulkan and X11
pixel transport. Fit once at startup or after a .blend reload, only when the\nmain is foreground and no auxiliary window, native fullscreen or interaction is active.
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
    if title == 'Blender':
        return True
    # Secondary Render Result and Preferences can carry a Blender suffix.
    if title.startswith(('Render Result - Blender', 'Render - Blender',
                         'Preferences - Blender', 'Blender Preferences')):
        return False
    return ' - Blender' in title


class LayoutController:
    def __init__(self):
        self.main = None
        self.applied = set()

    def project_loaded(self):
        # A .blend may restore the already selected main HWND's geometry.
        self.applied.discard(self.main)

    def update(self, windows, desktop, apply, foreground=None, interacting=False):
        if min(desktop) <= 0:
            return
        live = {w['handle'] for w in windows}
        self.applied.intersection_update(live)
        if self.main not in live:
            self.main = None
        visible = [w for w in windows if w['visible'] and not w['iconic']]
        if self.main is None:
            candidates = [w for w in visible if not w['owner'] and
                          primary_title(w['title']) and
                          # At startup only a desktop-sized GHOST top-level
                          # can be the primary; Preferences may be titled Blender.
                          desktop[0] * .9 <= w['rect'][2] <= desktop[0] * 1.2 and
                          desktop[1] * .9 <= w['rect'][3] <= desktop[1] * 1.2]
            if not candidates:
                return
            self.main = max(candidates, key=lambda w: w['rect'][2] * w['rect'][3])['handle']
        main_window = next((w for w in visible if w['handle'] == self.main), None)
        if main_window is None:
            return
        # Never compete with GHOST fullscreen, modal windows, menus or drags.
        # Applied means ownership was handed back to Blender. Geometry drift is
        # intentional until a project load explicitly requests another fit.
        if self.main in self.applied:
            return
        if foreground != self.main or interacting:
            return
        if any(w['handle'] != self.main for w in visible):
            return
        # Blender's native fullscreen uses WS_POPUP. Let GHOST own that state.
        if main_window['style'] & POPUP:
            return
        rect = (0, 0, desktop[0], desktop[1])
        style = main_window['style'] & ~(FRAME | WINDOW_STATE)
        if apply(self.main, style, rect, 'primary'):
            self.applied.add(self.main)


def install():
    if os.name != 'nt':
        return
    import bpy
    import ctypes
    from ctypes import wintypes as wt
    user = ctypes.WinDLL('user32', use_last_error=True)
    class GUIThreadInfo(ctypes.Structure):
        _fields_ = [('cbSize', wt.DWORD), ('flags', wt.DWORD),
                    ('hwndActive', wt.HWND), ('hwndFocus', wt.HWND),
                    ('hwndCapture', wt.HWND), ('hwndMenuOwner', wt.HWND),
                    ('hwndMoveSize', wt.HWND), ('hwndCaret', wt.HWND),
                    ('rcCaret', wt.RECT)]

    callback_type = ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
    bindings = {
        'GetForegroundWindow': ([], wt.HWND),
        'GetGUIThreadInfo': ([wt.DWORD, ctypes.POINTER(GUIThreadInfo)], wt.BOOL),
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
        previous = user.SetWindowLongPtrW(handle, -16, style)
        if previous == 0 and ctypes.get_last_error():
            raise ctypes.WinError(ctypes.get_last_error())
        # Recalculate non-client borders without changing focus, owner or Z order.
        if not user.SetWindowPos(handle, None, *rect, 0x0234):
            raise ctypes.WinError(ctypes.get_last_error())
        print('DRNAS_BLENDER_PRIMARY_FULLSCREEN', kind, handle, rect, flush=True)
        return True

    last_error = [None]

    def poll():
        try:
            desktop = (user.GetSystemMetrics(0), user.GetSystemMetrics(1))
            if min(desktop) > 0:
                info = GUIThreadInfo()
                info.cbSize = ctypes.sizeof(info)
                # Failure is treated as busy: no blind geometry/focus mutation.
                available = user.GetGUIThreadInfo(0, ctypes.byref(info))
                busy = not available or bool(info.flags & 0x1e or info.hwndCapture)
                controller.update(snapshot(), desktop, apply,
                                  foreground=user.GetForegroundWindow(),
                                  interacting=busy)
        except Exception as error:
            message = str(error)
            if message != last_error[0]:
                print('DRNAS_BLENDER_WINDOW_LAYOUT_FAILED', message, flush=True)
                last_error[0] = message
        return 2.0 if controller.main in controller.applied else 0.5

    @bpy.app.handlers.persistent
    def project_loaded(unused):
        controller.project_loaded()

    bpy.app.handlers.load_post.append(project_loaded)
    bpy.app.timers.register(poll, first_interval=0.1, persistent=True)


if __name__ == '__main__':
    install()
