import { createBridgeRequest } from "../packettracer/bridge.js";
import { createExAppBridgeAdapter } from "../packettracer/exappBridgeAdapter.js";
import type { PacketTracerHostRuntime, PacketTracerRuntimeStatusResult } from "../packettracer/runtime/index.js";

async function main(): Promise<void> {
  const requestedScenario = process.argv[2] ?? "success";
  const scenario = requestedScenario === "malformed-response" ? "malformed" : requestedScenario;
  const workerModulePath = new URL("./exappSmokeWorker.js", import.meta.url);
  const workerScenario =
    scenario === "timeout"
      ? "timeout"
      : scenario === "malformed"
        ? "malformed"
        : scenario === "booting"
          ? "booting"
          : "success";

  const adapter = createExAppBridgeAdapter({
    command: scenario === "unavailable" ? "/definitely-missing-exapp-worker" : process.execPath,
    args:
      scenario === "unavailable"
        ? []
        : [workerModulePath.pathname, workerScenario],
    runtime: createSmokeRuntime(),
  });

  const request = createBridgeRequest({
    correlationId: `exapp-${scenario}`,
    backend: "exapp",
    operation: "invoke",
    timeoutMs: scenario === "timeout" ? 100 : 1_000,
    payload: {
      kind: "invoke",
      entrypoint: "show ip route",
      args: ["--json"],
    },
  });

  const response = await adapter.invoke(request);
  process.stdout.write(`${JSON.stringify(response, null, 2)}\n`);
}

function createSmokeRuntime(): PacketTracerHostRuntime {
  const runningStatus: PacketTracerRuntimeStatusResult = {
    operation: "status",
    phase: "ready",
    isInstalled: true,
    isRunning: true,
    canLaunch: false,
    recoveryAvailable: true,
    probe: "appimage-process-detected",
    processes: [
      {
        pid: 4242,
        args: "/usr/lib/packettracer/packettracer.AppImage",
        kind: "appimage",
      },
    ],
    paths: {
      launcherPath: "/usr/bin/packettracer",
      resetHelperPath: "/usr/bin/packettracer-reset-login-state",
      appImagePath: "/usr/lib/packettracer/packettracer.AppImage",
      launcherAvailable: true,
      resetHelperAvailable: true,
      appImageAvailable: true,
    },
  };

  return {
    async resolvePaths() {
      return runningStatus.paths;
    },
    async status() {
      return runningStatus;
    },
    async launch() {
      throw new Error("Smoke runtime does not implement launch().");
    },
    async resetLoginState() {
      throw new Error("Smoke runtime does not implement resetLoginState().");
    },
  };
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`${message}\n`);
  process.exitCode = 1;
});
