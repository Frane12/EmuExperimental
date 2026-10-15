# DRNAS EMU v0.5.27 — Android keyboard / Wine focus test

Source branch: `feat/ime-fallback-focus-v0.5.27` based on the CI-green v0.5.26 keyboard/game-log build.

## Changes
- Keep a visible **⌨** button in the Wine desktop even when the Wine window loses focus.
- Restore focus to X11 before requesting the Android IME; retry failed show requests up to three times (240 ms spacing).
- Provide **⋮ → Tekst → Wine (IME fallback)** as an EditText dialog. Only on **Pošalji** does it inject supported Android virtual-keyboard events into the focused X11 window. It does not log the entered text.
- Version as `0.5.27` with an isolated `labv0527` application ID when no persistent signing key is configured.

## Device A/B validation
1. Keep the confirmed v0.5.25/v0.5.26 installed. Install the v0.5.27 LAB build alongside it; do not uninstall or reset the tested Wine prefix.
2. With `explorer.exe` running, click a Wine text field. Tap **⌨**, type `hello123`, Enter and Backspace. Close and reopen the Android keyboard. Repeat after switching from winefile to a game window.
3. If the IME hides, use **⋮ → Tekst → Wine**, enter `hello123`, then tap **Pošalji**; verify the text reaches the focused Wine field.
4. Recheck touchpad-like relative pointer, Wine file manager, R: drive and audio. Confirm X11 remains 1280×720 when IME appears.
5. For a Crysis launch failure, tap **Pošalji log** in the desktop and export its game-log snapshot, Wine SEH trace and DXVK logs. Note time of game exit and compare with the previous version.

## Pass / reject
- **Pass:** Android IME opens or fallback text injection works, guest resolution stays fixed, input reaches intended guest window, and no new rendering/audio regression is observed.
- **Reject:** taskbar/desktop disappears, injected text goes to wrong window, stuck modifier keys, crash during keyboard focus changes, or a regression against v0.5.26.
- **Not claimed fixed:** Crysis exit, missing D3D adapter, actual Turnip WSI/DRI3/Present, or Wine taskmgr contents. Those still need device logs and targeted rendering work.

CI compilation success alone cannot prove Android IME or Wine game behavior; retain as experimental pending device tests.
