import importlib.util
from pathlib import Path
import unittest

SCRIPT=Path(__file__).resolve().parents[1]/'android-app/app/src/main/assets/blender/drnas-window-layout.py'
spec=importlib.util.spec_from_file_location('layout',SCRIPT)
layout=importlib.util.module_from_spec(spec)
spec.loader.exec_module(layout)

def window(handle,title='Blender',rect=(-4,19,1288,724),owner=0,**extra):
    return dict(handle=handle,title=title,rect=rect,owner=owner,
                style=layout.FRAME,visible=True,iconic=False,**extra)

class WindowLayoutTest(unittest.TestCase):
    def test_primary_fills_both_display_profiles_without_a_frame(self):
        for desktop in [(1280,720),(960,544)]:
            calls=[]
            layout.LayoutController().update([window(1)],desktop,lambda *v:calls.append(v) or True)
            self.assertEqual(calls[0][2],(0,0,*desktop))
            self.assertFalse(calls[0][1]&layout.FRAME)

    def test_render_does_not_become_primary_and_retains_controls(self):
        calls=[]
        windows=[window(2,'Blender Render',rect=(0,0,3000,2000)),window(1)]
        state=layout.LayoutController()
        state.update(windows,(1280,720),lambda *v:calls.append(v) or True)
        self.assertEqual(state.main,1)
        render=next(v for v in calls if v[0]==2)
        self.assertEqual(render[1]&layout.FRAME,layout.FRAME)
        x,y,w,h=render[2]
        self.assertGreater(x,0)
        self.assertGreater(y,0)
        self.assertLess(x+w,1280)
        self.assertLess(y+h,720)

    def test_fitting_never_undoes_user_moves_or_minimization(self):
        calls=[]
        state=layout.LayoutController()
        windows=[window(1),window(2,'Blender Render')]
        apply=lambda *v:calls.append(v) or True
        state.update(windows,(1280,720),apply)
        windows[1]['rect']=(30,40,500,300)
        state.update(windows,(1280,720),apply)
        windows[1]['iconic']=True
        state.update(windows,(1280,720),apply)
        windows[1]['iconic']=False
        state.update(windows,(1280,720),apply)
        self.assertEqual(len(calls),2)

    def test_tooltips_and_small_popups_are_untouched(self):
        calls=[]
        state=layout.LayoutController()
        state.update([window(1),window(2,'',rect=(50,50,600,300)),
                      window(3,'Blender',rect=(0,0,100,40))],(960,544),
                     lambda *v:calls.append(v) or True)
        self.assertEqual([v[0] for v in calls],[1])

    def test_loading_a_project_refits_only_primary(self):
        calls=[]
        state=layout.LayoutController()
        windows=[window(1),window(2,'Blender Preferences')]
        apply=lambda *v:calls.append(v) or True
        state.update(windows,(1280,720),apply)
        state.project_loaded()
        state.update(windows,(1280,720),apply)
        self.assertEqual([v[0] for v in calls],[1,2,1])

    def test_closed_auxiliary_handle_can_be_reused(self):
        calls=[]
        state=layout.LayoutController()
        apply=lambda *v:calls.append(v) or True
        state.update([window(1),window(2,'Blender Render')],(960,544),apply)
        state.update([window(1)],(960,544),apply)
        state.update([window(1),window(2,'Blender Preferences')],(960,544),apply)
        self.assertEqual([v[0] for v in calls],[1,2,2])

if __name__=='__main__':
    unittest.main()
