import { randomUUID } from "node:crypto";

import {
  DEFAULT_BRIDGE_TIMEOUT_MS,
  MAX_BRIDGE_TIMEOUT_MS,
  MIN_BRIDGE_TIMEOUT_MS,
  type BridgeInvokePayload,
  type BridgeReadinessState,
  type BridgeResponse,
  createCanonicalBridgeErrorResponse,
} from "../bridge/index.js";
import {
  createExAppBridgeAdapter,
  resolveExAppBridgeTargetFromEnv,
  PACKET_TRACER_EXAPP_TARGET_ENV,
  type ExAppBridgeAdapterOptions,
} from "./exappBridgeAdapter.js";
import {
  createBridgeRequest,
} from "./bridge.js";
import {
  createPacketTracerHostRuntime,
  type PacketTracerHostRuntime,
  type PacketTracerRuntimeLaunchResult,
  type PacketTracerRuntimeResetResult,
  type PacketTracerRuntimeStatusResult,
} from "./runtime/index.js";

export const PACKET_TRACER_TOOL_NAMES = [
  "packettracer_launch",
  "packettracer_execute",
  "packettracer_reset",
  "packettracer_status",
] as const;

type PacketTracerToolName = (typeof PACKET_TRACER_TOOL_NAMES)[number];

export interface PacketTracerLaunchInput {
  args?: string[];
}

export interface PacketTracerExecuteInput {
  entrypoint: string;
  args?: string[];
  stdin?: string;
  timeoutMs?: number;
  experimental?: BridgeInvokePayload["experimental"];
}

export interface PacketTracerStatusInput {}

export interface PacketTracerBackendDescriptor {
  backend: "exapp";
  configured: boolean;
  selected: boolean;
}

export interface PacketTracerBackendSelection {
  preference: "exapp";
  requestedBackend: null;
  selectedBackend: "exapp" | null;
  attemptedBackend: "exapp";
  reason: "exapp-only" | "no-configured-backend";
  backends: PacketTracerBackendDescriptor[];
}

export interface PacketTracerLaunchResult {
  tool: typeof PACKET_TRACER_TOOL_NAMES[0];
  ok: boolean;
  backend: "exapp" | null;
  selection: PacketTracerBackendSelection;
  phase: PacketTracerRuntimeLaunchResult["phase"];
  launched: boolean;
  isInstalled: boolean;
  processes: PacketTracerRuntimeLaunchResult["processes"];
  paths: PacketTracerRuntimeLaunchResult["paths"];
  error?: {
    code: string;
    message: string;
  };
}

export interface PacketTracerResetResult {
  tool: typeof PACKET_TRACER_TOOL_NAMES[2];
  ok: boolean;
  backend: "exapp" | null;
  state: PacketTracerRuntimeResetResult["state"];
  exitCode: number | null;
  stdout: string;
  stderr: string;
  paths: PacketTracerRuntimeResetResult["paths"];
  error?: {
    code: string;
    message: string;
  };
}

export interface PacketTracerStatusResult {
  tool: typeof PACKET_TRACER_TOOL_NAMES[3];
  ok: boolean;
  backend: "exapp" | null;
  selection: PacketTracerBackendSelection;
  bridge: PacketTracerBridgeStatus;
  phase: PacketTracerRuntimeStatusResult["phase"];
  isInstalled: boolean;
  isRunning: boolean;
  canLaunch: boolean;
  recoveryAvailable: boolean;
  probe: PacketTracerRuntimeStatusResult["probe"];
  processes: PacketTracerRuntimeStatusResult["processes"];
  paths: PacketTracerRuntimeStatusResult["paths"];
  error?: {
    code: string;
    message: string;
  };
}

export interface PacketTracerBridgeStatus {
  detected: boolean;
  readiness: BridgeReadinessState;
  address: string | null;
  port: number | null;
  discovery: "local-experimental-bridge-range" | "packet-tracer-ipc-port-range" | null;
  instanceId: string | null;
}

export type PacketTracerExecuteResult =
  | ({ tool: typeof PACKET_TRACER_TOOL_NAMES[1]; selection: PacketTracerBackendSelection } & Extract<
      BridgeResponse,
      { ok: true }
    >)
  | ({ tool: typeof PACKET_TRACER_TOOL_NAMES[1]; selection: PacketTracerBackendSelection } & Extract<
      BridgeResponse,
      { ok: false }
    >);

export interface PacketTracerCatalogResource {
  namespace: "packettracer";
  version: "v1";
  tools: readonly PacketTracerToolName[];
  executionModel: "exapp-only";
  backendSelection: {
    activeBackend: "exapp";
    exappTargetEnv: typeof PACKET_TRACER_EXAPP_TARGET_ENV;
  };
}

export interface PacketTracerMcpServiceDependencies {
  env: NodeJS.ProcessEnv;
  runtime: PacketTracerHostRuntime;
  createExAppAdapter: (
    options: ExAppBridgeAdapterOptions,
    runtime: PacketTracerHostRuntime
  ) => ReturnType<typeof createExAppBridgeAdapter>;
  createCorrelationId: () => string;
}

