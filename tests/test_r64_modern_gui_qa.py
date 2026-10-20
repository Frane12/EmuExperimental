"""Static regressions for isolated R64 modern EmuDRNAS GUI edition.

These checks intentionally do not alter the working r64 Vulkan/WinARM64
runtime, only Android-side presentation, controls and diagnostic visibility.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android-app/app/src/main/java/com/drnas/console"

def text(filename):
    return (JAVA / filename).read_text(encoding="utf-8")


class ModernR64Gui(unittest.TestCase):
    def test_spinner_shared_across_both_starts(self):
        anim = text("StartupAnimationView.java")
        gui = text("WineGuiActivity.java")
        launch = text("BlenderLauncherActivity.java")
        self.assertIn("static android.widget.LinearLayout blenderPanel", anim)
        self.assertIn("Math.sin(angle)", anim)
        self.assertIn("StartupAnimationView.blenderPanel(this,status)", launch)
        self.assertIn("StartupAnimationView.blenderPanel(this,startupStatus)", gui)
        self.assertNotIn("blenderStartupPanel.addView(blenderStartupBar", gui)

    def test_no_r64_diagnostic_chooser_and_guaranteed_working_vulkan_path(self):
        launch = text("BlenderLauncherActivity.java")
        gui = text("WineGuiActivity.java")
        self.assertIn('r64LabMode="vulkan"', launch)
        self.assertNotIn("if(isR64Lab() && !labChoiceMade) showR64LabChoice()", launch)
        self.assertIn("session.setR64BlenderLabMode", gui)
        self.assertNotIn("verboseLogs || r64BlenderVulkan", gui)
        menu = gui.split("private void showBlenderMenu() {", 1)[1].split(
            "private void toggleBlenderHostFullscreen()", 1)[0]
        for hidden in ("Pošalji log", "Test performansi", "FPS brojač", "Postavke i dijagnostika"):
            self.assertNotIn(hidden, menu)

    def test_mouse_controls_keep_true_press_release_and_follow_movable_zoom(self):
        keys = text("BlenderKeyPanel.java")
        zoom = text("BlenderZoomPanel.java")
        gui = text("WineGuiActivity.java")
        self.assertIn('mouseButton("LMB"', keys)
        self.assertIn('mouseButton("RMB"', keys)
        self.assertIn("mouseHold.release(x11Button)", keys)
        self.assertIn("void moveMouseButtonsTo(LinearLayout destination)", keys)
        self.assertIn("blenderKeys.moveMouseButtonsTo(blenderZoom)", gui)
        self.assertIn("Drag to move mouse buttons and zoom", zoom)
        self.assertIn('putFloat("position_x"', zoom)
        self.assertIn("smooth.sendDelta(delta)", zoom)
        self.assertIn("this::returnFromBlenderRender", gui)

    def test_separate_install_keeps_proven_r64_build(self):
        build = (ROOT / "android-app/app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("DRNAS_MALI_R64_GUI_LAB", build)
        self.assertIn('applicationIdSuffix = ".mali.r64.gui.lab"', build)
        self.assertIn('applicationIdSuffix = ".mali.r64.lab"', build)
        self.assertIn('com.drnas.console.mali.r64.gui.lab', text("BlenderLauncherActivity.java"))
        self.assertIn('com.drnas.console.mali.r64.gui.lab', text("WineGuiActivity.java"))


if __name__ == "__main__":
    unittest.main()
