/** Contract sketch. Not an implemented runtime or Android binding. */
export type Availability = "unsupported" | "experimental" | "validated";
export type SessionState =
  | "idle" | "validating" | "preparing" | "starting" | "running"
  | "stopping" | "failed";

export interface RuntimeComponent {
  id: string; version: string; source: string; sha256: string;
  abi: "arm64-v8a" | "x86_64"; availability: Availability;
}
export interface DeviceCapabilities {
  androidApi: number;
  gpuChipId: string; // never spoof
  gpuName: string;
  vulkanAvailable: boolean;
  freeMemoryMiB: number;
}
export interface GameProfile {
  schema: 1;
  gameId: string;
  name: string;
  windowsRuntime: string;
  cpuTranslator: string;
  graphics: { api: "d3d11" | "d3d12" | "opengl"; wrapper: string; driver: string };
  audio: { engine: string; latency: "low" | "balanced" | "compatibility" };
  input: { keyboardAndMouse: boolean; pointerOwnerRouting: true };
  frameCap: number; maxFrameLatency: boolean;
  experimental: boolean;
}
export interface SessionContext {
  sessionId: string;
  profile: Readonly<GameProfile>;
  device: Readonly<DeviceCapabilities>;
  componentHashes: Readonly<Record<string, string>>;
}
export interface RuntimeAdapter {
  id: string;
  probe(device: DeviceCapabilities): Promise<Availability>;
  validate(context: SessionContext): Promise<string[]>; // errors; empty means pass
  prepare(context: SessionContext): Promise<void>;
  start(context: SessionContext): Promise<{ processId: number }>;
  stop(sessionId: string): Promise<void>;
  collectLogs(sessionId: string): Promise<string[]>;
}
// Adapter calls are serialised by the session manager. No one else starts processes.
