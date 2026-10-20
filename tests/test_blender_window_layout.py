"""Regression scenarios for fullscreen ownership during real window transitions."""
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


class FullscreenOwnershipTests(unittest.TestCase):
    def setUp(self):
        self.state = layout.LayoutController()
        self.calls = []
        self.main = window(1)

    def update(self, windows=None, foreground=1, interacting=False, desktop=(1280,720)):
        self.state.update(windows if windows is not None else [self.main],
            desktop, lambda *args: self.calls.append(args) or True,
            foreground=foreground, interacting=interacting)

    def test_startup_fits_only_main_for_both_profiles(self):
        for desktop in ((1280,720), (960,544)):
            self.setUp()
            self.main['rect'] = (-4,0,desktop[0]+8,desktop[1]+8)
            self.update(desktop=desktop)
            self.assertEqual(len(self.calls),1)
            self.assertEqual(self.calls[0][0],1)
            self.assertEqual(self.calls[0][2],(0,0,*desktop))
            self.assertFalse(self.calls[0][1] & layout.FRAME)

    def test_no_focus_or_other_app_defers_startup(self):
        self.update(foreground=None)
        self.update(foreground=99)
        self.assertEqual(self.calls,[])
        self.update()
        self.assertEqual(len(self.calls),1)

    def test_visible_render_defers_fit_even_if_main_is_foreground(self):
        render=window(2,'Render Result - Blender 4.5 LTS',(10,10,900,600),owner=1)
        self.update([self.main,render])
        self.update([self.main,render],foreground=2)
        self.assertEqual(self.calls,[])
        self.assertEqual(self.state.main,1)
        self.update()
        self.assertEqual([c[0] for c in self.calls],[1])

    def test_unowned_generic_auxiliary_also_defers_fit(self):
        self.update([self.main,window(2,'Blender',(10,10,800,500))])
        self.assertEqual(self.calls,[])
        self.update()
        self.assertEqual(len(self.calls),1)

    def test_hidden_and_minimized_auxiliary_does_not_block(self):
        self.update([self.main,window(2,visible=False),window(3,iconic=True)])
        self.assertEqual(len(self.calls),1)

    def test_menu_capture_move_and_resize_defer_fit(self):
        self.update(interacting=True)
        self.assertEqual(self.calls,[])
        self.update()
        self.assertEqual(len(self.calls),1)

    def test_drift_after_initial_fit_belongs_to_blender(self):
        self.update()
        self.main['rect']=(0,23,1280,697)
        self.main['style']=layout.FRAME
        self.update()
        self.update()
        self.assertEqual(len(self.calls),1)

    def test_native_fullscreen_is_never_restyled(self):
        self.main['style'] |= layout.POPUP
        self.update()
        self.assertEqual(self.calls,[])
        self.main['style'] &= ~layout.POPUP
        self.update()
        self.assertEqual(len(self.calls),1)

    def test_reload_remains_pending_until_render_closes_and_main_activates(self):
        self.update()
        self.state.project_loaded()
        render=window(2,'Render Result - Blender 4.5 LTS',(10,10,900,600))
        self.update([self.main,render],foreground=2)
        self.update(foreground=99)
        self.assertEqual(len(self.calls),1)
        self.update()
        self.update()
        self.assertEqual([c[0] for c in self.calls],[1,1])

    def test_minimized_main_is_not_restored(self):
        self.main['iconic']=True
        self.update()
        self.assertEqual(self.calls,[])

    def test_render_title_never_establishes_a_main(self):
        self.update([window(2,'Render Result - Blender 4.5 LTS')],foreground=2)
        self.assertIsNone(self.state.main)
        self.assertEqual(self.calls,[])

    def test_failed_fit_retries_and_closed_main_can_be_replaced(self):
        self.state.update([self.main],(1280,720),lambda *args:False,foreground=1)
        self.assertNotIn(1,self.state.applied)
        self.update()
        self.update([window(3)],foreground=3)
        self.assertEqual([c[0] for c in self.calls],[1,3])


if __name__ == '__main__':
    unittest.main()
