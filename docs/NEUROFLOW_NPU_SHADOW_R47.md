# DRNAS NeuroFlow NPU Assistant — private r47 experiment

**Status: shadow telemetry + CPU heuristic + NNAPI device-inventory probe only. NOT NPU inference.**

Branch: `experiment/neuroflow-npu-shadow-r47`. Keep private. All builds are **draft**, never publicly published or distributed without explicit device tests and approval.

## Why this first stage

We need baseline evidence about what is actually slow before implementing a hardware-specific NPU delegate. DRNAS presents Blender through Wine/Proton, a Vulkan driver and an Android/X11 display bridge; host UI timing can differ dramatically from Blender's own render time. There is currently no trustworthy in-app DXVK or Blender GPU timestamp series to train a render predictor from.

This stage does not inject NPU work into Blender, share resources with Vulkan, or change any GPU / WSI / render-pass configuration.

## What is implemented

- `NeuroFlowPredictor.java`: deterministic 16-sample rolling mean, bounded trend projection and prediction-error EWMA for *Android/X11 displayed frame intervals*. Explicitly described as a **CPU heuristic**, not machine learning.
- `NeuroFlowShadow.java`: separate minimum-priority Java worker wakes every 2 seconds. UI thread only posts immutable sample pointer every ~650 ms, which can be overwritten if inference is behind (latest-wins). Logs at most every 15 seconds; no file IO from render thread.
- `NeuroFlowHardwareProbe.java` / `neuroflow_npu_probe.c`: informative NNAPI accelerator-device enumeration using runtime-loaded `libneuralnetworks.so`. NNAPI is deprecated; an empty list **does not** establish that a Qualcomm Hexagon or MediaTek APU is absent. Enumeration **does not** establish QNN, NeuroPilot or LiteRT compatibility.
- Blender menu: `NeuroFlow NPU Shadow (experimental)` toggle, persisted in private preferences. In the private experiment the sampler defaults to ON for Blender only, OFF outside Blender.
- `DRNAS_NPU_SHADOW_START`, `DRNAS_NPU_SHADOW_HARDWARE`, `DRNAS_NPU_SHADOW_DISPLAY`, `DRNAS_NPU_SHADOW_SWITCH`, `DRNAS_NPU_SHADOW_ERROR` are essential diagnostic markers. No project names or paths are logged by this sampler.
- Isolated JVM tests exercise invalid inputs, warmup, stable display and pressure detection.

**The app does not yet execute ANY neural model on the NPU.** No NPU backend, proprietary vendor binaries, model files or promises of speedups have been added.

## Manual A/B test on device

1. Install a signed private r47 test APK over the existing stable-certificate DRNAS install (versionCode must increase, no uninstall).
2. Open Blender and let it start normally. The 92% startup fix, fullscreen controls, folder mapping and previous drivers must not regress.
3. Open Blender menu, toggle **NeuroFlow NPU Shadow** ON. Capture 20 seconds of performance and share the diagnostic log.
4. Repeat the same scene, same device temperature, same camera movement with the switch OFF.
5. Inspect `DRNAS_NPU_SHADOW_HARDWARE`: `NNAPI devices` is only a capability hint, `npu_inference=NOT_CONFIGURED` is deliberate. Compare UI FPS, `UI ms`, X11 p95/p99 from a rendering sample, memory and battery temperature.
6. Reject if 92% launch regresses, window popups disappear, any crash, persistent thermal increase, or UI fps/p99 worsen beyond repeated A/B noise.

## Next technical gate: actual NPU inference

1. Measure representative workloads and acquire **real** GPU/WSI time series; otherwise an inference model cannot know what to optimize.
2. Obtain and verify a licensed small LiteRT model or generate our own. Select device-supported LiteRT NPU runtime (Qualcomm QNN/HTP, MediaTek only if provider is available). Check Android ABI, 16KiB page sizes, supported ops and packaged licenses.
3. Run the *same* fixed model on CPU and NPU with precisely measured latency and power. Do not report hardware-NPU success based on `Build.HARDWARE`, device model or NNAPI enumeration.
4. If NPU faster and safe, try NPU predictions as a detachable background worker; CPU always works as fallback.
5. Only allow **cold-start choices** of already-verified graphics configurations. Never hot-switch Vulkan ICD in a live Blender process. Enforce confidence, rollback and cooldown. Validate on A810, A830, A840, Mali G52, G57 and a newer Mali device separately.

No automated publishing or public release is authorized by this experiment.
