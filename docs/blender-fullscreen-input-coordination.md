# Fullscreen/input coordination LAB

Based on performance input fix 9de8de2. No release, APK packaging or public app changes.

The prior overlay fix only changed keyboard focus. The guest layout timer could
still rewrite main-window styles and geometry after native fullscreen or while
another window was in use. Pointer events also followed a separate hit/grab path.

Changes:
- Fit the primary once at startup or after project load, only when foreground,
  no visible auxiliary GHOST window exists, and GetGUIThreadInfo reports no
  capture/menu/move/resize. API failure defers the fit. WS_POPUP is owned by GHOST.
  Later geometry drift is left alone; Android immersive/scaling is unchanged.
- First idle pointer press focuses its actual Blender top-level hit, using the
  same X11 stack as rendering. Foreign/unnamed menu targets are not activated.
- Overlay focus changes require no pointer/keyboard grab, held buttons or keys;
  key chords and drag ownership are preserved.
- Unmapping/deleting a grab window or ancestor releases that invalid grab and
  drops its queued events. Surviving grabs retain ownership. Parent-revert focus
  can fall back to a confirmed visible Blender sibling after Render closes.
- Render/Preferences titles cannot qualify as the primary.

Validation status: NOT EXECUTED. CI run 39460501215 failed before assigning a
runner (runner_id=0, no steps, no logs). The connector did not expose the failure
annotation, so the exact infrastructure reason is not confirmed. Local execution
was also unavailable. The dedicated workflow is configured for Python transition
tests, Android Java compilation and selected JVM regressions, without APK assembly.
Hardware acceptance is still required: open/close Render repeatedly; use Esc/F11;
toggle Blender fullscreen; open Preferences/file dialogs and menus; drag with
held LMB/RMB; zoom; load a project while Render is open. Check that visible window
and input agree and no held buttons or keys remain. Healthy explicit Wine grabs
are deliberately preserved; this change cannot prove an incorrectly retained
grab on a still-visible main window is fixed without device logs.
