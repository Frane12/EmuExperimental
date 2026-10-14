# Drnas CPU Assist v0.1 — Hexagon investigation

**Status: CPU baseline and FastRPC loader probe only. NOT an NPU backend.**

Target: Qualcomm Snapdragon 8 Elite / SM8750, Android 16, Hexagon v79.
Project: EmuExperimental; independent of the GPU Turnip rendering path.

## Why test this?

Our FEX 2609/Wine host executes ordinary guest logic on Oryon. Hexagon cannot
execute arbitrary x86 or ARM64 Wine/FEX threads. A CPU offload only works for
specific, sufficiently large, *independent* DSP/ML workloads where cost
including synchronization, data transfer, and energy beats Oryon.

First potential application: isolated audio / signal processing batches or
other explicitly routed vector operators owned by our runtime. Do not intercept
arbitrary game or Windows APIs until a concrete operation is identified.

## What the experiment implements

- A JNI/native 7-tap **int16 signal filter on the CPU**, repeated at 4,096,
  65,536 and 262,144 samples.
- Median and p95 timing over 21 warmed runs, plus result checksum.
- Runtime loader visibility check via dlopen(libcdsprpc.so/libadsprpc.so).
- Opt-in diagnostics button; tests run on a worker, not the UI thread.
- No network permissions, no privileged device operations, no vendor blobs.
- Source/working driver/kernel remain unchanged; no benchmark affects game code.

**Caution:** A FastRPC library being loadable does not prove that an unsigned
DSP user-PD can be spawned, or that a custom HVX kernel may run. Conversely,
failure to load under Android's app linker namespace doesn't prove the DSP is
absent. Check actual FastRPC call return codes and device logs later.

## Planned v0.2 — only if we have SDK/device access

1. Build the **same** 7-tap kernel for Hexagon v79 HVX with Qualcomm Hexagon
   SDK and suitable FastRPC AArch64 host stub / IDL.
2. Check device/user-PD availability; capture the *real* invocation result.
3. Validate bit-exact or bounded-error output against CPU vectors.
4. Include buffer allocation, host↔DSP copies or shared-memory setup, RPC,
   synchronization and cache maintenance in the measured end-to-end cost.
5. Request no root and do not bypass vendor/system permissions. If blocked,
   report UNSUPPORTED, retain CPU fallback. Never fake an NPU result.
6. A/B per batch: CPU-only vs CPU+Hexagon while a real game is running.
   Measure CPU utilization, p50/p95/p99 frame-times, FPS, RAM, device power,
   temperature and DSP errors. Keep Max Frame Latency constant.

## Runtime gate for actual use

Default backend: CPU. Enable Hexagon only when:

- a real DSP kernel successfully ran on the phone with correct output;
- **total offload time (including transfer+RPC+synchronization)** is lower than
  corresponding CPU time by >= 20% on repeated equal-workload trials;
- 3 repeated game runs show a statistically meaningful reduction in CPU work
  and no >2% p99 frame-time regression, crashes or visual/audio glitches.

Timeouts, errors or unsupported platforms immediately fall back to CPU.
Never block Wine, the render thread, or audio callback waiting for a DSP job.

## References

- Qualcomm Hexagon SDK: https://www.qualcomm.com/developer/software/hexagon-npu-sdk
- Qualcomm Hexagon-MLIR FAQ (v79):
  https://github.com/qualcomm/hexagon-mlir/blob/main/docs/faq.md
- Qualcomm FastRPC: https://github.com/qualcomm/fastrpc

NPU neural graph APIs (QNN) and unrestricted arbitrary DSP code are
**different execution paths**. Availability of QNN does not imply the custom
FastRPC/HVX user-PD path will work on a stock Android device.
