# Architectural contract — no more spaghetti

## Components

```text
app-android (UI only)
  -> profile-store (schema+versioned migrations)
  -> capability-resolver (GPU actual ID, Android version, available runtimes)
  -> session-manager (one owner of lifecycle, threading and resources)
    -> RuntimeAdapter (Wine/Proton path; FEXCore/Box64)
    -> GraphicsAdapter (DXVK/VKD3D -> Vulkan loader -> installed Turnip)
    -> AudioAdapter (Wine pulse/ALSA bridge -> Android AAudio or equivalent)
    -> InputRouter (per-pointer ownership, stable key state)
  -> metrics (timestamped trace, crash classification, reproducible export)
```

### Pure boundaries
- UI may only send `CreateSession(profileId)`, `StopSession(id)`, `ApplyProfileRevision` and observe state.
- UI does not call Wine launch directly, write GPU flags or send raw keyboard events to unmanaged threads.
- A module reports capabilities. Orchestrator rejects invalid cross-component combinations before preparing any runtime.
- Runtime owns processes; graphics owns driver selection; input owns pointer ID routing; audio owns output strategy.
- Cache boundaries: `game + device-uuid + GPU-chip + driver-hash + Wine-build + DXVK-build + chosen config`.
- All module package manifests must contain source, version, ABI, SHA-256, install timestamp and trust level.
- Crash recovery restores last validated profile and preserves reproducible logs.

## Session state machine

`IDLE -> VALIDATING -> PREPARING -> STARTING -> RUNNING -> STOPPING -> IDLE`

Failures in preparing/starting/running transition to `FAILED`, collect logs and permit rollback to `IDLE`. Android lifecycle pause/resume must not cause duplicate process launches.

## Input guarantees
- On ACTION_DOWN, resolve exactly one owner by hit-test priority: button/joystick, else relative touchpad.
- Map Android `pointerId` to owner; never reroute the same pointer on MOVE.
- Process ACTION_POINTER_UP and ACTION_CANCEL per ID; release stuck buttons.
- Multi-touch test: left stick held while right finger starts in empty area and moves camera; camera touch never drags the left stick.
- Overlay transparency changes visual opacity only, **not** the hit-test ownership.
- Controller layout must have an explicit "pass through empty space" mode.

## Testing before real game use
- Property tests for session transition ordering, double-stop/start, cancellation, invalid manifests and rollback.
- Input tests for two simultaneous pointers, second pointer over button, interrupted gesture, orientation switch, three-finger Android gesture.
- Graphics tests for Vulkan loader init, driver manifest, shader cache segregation, GMEM/DEPTH correctness, clean crash recovery.
- Audio tests for channel mapping, underflow counter, latency and device suspend/resume.
- Integration smoke test must use a simple executable before Crysis/Far Cry.

No automatic uploading of private game logs, executables or device-identifying data.