export interface PacketTracerMcpService {
  launch(input?: PacketTracerLaunchInput): Promise<PacketTracerLaunchResult>;
  execute(input: PacketTracerExecuteInput): Promise<PacketTracerExecuteResult>;
  reset(): Promise<PacketTracerResetResult>;
  status(input?: PacketTracerStatusInput): Promise<PacketTracerStatusResult>;
  getCatalogResource(): PacketTracerCatalogResource;
}

export function createPacketTracerMcpService(
  dependencies: Partial<PacketTracerMcpServiceDependencies> = {}
): PacketTracerMcpService {
  const env = dependencies.env ?? process.env;
  const runtime = dependencies.runtime ?? createPacketTracerHostRuntime();
  const createExAdapter =
    dependencies.createExAppAdapter ??
    ((options, sharedRuntime) => createExAppBridgeAdapter({ ...options, runtime: sharedRuntime }));
  const createCorrelationId = dependencies.createCorrelationId ?? (() => randomUUID());
  const exappTarget = resolveExAppBridgeTargetFromEnv(env);
  const exappConfigured = exappTarget !== null;

  return {
    async launch(input = {}) {
      const selection = resolveSelection(exappConfigured);
      const result = await runtime.launch(input.args ?? []);
      return {
        tool: "packettracer_launch",
        ok: result.phase === "ready" || result.phase === "already-running",
        backend: selection.selectedBackend,
        selection,
        phase: result.phase,
        launched: result.launched,
        isInstalled: result.isInstalled,
        processes: result.processes,
        paths: result.paths,
        ...(result.problem
          ? {
              error: {
                code: result.problem.code,
                message: result.problem.message,
              },
            }
          : {}),
      };
    },

    async execute(input) {
      const selection = resolveSelection(exappConfigured);
      const experimental = input.experimental;
      const request = createBridgeRequest({
        correlationId: createCorrelationId(),
        backend: selection.attemptedBackend,
        operation: "invoke",
        timeoutMs: resolveExecuteTimeoutMs(input, experimental),
        payload: {
          entrypoint: input.entrypoint,
          args: input.args ?? [],
          ...(input.stdin !== undefined ? { stdin: input.stdin } : {}),
          ...(experimental !== undefined ? { experimental } : {}),
        },
      });

      if (selection.selectedBackend === null) {
        return {
          tool: "packettracer_execute",
          selection,
          ...createCanonicalBridgeErrorResponse(request, {
            code: "unavailable-backend",
            readiness: "unavailable",
            durationMs: 0,
            details: createSelectionDetails(selection),
          }),
        };
      }

      const configuredExappTarget = exappTarget;
      if (configuredExappTarget === null) {
        return {
          tool: "packettracer_execute",
          selection,
          ...createCanonicalBridgeErrorResponse(request, {
            code: "unavailable-backend",
            readiness: "unavailable",
            durationMs: 0,
            details: createSelectionDetails(selection),
          }),
        };
      }

      const response = await createExAdapter(configuredExappTarget, runtime).invoke(request);
      return {
        tool: "packettracer_execute",
        selection,
        ...response,
      };
    },

    async reset() {
      const result = await runtime.resetLoginState();
      return {
        tool: "packettracer_reset",
        ok: result.ok,
        backend: null,
        state: result.state,
        exitCode: result.exitCode,
        stdout: result.stdout,
        stderr: result.stderr,
        paths: result.paths,
        ...(result.problem
          ? {
              error: {
                code: result.problem.code,
                message: result.problem.message,
              },
            }
          : {}),
      };
    },

    async status() {
      const selection = resolveSelection(exappConfigured);
      const result = await runtime.status();
      const bridge = await readBridgeStatus({
        selection,
        runtime,
        createExAdapter,
        createCorrelationId,
        exappTarget,
      });

      return {
        tool: "packettracer_status",
        ok: result.phase !== "failed",
        backend: selection.selectedBackend,
        selection,
        bridge,
        phase: result.phase,
        isInstalled: result.isInstalled,
        isRunning: result.isRunning,
        canLaunch: result.canLaunch,
        recoveryAvailable: result.recoveryAvailable,
        probe: result.probe,
        processes: result.processes,
        paths: result.paths,
        ...(result.problem
          ? {
              error: {
                code: result.problem.code,
                message: result.problem.message,
              },
            }
          : {}),
      };
    },

    getCatalogResource() {
      return {
        namespace: "packettracer",
        version: "v1",
        tools: PACKET_TRACER_TOOL_NAMES,
        executionModel: "exapp-only",
        repoPaths: {
          workspaceRoot: "packettracer-mcp-bridge/",
          mcpServer: "packettracer-mcp-bridge/mcp-server/",
          apps: "packettracer-mcp-bridge/apps/",
        },
        backendSelection: {
          activeBackend: "exapp",
          exappTargetEnv: PACKET_TRACER_EXAPP_TARGET_ENV,
        },
        exappRuntime: {
          launchTool: "packettracer_launch",
          executeTool: "packettracer_execute",
          statusTool: "packettracer_status",
          resetTool: "packettracer_reset",
          readinessSource: "exapp-handshake",
          runtimeRole: "host-runtime-plus-exapp-bridge",
        },
      };
    },
  };
}

