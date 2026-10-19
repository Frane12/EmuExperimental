"""Pure-policy regressions: only primary GHOST_WindowClass may be restyled."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'android-app/app/src/main/assets/blender/drnas-window-layout.py'
spec = importlib.util.spec_from_file_location('layout', SCRIPT)
layout = importlib.util.module_from_spec(spec)
spec.loader.exec_module(layout)


def window(handle, title='Blender', rect=(-4, 19, 1288, 724), owner=0, **updates):
    info = dict(handle=handle, title=title, rect=rect, owner=owner,
                style=layout.FRAME, visible=True, iconic=False)
    info.update(updates)
    return info


class PrimaryOnlyFullscreenTests(unittest.TestCase):
    def test_only_primary_becomes_borderless_fullscreen_for_both_profiles(self):
        for desktop in ((1280, 720), (960, 544)):
            calls = []
            w = window(1, rect=(-4, 0, desktop[0]+8, desktop[1]+8))
            layout.LayoutController().update([w], desktop,
                lambda *args: calls.append(args) or True)
            self.assertEqual(len(calls), 1)
            self.assertEqual(calls[0][0], 1)
            self.assertEqual(calls[0][2], (0, 0, *desktop))
            self.assertFalse(calls[0][1] & layout.FRAME)

    def test_secondary_windows_are_never_touched_even_if_larger_than_primary(self):
        calls = []
        windows = [window(1), window(2, 'Preferences', (10, 16, 664, 555)),
                   window(3, 'Blender Render', (0, 0, 3000, 2000)),
                   window(4, 'Blender', (2, 4, 1068, 627), owner=1)]
        state = layout.LayoutController()
        state.update(windows, (1280, 720), lambda *args: calls.append(args) or True)
        self.assertEqual(state.main, 1)
        self.assertEqual([call[0] for call in calls], [1])

    def test_existing_main_handle_is_unchanged_when_new_blender_window_opens(self):
        state = layout.LayoutController()
        calls = []
        send = lambda *args: calls.append(args) or True
        primary = window(1)
        state.update([primary], (1280,720), send)
        # New Render Window can have the generic title 'Blender' and be huge.
        state.update([primary, window(10, 'Blender', (0,0,1800,1200))],
                     (1280,720), send)
        self.assertEqual(state.main, 1)
        self.assertEqual([c[0] for c in calls], [1,1])

    def test_already_fitted_main_requires_no_second_win32_call(self):
        state=layout.LayoutController()
        calls=[]
        send=lambda *args: calls.append(args) or True
        state.update([window(1, style=0, rect=(0,0,1280,720))], (1280,720),send)
        state.update([window(1, style=0, rect=(0,0,1280,720))], (1280,720),send)
        self.assertEqual(len(calls),1)

    def test_project_reload_and_geometry_drift_are_repaired_only_on_primary(self):
        state=layout.LayoutController()
        calls=[]
        send=lambda *args: calls.append(args) or True
        main=window(1, rect=(0,0,1280,720), style=0)
        dialog=window(2,'Preferences',(15,15,600,400))
        state.update([main,dialog],(1280,720),send)
        state.update([main,dialog],(1280,720),send)
        main['rect']=(0,23,1280,697)
        state.update([main,dialog],(1280,720),send)
        state.project_loaded()
        main['rect']=(0,0,1280,720)
        state.update([main,dialog],(1280,720),send)
        self.assertEqual([c[0] for c in calls],[1,1,1])

    def test_no_primary_means_no_mutation(self):
        calls=[]
        state=layout.LayoutController()
        state.update([window(2,'Preferences'), window(3,'Default IME',(0,0,1,1)),
                      window(4,'Blender',(0,0,100,40))],
                      (960,544),lambda *args:calls.append(args) or True)
        self.assertEqual(calls,[])
        self.assertIsNone(state.main)

    def test_failures_can_retry_and_closed_main_reselects(self):
        state=layout.LayoutController()
        calls=[]
        state.update([window(1)],(1280,720),lambda *args:False)
        self.assertNotIn(1,state.applied)
        state.update([window(1)],(1280,720),lambda *args:calls.append(args) or True)
        self.assertIn(1,state.applied)
        state.update([window(3)],(1280,720),lambda *args:calls.append(args) or True)
        self.assertEqual([c[0] for c in calls],[1,3])

    def test_user_minimization_does_not_trigger_restoration(self):
        state=layout.LayoutController()
        calls=[]
        send=lambda *args:calls.append(args) or True
        state.update([window(1)],(1280,720),send)
        state.update([window(1, iconic=True)],(1280,720),send)
        self.assertEqual(len(calls),1)


if __name__ == '__main__':
    unittest.main()
