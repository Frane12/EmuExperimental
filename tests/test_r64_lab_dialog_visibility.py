"""Regression guard for Android AlertDialog R64 test selector.

Android AlertController prioritizes setMessage's message panel over the
setItems list view. Never configure both on this R64 test-selection dialog.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "android-app/app/src/main/java/com/drnas/console/BlenderLauncherActivity.java"

def dialog_source():
    text = LAUNCHER.read_text(encoding="utf-8")
    return text.split("private void showR64LabChoice() {", 1)[1].split(
        "private boolean waitingExternal;", 1
    )[0]

class R64LabSelectorTests(unittest.TestCase):
    def test_selection_items_cannot_be_hidden_by_message(self):
        code = dialog_source()
        self.assertIn(".setItems(new CharSequence[]{", code)
        self.assertNotIn(".setMessage(", code)
        self.assertIn('.setNeutralButton("Zatvori"', code)

    def test_all_three_test_modes_remain_selectable(self):
        code = dialog_source()
        for mode in ('"winecfg"', '"background"', '"vulkan"'):
            self.assertIn(mode, code)
        for label in ("TEST WINECFG", "TEST BLENDER BACKGROUND",
                      "TEST BLENDER VULKAN"):
            self.assertIn(label, code)

if __name__ == "__main__":
    unittest.main()