async function readBridgeStatus({
  selection,
  runtime,
  createExAdapter,
  createCorrelationId,
  exappTarget,
}: {
  selection: PacketTracerBackendSelection;
  runtime: PacketTracerHostRuntime;
  createExAdapter: PacketTracerMcpServiceDependencies["createExAppAdapter"];
  createCorrelationId: () => string;
  exappTarget: ReturnType<typeof resolveExAppBridgeTargetFromEnv>;
}): Promise<PacketTracerBridgeStatus> {
  if (selection.selectedBackend !== "exapp" || exappTarget === null) {
    return {
      detected: false,
      readiness: "unavailable",
      address: null,
      port: null,
      discovery: null,
      instanceId: null,
    };
  }

  try {
    const response = await createExAdapter(exappTarget, runtime).invoke(
      createBridgeRequest({
        correlationId: createCorrelationId(),
        backend: "exapp",
        operation: "read_status",
        timeoutMs: DEFAULT_BRIDGE_TIMEOUT_MS,
        payload: {
          includeRuntimeDiagnostics: false,
          includeHandshakeDetails: true,
        },
      })
    );

    return createBridgeStatusSnapshot(response);
  } catch {
    return {
      detected: false,
      readiness: "degraded",
      address: null,
      port: null,
      discovery: null,
      instanceId: null,
    };
  }
}

function createBridgeStatusSnapshot(response: BridgeResponse): PacketTracerBridgeStatus {
  if (response.operation !== "read_status") {
    return {
      detected: false,
      readiness: response.readiness,
      address: null,
      port: null,
      discovery: null,
      instanceId: null,
    };
  }

  const readStatusResponse: Extract<BridgeResponse, { operation: "read_status" }> = response;
  const details = readStatusResponse.ok
    ? readStatusResponse.result.bridge.details
    : readStatusResponse.error.details;
  const discovery = readBridgeDiscovery(details.sessionDiscovery);
  const address =
    discovery === "local-experimental-bridge-range" ? readNonEmptyString(details.ipcListenerAddress) : null;
  const port =
    discovery === "local-experimental-bridge-range" ? readNumericPort(details.ipcListenerPort) : null;

  return {
    detected:
      discovery === "local-experimental-bridge-range" &&
      details.sessionDiscoveryState === "connected" &&
      address !== null &&
      port !== null,
    readiness: readStatusResponse.readiness,
    address,
    port,
    discovery,
    instanceId: readNonEmptyString(details.instanceId),
  };
}

function readBridgeDiscovery(
  value: string | undefined
): PacketTracerBridgeStatus["discovery"] {
  if (value === "local-experimental-bridge-range" || value === "packet-tracer-ipc-port-range") {
    return value;
  }

  return null;
}

function readNumericPort(value: string | undefined): number | null {
  if (value === undefined) {
    return null;
  }

  const parsed = Number.parseInt(value, 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function readNonEmptyString(value: string | undefined): string | null {
  if (value === undefined) {
    return null;
  }

  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function resolveSelection(exappConfigured: boolean): PacketTracerBackendSelection {
  const selectedBackend = exappConfigured ? "exapp" : null;

  return {
    preference: "exapp",
    requestedBackend: null,
    selectedBackend,
    attemptedBackend: "exapp",
    reason: selectedBackend === null ? "no-configured-backend" : "exapp-only",
    backends: [
      {
        backend: "exapp",
        configured: exappConfigured,
        selected: selectedBackend === "exapp",
      },
    ],
  };
}

function resolveExperimentalInvoke(
  input: PacketTracerExecuteInput
): BridgeInvokePayload["experimental"] | undefined {
  return input.experimental;
}

function createSelectionDetails(selection: PacketTracerBackendSelection): Record<string, string> {
  return {
    selectionPreference: selection.preference,
    selectionReason: selection.reason,
    requestedBackend: selection.requestedBackend ?? "",
    selectedBackend: selection.selectedBackend ?? "",
    exappConfigured: String(
      selection.backends.find((backend) => backend.backend === "exapp")?.configured ?? false
    ),
  };
}

function clampTimeout(timeoutMs: number | undefined): number {
  if (timeoutMs === undefined) {
    return DEFAULT_BRIDGE_TIMEOUT_MS;
  }

  return Math.min(MAX_BRIDGE_TIMEOUT_MS, Math.max(MIN_BRIDGE_TIMEOUT_MS, Math.round(timeoutMs)));
}

function resolveExecuteTimeoutMs(
  input: PacketTracerExecuteInput,
  experimental: BridgeInvokePayload["experimental"] | undefined
): number {
  const requestedTimeoutMs = clampTimeout(input.timeoutMs);

  if (input.timeoutMs !== undefined) {
    return requestedTimeoutMs;
  }

  if (experimental?.operation === "list_components") {
    return Math.max(requestedTimeoutMs, 10_000);
  }

  return requestedTimeoutMs;
}
