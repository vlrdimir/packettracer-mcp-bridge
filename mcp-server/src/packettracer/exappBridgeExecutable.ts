import { readdir, readFile, readlink } from "node:fs/promises";
import { Socket } from "node:net";

import {
  createBridgeSuccessResponse,
  createCanonicalBridgeErrorResponse,
  type BridgeInvokePayload,
  validateBridgeRequest,
  validateBridgeResponse,
  type BridgeInvokeRequest,
  type BridgeReadStatusSuccessPayload,
  type BridgeReadinessState,
  type BridgeRequest,
  type BridgeResponse,
} from "../bridge/index.js";
import {
  createPacketTracerHostRuntime,
  type PacketTracerHostRuntime,
  type PacketTracerRuntimeStatusResult,
} from "./runtime/index.js";
import {
  EXAPP_BRIDGE_MODE_ARGS,
  PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE,
  exAppHandshakeSchema,
  type ExAppHandshake,
} from "./exappBridgeCli.js";
import { createExAppCapabilityResult } from "./exappCapabilityResult.js";
import { createReadStatusResult } from "./readStatusResult.js";

type ExAppBridgeMode = keyof typeof EXAPP_BRIDGE_MODE_ARGS;

const PACKET_TRACER_IPC_PORT_RANGE = {
  first: 39_000,
  last: 39_999,
} as const;

const LOCAL_EXPERIMENTAL_BRIDGE_PORT_RANGE = {
  first: 39_150,
  last: 39_159,
} as const;

/** When set (e.g. "39151"), probe this port before cache + range scan so MCP attaches to the intended ExApp listener. */
const PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT_ENV = "PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT";

function parsePreferredLocalExperimentalBridgePort(): number | null {
  const raw = process.env[PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT_ENV]?.trim();
  if (!raw) {
    return null;
  }

  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed)) {
    return null;
  }

  if (parsed < LOCAL_EXPERIMENTAL_BRIDGE_PORT_RANGE.first || parsed > LOCAL_EXPERIMENTAL_BRIDGE_PORT_RANGE.last) {
    return null;
  }

  return parsed;
}

const LOCAL_EXPERIMENTAL_HANDSHAKE_TIMEOUT_MS = 1500;
const LOCAL_EXPERIMENTAL_SCAN_TIMEOUT_MS = 250;
const LOCAL_EXPERIMENTAL_RESPONSE_GRACE_MS = 1500;
const LOCAL_EXPERIMENTAL_MAX_CACHE_MISSES = 3;

const LOCAL_PROBE_HOSTS = ["127.0.0.1", "::1"] as const;

let lastObservedLocalExperimentalBridge: ExAppIpcListenerObservation | null = null;
let lastObservedLocalExperimentalBridgeMisses = 0;

type ExAppSessionDiscoveryState =
  | "disconnected"
  | "listening"
  | "connected"
  | "degraded"
  | "unavailable";

interface ExAppIpcListenerObservation {
  pid: number;
  port: number;
  inode: string;
  address: string;
  instanceId?: string;
}

interface ExAppSessionObservation {
  method: "packet-tracer-ipc-port-range" | "local-experimental-bridge-range";
  state: ExAppSessionDiscoveryState;
  listeners: ExAppIpcListenerObservation[];
  selectedListener: ExAppIpcListenerObservation | null;
  isReachable: boolean;
  sessionId: string;
  instanceId: string;
  reason?: string;
}

interface ProcNetListenerRecord {
  address: string;
  port: number;
  inode: string;
}

interface ExAppSessionAttemptObservation {
  transport: "tcp-connect-observe-close";
  connectHost: string;
  connectPort: number;
  connected: boolean;
  observedBytes: number;
  observedPreview: string;
  remoteClosed: boolean;
  timedOut: boolean;
  reason: string;
}

interface ExperimentalJsStringPayloadAttemptObservation {
  transport: "tcp-connect-write-read-close";
  connectHost: string;
  connectPort: number;
  connected: boolean;
  payloadBytesSent: number;
  observedBytes: number;
  observedPreview: string;
  observedResponseText: string;
  observedEncoding: "empty" | "plain-string" | "json-string";
  remoteClosed: boolean;
  timedOut: boolean;
  reason: string;
}

interface ParsedLocalExperimentalReply {
  operation:
    | "handshake"
    | "echo"
    | "list_devices"
    | "list_components"
    | "list_ports"
    | "list_links"
    | "get_device_detail"
    | "read_interface_status"
    | "read_port_power_state"
    | "add_device"
    | "connect_devices"
    | "set_interface_ip"
    | "set_default_gateway"
    | "add_static_route"
    | "run_device_cli"
    | "set_port_power_state"
    | "run_ping"
    | "probe_terminal_transcript"
    | "remove_device"
    | "delete_link"
    | "get_device_module_layout"
    | "add_module"
    | "remove_module"
    | "add_module_at";
  status: "ok";
  payload: string;
}

interface ExperimentalOperationContractMetadata {
  resultContract:
    | "structured-state-surface"
    | "handshake-surface";
  proofRecommended: boolean;
  transcriptDependent: boolean;
  proofGuidance: string;
}

interface LocalExperimentalBridgeProbeResult {
  instanceId: string | null;
}

const EXPERIMENTAL_LOCAL_MESSAGE_FEATURE = "local-experimental-message" as const;
const EXPERIMENTAL_LOCAL_MESSAGE_MODE = "string" as const;
const LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX = "local-experimental" as const;
const LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE = "handshake" as const;
const LOCAL_EXPERIMENTAL_OPERATION_ECHO = "echo" as const;
const LOCAL_EXPERIMENTAL_OPERATION_LIST_DEVICES = "list_devices" as const;
const LOCAL_EXPERIMENTAL_OPERATION_LIST_COMPONENTS = "list_components" as const;
const LOCAL_EXPERIMENTAL_OPERATION_LIST_PORTS = "list_ports" as const;
const LOCAL_EXPERIMENTAL_OPERATION_LIST_LINKS = "list_links" as const;
const LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_DETAIL = "get_device_detail" as const;
const LOCAL_EXPERIMENTAL_OPERATION_READ_INTERFACE_STATUS = "read_interface_status" as const;
const LOCAL_EXPERIMENTAL_OPERATION_READ_PORT_POWER_STATE = "read_port_power_state" as const;
const LOCAL_EXPERIMENTAL_OPERATION_ADD_DEVICE = "add_device" as const;
const LOCAL_EXPERIMENTAL_OPERATION_CONNECT_DEVICES = "connect_devices" as const;
const LOCAL_EXPERIMENTAL_OPERATION_SET_INTERFACE_IP = "set_interface_ip" as const;
const LOCAL_EXPERIMENTAL_OPERATION_SET_DEFAULT_GATEWAY = "set_default_gateway" as const;
const LOCAL_EXPERIMENTAL_OPERATION_ADD_STATIC_ROUTE = "add_static_route" as const;
const LOCAL_EXPERIMENTAL_OPERATION_RUN_DEVICE_CLI = "run_device_cli" as const;
const LOCAL_EXPERIMENTAL_OPERATION_SET_PORT_POWER_STATE = "set_port_power_state" as const;
const LOCAL_EXPERIMENTAL_OPERATION_RUN_PING = "run_ping" as const;
const LOCAL_EXPERIMENTAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT = "probe_terminal_transcript" as const;
const LOCAL_EXPERIMENTAL_OPERATION_REMOVE_DEVICE = "remove_device" as const;
const LOCAL_EXPERIMENTAL_OPERATION_DELETE_LINK = "delete_link" as const;
const LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_MODULE_LAYOUT = "get_device_module_layout" as const;
const LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE = "add_module" as const;
const LOCAL_EXPERIMENTAL_OPERATION_REMOVE_MODULE = "remove_module" as const;
const LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE_AT = "add_module_at" as const;
type ExperimentalLocalMessageMode = NonNullable<BridgeInvokePayload["experimental"]>;

export interface PacketTracerExAppBridgeExecutableDependencies {
  argv: string[];
  stdin: NodeJS.ReadableStream;
  stdout: NodeJS.WritableStream;
  runtime: PacketTracerHostRuntime;
  now: () => number;
}

export async function runPacketTracerExAppBridgeExecutable(
  dependencies: Partial<PacketTracerExAppBridgeExecutableDependencies> = {}
): Promise<void> {
  const executable = {
    argv: dependencies.argv ?? process.argv.slice(2),
    stdin: dependencies.stdin ?? process.stdin,
    stdout: dependencies.stdout ?? process.stdout,
    runtime: dependencies.runtime ?? createPacketTracerHostRuntime(),
    now: dependencies.now ?? (() => Date.now()),
  } satisfies PacketTracerExAppBridgeExecutableDependencies;

  const mode = resolveMode(executable.argv);
  if (mode === null) {
    throw new Error(
      `Expected ${EXAPP_BRIDGE_MODE_ARGS.handshake} or ${EXAPP_BRIDGE_MODE_ARGS.invoke}.`
    );
  }

  if (mode === "handshake") {
    writeJson(executable.stdout, await createHandshake(executable.runtime));
    return;
  }

  const request = validateBridgeRequest(await readJsonFromStdin(executable.stdin));
  writeJson(executable.stdout, await createResponse(request, executable.runtime, executable.now));
}

async function createResponse(
  request: BridgeRequest,
  runtime: PacketTracerHostRuntime,
  now: () => number
): Promise<BridgeResponse> {
  const startedAt = now();
  const { handshake, observation, status } = await createHandshakeWithStatus(runtime);

  if (request.operation === "read_status") {
    return validateBridgeResponse(
      createBridgeSuccessResponse(
        request,
        handshake.readiness,
        createReadStatusPayload(handshake, status),
        durationSince(now, startedAt)
      )
    );
  }

  if (request.operation === "query_capabilities") {
    return validateBridgeResponse(
      createBridgeSuccessResponse(
        request,
        handshake.readiness,
        createCapabilitiesPayload(handshake),
        durationSince(now, startedAt)
      )
    );
  }

  return validateBridgeResponse(
    await createInvokeResponse({
      request,
      handshake,
      observation,
      now,
      startedAt,
    })
  );
}

async function createInvokeResponse({
  request,
  handshake,
  observation,
  now,
  startedAt,
}: {
  request: BridgeInvokeRequest;
  handshake: ExAppHandshake;
  observation: ExAppSessionObservation | null;
  now: () => number;
  startedAt: number;
}): Promise<BridgeResponse> {
  const selectedListener = observation?.selectedListener ?? null;

  if (handshake.readiness === "unavailable") {
    return createCanonicalBridgeErrorResponse(request, {
      code: "unavailable-backend",
      readiness: handshake.readiness,
      durationMs: durationSince(now, startedAt),
      details: createInvokeDetails(handshake, {
        invokeAttempt: "not-attempted",
        invokeReason: "backend-unavailable",
      }),
    });
  }

  if (handshake.readiness !== "ready") {
    return createCanonicalBridgeErrorResponse(request, {
      code: "not-ready",
      readiness: handshake.readiness === "booting" ? "booting" : "not-ready",
      durationMs: durationSince(now, startedAt),
      details: createInvokeDetails(handshake, {
        invokeAttempt: "not-attempted",
        invokeReason: `handshake-${handshake.readiness}`,
      }),
    });
  }

  if (selectedListener === null) {
    return createCanonicalBridgeErrorResponse(request, {
      code: "not-ready",
      readiness: "not-ready",
      durationMs: durationSince(now, startedAt),
      details: createInvokeDetails(handshake, {
        invokeAttempt: "not-attempted",
        invokeReason: "session-listener-unavailable",
      }),
    });
  }

  const timeoutMs = remainingInvokeBudget(now, startedAt, request.timeoutMs);
  const experimentalMode = readExperimentalLocalMessageMode(request.payload);

  try {
    if (experimentalMode !== null) {
      return await createExperimentalLocalMessageInvokeResponse({
        request,
        handshake,
        selectedListener,
        experimentalMode,
        timeoutMs,
        now,
        startedAt,
      });
    }

    const sessionAttempt = await attemptExAppSessionContact(selectedListener, timeoutMs);

    return createCanonicalBridgeErrorResponse(request, {
      code: "malformed-response",
      readiness: "degraded",
      durationMs: durationSince(now, startedAt),
      message:
        "A real Packet Tracer ExApp session was reached, but no provable invoke payload protocol is implemented yet.",
      retryable: false,
      details: createInvokeDetails(handshake, {
        invokeAttempt: sessionAttempt.connected ? "session-contacted" : "session-contact-failed",
        invokeReason: sessionAttempt.reason,
        transportInteraction: sessionAttempt.transport,
        connectHost: sessionAttempt.connectHost,
        connectPort: String(sessionAttempt.connectPort),
        sessionTransportContact: String(sessionAttempt.connected),
        sessionObservedBytes: String(sessionAttempt.observedBytes),
        sessionObservedPreview: sessionAttempt.observedPreview,
        sessionRemoteClosed: String(sessionAttempt.remoteClosed),
        sessionAttemptTimedOut: String(sessionAttempt.timedOut),
        payloadProtocol: "unproven",
        payloadBytesSent: "0",
        requestedEntrypoint: String(request.payload.entrypoint ?? ""),
      }),
    });
  } catch (error) {
    if (error instanceof ExAppSessionAttemptTimeoutError) {
      return createCanonicalBridgeErrorResponse(request, {
        code: "timeout",
        readiness: "degraded",
        durationMs: durationSince(now, startedAt),
        details: createInvokeDetails(handshake, {
          invokeAttempt: "session-contact-timeout",
          invokeReason: error.message,
          transportInteraction: "tcp-connect-observe-close",
          connectHost: error.host,
          connectPort: String(error.port),
          payloadProtocol: "unproven",
        }),
      });
    }

    if (error instanceof ExAppSessionAttemptUnavailableError) {
      return createCanonicalBridgeErrorResponse(request, {
        code: "unavailable-backend",
        readiness: "degraded",
        durationMs: durationSince(now, startedAt),
        message: "The discovered Packet Tracer ExApp session could not be reached for invoke.",
        details: createInvokeDetails(handshake, {
          invokeAttempt: "session-contact-failed",
          invokeReason: error.message,
          transportInteraction: "tcp-connect-observe-close",
          connectHost: error.host,
          connectPort: String(error.port),
          payloadProtocol: "unproven",
        }),
      });
    }

    throw error;
  }
}

async function createExperimentalLocalMessageInvokeResponse({
  request,
  handshake,
  selectedListener,
  experimentalMode,
  timeoutMs,
  now,
  startedAt,
}: {
  request: BridgeInvokeRequest;
  handshake: ExAppHandshake;
  selectedListener: ExAppIpcListenerObservation;
  experimentalMode: ExperimentalLocalMessageMode;
  timeoutMs: number;
  now: () => number;
  startedAt: number;
}): Promise<BridgeResponse> {
  if (!isExperimentalLocalMessagePayloadAvailable(handshake)) {
    return createCanonicalBridgeErrorResponse(request, {
      code: "malformed-response",
      readiness: "degraded",
      durationMs: durationSince(now, startedAt),
      message:
        "Local experimental message mode was requested, but the current bridge/session observations do not prove the feature is available.",
      retryable: false,
      details: createInvokeDetails(handshake, {
        invokeAttempt: "not-attempted",
        invokeReason: "local-experimental-message-unavailable",
        payloadProtocol: LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX,
        payloadFeature: EXPERIMENTAL_LOCAL_MESSAGE_FEATURE,
        payloadModeStatus: "experimental-non-official",
        payloadBytesSent: "0",
        requestedEntrypoint: experimentalMode.operation,
      }),
    });
  }

  const payloadString = createExperimentalLocalMessagePayload(experimentalMode);
  const sessionAttempt = await attemptExperimentalLocalMessagePayload(
    selectedListener,
    payloadString,
    timeoutMs
  );

  if (sessionAttempt.observedBytes === 0 || sessionAttempt.observedResponseText.trim().length === 0) {
    return createCanonicalBridgeErrorResponse(request, {
      code: "malformed-response",
      readiness: "degraded",
      durationMs: durationSince(now, startedAt),
      message:
        "A real Packet Tracer ExApp session was reached with the local experimental message mode, but no usable string response was observed.",
      retryable: false,
      details: createInvokeDetails(handshake, {
        invokeAttempt: sessionAttempt.connected ? "local-experimental-message-sent" : "session-contact-failed",
        invokeReason: sessionAttempt.reason,
        transportInteraction: sessionAttempt.transport,
        connectHost: sessionAttempt.connectHost,
        connectPort: String(sessionAttempt.connectPort),
        sessionTransportContact: String(sessionAttempt.connected),
        sessionObservedBytes: String(sessionAttempt.observedBytes),
        sessionObservedPreview: sessionAttempt.observedPreview,
        sessionRemoteClosed: String(sessionAttempt.remoteClosed),
        sessionAttemptTimedOut: String(sessionAttempt.timedOut),
        payloadProtocol: LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX,
        payloadFeature: EXPERIMENTAL_LOCAL_MESSAGE_FEATURE,
        payloadModeStatus: "experimental-non-official",
        payloadBytesSent: String(sessionAttempt.payloadBytesSent),
        rawOutboundPayload: payloadString,
        localExperimentalOperation: experimentalMode.operation,
        requestedEntrypoint: experimentalMode.operation,
      }),
    });
  }

  const parsedReply = parseLocalExperimentalReply(sessionAttempt.observedResponseText);
  if (parsedReply === null || parsedReply.operation !== experimentalMode.operation) {
    return createCanonicalBridgeErrorResponse(request, {
      code: "malformed-response",
      readiness: "degraded",
      durationMs: durationSince(now, startedAt),
      message:
        "A real Packet Tracer ExApp session was reached with the local experimental message mode, but the reply did not match the expected local-experimental operation format.",
      retryable: false,
      details: createInvokeDetails(handshake, {
        invokeAttempt: "local-experimental-message-sent",
        invokeReason:
          parsedReply === null
            ? "local-experimental-reply-malformed"
            : "local-experimental-reply-operation-mismatch",
        transportInteraction: sessionAttempt.transport,
        connectHost: sessionAttempt.connectHost,
        connectPort: String(sessionAttempt.connectPort),
        sessionTransportContact: String(sessionAttempt.connected),
        sessionObservedBytes: String(sessionAttempt.observedBytes),
        sessionObservedPreview: sessionAttempt.observedPreview,
        sessionRemoteClosed: String(sessionAttempt.remoteClosed),
        sessionAttemptTimedOut: String(sessionAttempt.timedOut),
        payloadProtocol: LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX,
        payloadFeature: EXPERIMENTAL_LOCAL_MESSAGE_FEATURE,
        payloadModeStatus: "experimental-non-official",
        payloadBytesSent: String(sessionAttempt.payloadBytesSent),
        rawOutboundPayload: payloadString,
        rawReturnedPayload: sessionAttempt.observedResponseText,
        localExperimentalOperation: experimentalMode.operation,
        expectedReplyOperation: experimentalMode.operation,
        observedReplyOperation: parsedReply?.operation ?? "",
        requestedEntrypoint: experimentalMode.operation,
      }),
    });
  }

  return validateBridgeResponse(
    createBridgeSuccessResponse(
      request,
      handshake.readiness,
      {
        stdout: parsedReply.payload,
        stderr: "",
        exitCode: 0,
        backendRequestId: handshake.backendRequestId,
        metadata: {
          payloadMode: "local-experimental-message",
          payloadFeature: EXPERIMENTAL_LOCAL_MESSAGE_FEATURE,
          payloadTransportModel: "local-experimental-exapp-string-message",
          payloadOfficialSupport: "false",
          rawOutboundPayload: payloadString,
          rawReturnedPayload: sessionAttempt.observedResponseText,
          localExperimentalOperation: experimentalMode.operation,
          localExperimentalStatus: parsedReply.status,
          responseEncoding: sessionAttempt.observedEncoding,
          ...createExperimentalOperationMetadata(experimentalMode.operation, parsedReply.payload),
        },
      },
      durationSince(now, startedAt)
    )
  );
}

function createExperimentalOperationMetadata(
  operation: ParsedLocalExperimentalReply["operation"],
  payload: string
): Record<string, string> {
  const contract = resolveExperimentalOperationContract(operation, payload);

  return {
    resultContract: contract.resultContract,
    proofRecommended: String(contract.proofRecommended),
    transcriptDependent: String(contract.transcriptDependent),
    proofGuidance: contract.proofGuidance,
    preferredProofOperations:
      "packettracer_status,list_devices,list_components,list_ports,list_links,get_device_detail,read_interface_status,read_port_power_state",
  };
}

function resolveExperimentalOperationContract(
  operation: ParsedLocalExperimentalReply["operation"],
  payload: string
): ExperimentalOperationContractMetadata {
  if (
    operation === LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_ECHO
  ) {
    return {
      resultContract: "handshake-surface",
      proofRecommended: operation === LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE,
      transcriptDependent: false,
      proofGuidance:
        operation === LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE
          ? "Use handshake together with packettracer_status.bridge for bridge/session readiness proof."
          : "This operation is only a transport/protocol probe, not a topology or command-output proof surface.",
    };
  }

  if (
    operation === LOCAL_EXPERIMENTAL_OPERATION_LIST_DEVICES ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_LIST_COMPONENTS ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_LIST_PORTS ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_LIST_LINKS ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_DETAIL ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_READ_INTERFACE_STATUS ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_READ_PORT_POWER_STATE
  ) {
    return {
      resultContract: "structured-state-surface",
      proofRecommended: true,
      transcriptDependent: false,
      proofGuidance:
        "This is a structured state surface and should be preferred over terminal transcript scraping for automation proof.",
      };
  }

  if (
    operation === LOCAL_EXPERIMENTAL_OPERATION_ADD_DEVICE ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_CONNECT_DEVICES ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_SET_INTERFACE_IP ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_SET_DEFAULT_GATEWAY ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_ADD_STATIC_ROUTE
  ) {
    return {
      resultContract: "structured-state-surface",
      proofRecommended: false,
      transcriptDependent: false,
      proofGuidance:
        "This is a mutation/config surface. Follow it with structured proof such as list_links, get_device_detail, read_interface_status, or read_port_power_state.",
    };
  }

  if (operation === LOCAL_EXPERIMENTAL_OPERATION_RUN_DEVICE_CLI) {
    return {
      resultContract: "handshake-surface",
      proofRecommended: false,
      transcriptDependent: true,
      proofGuidance:
        "This is a CLI helper surface. Follow it with structured proof when authoritative topology or interface state matters.",
    };
  }

  if (operation === LOCAL_EXPERIMENTAL_OPERATION_SET_PORT_POWER_STATE) {
    return {
      resultContract: "structured-state-surface",
      proofRecommended: false,
      transcriptDependent: false,
      proofGuidance:
        "This is a mutation helper. Follow it with read_interface_status or read_port_power_state to confirm operational state.",
    };
  }

  if (operation === LOCAL_EXPERIMENTAL_OPERATION_RUN_PING) {
    return {
      resultContract: "handshake-surface",
      proofRecommended: false,
      transcriptDependent: true,
      proofGuidance:
        "This is a ping helper surface. Use probe_terminal_transcript or structured state for stronger proof when needed.",
    };
  }

  if (operation === LOCAL_EXPERIMENTAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT) {
    return {
      resultContract: "handshake-surface",
      proofRecommended: false,
      transcriptDependent: true,
      proofGuidance:
        "This is a transcript/terminal observation surface. Prefer structured state for authoritative topology proof.",
    };
  }

  if (
    operation === LOCAL_EXPERIMENTAL_OPERATION_REMOVE_DEVICE ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_DELETE_LINK ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_MODULE_LAYOUT ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_REMOVE_MODULE ||
    operation === LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE_AT
  ) {
    return {
      resultContract: "structured-state-surface",
      proofRecommended: false,
      transcriptDependent: false,
      proofGuidance:
        "This is a mutation or module-inspection surface. Follow it with list_ports, list_links, get_device_detail, or get_device_module_layout as appropriate.",
    };
  }

  return {
    resultContract: "handshake-surface",
    proofRecommended: false,
    transcriptDependent: false,
    proofGuidance:
      "This operation is only a narrow local experimental proof surface.",
  };
}

function createInvokeDetails(
  handshake: ExAppHandshake,
  details: Record<string, string>
): Record<string, string> {
  return {
    ...handshake.details,
    executable: PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE,
    mode: "invoke",
    ...details,
  };
}

function readExperimentalLocalMessageMode(
  payload: BridgeInvokeRequest["payload"]
): ExperimentalLocalMessageMode | null {
  if (
    payload.experimental?.feature === EXPERIMENTAL_LOCAL_MESSAGE_FEATURE &&
    payload.experimental.mode === EXPERIMENTAL_LOCAL_MESSAGE_MODE
  ) {
    return payload.experimental as ExperimentalLocalMessageMode;
  }

  return null;
}

function isExperimentalLocalMessagePayloadAvailable(handshake: ExAppHandshake): boolean {
  return (
    handshake.readiness === "ready" &&
    handshake.details.experimentalInvokePayloadStatus === "available-with-opt-in" &&
    handshake.details.experimentalInvokePayloadFeature === EXPERIMENTAL_LOCAL_MESSAGE_FEATURE &&
    handshake.details.sessionDiscovery === "local-experimental-bridge-range"
  );
}

function createExperimentalLocalMessagePayload(
  experimentalMode: ExperimentalLocalMessageMode
): string {
  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_LIST_DEVICES) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_LIST_DEVICES}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_LIST_COMPONENTS) {
    return experimentalMode.category && experimentalMode.category.trim().length > 0
      ? `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_LIST_COMPONENTS}|${experimentalMode.category.trim()}`
      : `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_LIST_COMPONENTS}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_LIST_PORTS) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_LIST_PORTS}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_LIST_LINKS) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_LIST_LINKS}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_DETAIL) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_DETAIL}|${experimentalMode.selector}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_READ_INTERFACE_STATUS) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_READ_INTERFACE_STATUS}|${experimentalMode.selector}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_READ_PORT_POWER_STATE) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_READ_PORT_POWER_STATE}|${experimentalMode.deviceSelector}|${experimentalMode.portSelector}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_ADD_DEVICE) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_ADD_DEVICE}|${experimentalMode.deviceType}|${experimentalMode.model}|${experimentalMode.x}|${experimentalMode.y}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_CONNECT_DEVICES) {
    return experimentalMode.connectionType && experimentalMode.connectionType.trim().length > 0
      ? `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_CONNECT_DEVICES}|${experimentalMode.leftDeviceSelector}|${experimentalMode.leftPortSelector}|${experimentalMode.rightDeviceSelector}|${experimentalMode.rightPortSelector}|${experimentalMode.connectionType.trim()}`
      : `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_CONNECT_DEVICES}|${experimentalMode.leftDeviceSelector}|${experimentalMode.leftPortSelector}|${experimentalMode.rightDeviceSelector}|${experimentalMode.rightPortSelector}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_SET_INTERFACE_IP) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_SET_INTERFACE_IP}|${experimentalMode.deviceSelector}|${experimentalMode.portSelector}|${experimentalMode.ipAddress}|${experimentalMode.subnetMask}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_SET_DEFAULT_GATEWAY) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_SET_DEFAULT_GATEWAY}|${experimentalMode.deviceSelector}|${experimentalMode.gateway}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_ADD_STATIC_ROUTE) {
    const portSelector = experimentalMode.portSelector ?? "";
    const adminDistance = experimentalMode.adminDistance === undefined ? "" : String(experimentalMode.adminDistance);
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_ADD_STATIC_ROUTE}|${experimentalMode.deviceSelector}|${experimentalMode.network}|${experimentalMode.subnetMask}|${experimentalMode.nextHop}|${portSelector}|${adminDistance}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_RUN_DEVICE_CLI) {
    const cliMode = experimentalMode.cliMode && experimentalMode.cliMode.trim().length > 0 ? experimentalMode.cliMode.trim() : "user";
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_RUN_DEVICE_CLI}|${experimentalMode.deviceSelector}|${cliMode}|${experimentalMode.command}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_SET_PORT_POWER_STATE) {
    const rawState = experimentalMode.powerOn === false ? "off" : "on";
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_SET_PORT_POWER_STATE}|${experimentalMode.deviceSelector}|${experimentalMode.portSelector}|${rawState}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_RUN_PING) {
    const repeatCount = experimentalMode.repeatCount === undefined ? "" : String(experimentalMode.repeatCount);
    const timeoutSeconds = experimentalMode.timeoutSeconds === undefined ? "" : String(experimentalMode.timeoutSeconds);
    const packetSize = experimentalMode.packetSize === undefined ? "" : String(experimentalMode.packetSize);
    const sourcePortName = experimentalMode.sourcePortName ?? "";
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_RUN_PING}|${experimentalMode.deviceSelector}|${experimentalMode.destinationIpAddress}|${repeatCount}|${timeoutSeconds}|${packetSize}|${sourcePortName}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT) {
    const cliMode = experimentalMode.cliMode && experimentalMode.cliMode.trim().length > 0 ? experimentalMode.cliMode.trim() : "user";
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT}|${experimentalMode.deviceSelector}|${cliMode}|${experimentalMode.command}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_REMOVE_DEVICE) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_REMOVE_DEVICE}|${experimentalMode.selector}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_DELETE_LINK) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_DELETE_LINK}|${experimentalMode.selector}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_MODULE_LAYOUT) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_MODULE_LAYOUT}|${experimentalMode.deviceSelector}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE}|${experimentalMode.deviceSelector}|${experimentalMode.slot}|${experimentalMode.moduleType}|${experimentalMode.model}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_REMOVE_MODULE) {
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_REMOVE_MODULE}|${experimentalMode.deviceSelector}|${experimentalMode.slot}|${experimentalMode.moduleType}`;
  }

  if (experimentalMode.operation === LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE_AT) {
    const parentModulePath = experimentalMode.parentModulePath ?? "";
    return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE_AT}|${experimentalMode.deviceSelector}|${parentModulePath}|${experimentalMode.slotIndex}|${experimentalMode.model}`;
  }

  return `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_ECHO}|${experimentalMode.payload}`;
}

function remainingInvokeBudget(now: () => number, startedAt: number, timeoutMs: number): number {
  return Math.max(1, timeoutMs - durationSince(now, startedAt));
}

async function attemptExAppSessionContact(
  listener: ExAppIpcListenerObservation,
  timeoutMs: number
): Promise<ExAppSessionAttemptObservation> {
  const connectHost = resolveSessionConnectHost(listener.address);

  return new Promise<ExAppSessionAttemptObservation>((resolve, reject) => {
    const socket = new Socket();
    let settled = false;
    let connected = false;
    let observedBytes = 0;
    let preview = "";
    let remoteClosed = false;
    let observationTimer: NodeJS.Timeout | null = null;

    const finish = (callback: () => void) => {
      if (settled) {
        return;
      }

      settled = true;
      if (observationTimer) {
        clearTimeout(observationTimer);
      }
      socket.removeAllListeners();
      socket.destroy();
      callback();
    };

    const succeed = (reason: string, timedOut = false) => {
      finish(() => {
        resolve({
          transport: "tcp-connect-observe-close",
          connectHost,
          connectPort: listener.port,
          connected,
          observedBytes,
          observedPreview: preview,
          remoteClosed,
          timedOut,
          reason,
        });
      });
    };

    socket.setTimeout(Math.max(timeoutMs, LOCAL_EXPERIMENTAL_RESPONSE_GRACE_MS));
    socket.once("connect", () => {
      connected = true;
      const observationWindowMs = Math.max(1, Math.min(150, timeoutMs));
      observationTimer = setTimeout(() => {
        socket.end();
        succeed(observedBytes > 0 ? "session-contacted-observed-unrecognized-data" : "session-contacted-no-payload-observed");
      }, observationWindowMs);
    });
    socket.on("data", (chunk: Buffer) => {
      observedBytes += chunk.length;
      if (preview.length < 256) {
        preview += summarizeObservedBytes(chunk, 256 - preview.length);
      }
    });
    socket.once("end", () => {
      remoteClosed = true;
    });
    socket.once("close", () => {
      if (!settled) {
        succeed(
          connected
            ? observedBytes > 0
              ? "session-closed-after-unrecognized-data"
              : "session-closed-without-payload"
            : "session-closed-before-connect"
        );
      }
    });
    socket.once("timeout", () => {
      finish(() => {
        reject(new ExAppSessionAttemptTimeoutError(connectHost, listener.port, timeoutMs));
      });
    });
    socket.once("error", (error) => {
      finish(() => {
        reject(new ExAppSessionAttemptUnavailableError(connectHost, listener.port, error));
      });
    });

    socket.connect(listener.port, connectHost);
  });
}

async function attemptExperimentalLocalMessagePayload(
  listener: ExAppIpcListenerObservation,
  payloadString: string,
  timeoutMs: number
): Promise<ExperimentalJsStringPayloadAttemptObservation> {
  const connectHost = resolveSessionConnectHost(listener.address);
  const framedPayload = `${payloadString}\n`;

  return new Promise<ExperimentalJsStringPayloadAttemptObservation>((resolve, reject) => {
    const socket = new Socket();
    let settled = false;
    let connected = false;
    let payloadBytesSent = 0;
    let observedBytes = 0;
    let observedResponseText = "";
    let observedPreview = "";
    let remoteClosed = false;
    let observationTimer: NodeJS.Timeout | null = null;

    const finish = (callback: () => void) => {
      if (settled) {
        return;
      }

      settled = true;
      if (observationTimer) {
        clearTimeout(observationTimer);
      }
      socket.removeAllListeners();
      socket.destroy();
      callback();
    };

    const succeed = (reason: string, timedOut = false) => {
      finish(() => {
        resolve({
          transport: "tcp-connect-write-read-close",
          connectHost,
          connectPort: listener.port,
          connected,
          payloadBytesSent,
          observedBytes,
          observedPreview,
          observedResponseText,
          observedEncoding: classifyObservedStringResponse(observedResponseText),
          remoteClosed,
          timedOut,
          reason,
        });
      });
    };

    socket.setTimeout(timeoutMs);
    socket.once("connect", () => {
        connected = true;
        payloadBytesSent = Buffer.byteLength(framedPayload, "utf8");
        socket.write(framedPayload, "utf8", () => {
          socket.end();
        });
    });
    socket.on("data", (chunk: Buffer) => {
      observedBytes += chunk.length;
      observedResponseText += chunk.toString("utf8");
      if (observationTimer) {
        clearTimeout(observationTimer);
      }
      observationTimer = setTimeout(() => {
        succeed("local-experimental-response-observed");
      }, Math.max(1, Math.min(LOCAL_EXPERIMENTAL_RESPONSE_GRACE_MS, timeoutMs)));
      if (observedPreview.length < 256) {
        observedPreview += summarizeObservedBytes(chunk, 256 - observedPreview.length);
      }
    });
    socket.once("end", () => {
      remoteClosed = true;
    });
    socket.once("close", () => {
        if (!settled) {
          succeed(
            connected
              ? observedBytes > 0
                ? "local-experimental-response-observed"
                : "local-experimental-closed-without-response"
              : "session-closed-before-connect"
          );
        }
    });
    socket.once("timeout", () => {
      finish(() => {
        reject(new ExAppSessionAttemptTimeoutError(connectHost, listener.port, timeoutMs));
      });
    });
    socket.once("error", (error) => {
      finish(() => {
        reject(new ExAppSessionAttemptUnavailableError(connectHost, listener.port, error));
      });
    });

    socket.connect(listener.port, connectHost);
  });
}

function classifyObservedStringResponse(responseText: string): "empty" | "plain-string" | "json-string" {
  const trimmed = responseText.trim();
  if (trimmed.length === 0) {
    return "empty";
  }

  try {
    JSON.parse(trimmed);
    return "json-string";
  } catch {
    return "plain-string";
  }
}

function parseLocalExperimentalReply(rawResponseText: string): ParsedLocalExperimentalReply | null {
  const trimmedResponse = rawResponseText.trim();
  if (trimmedResponse.length === 0) {
    return null;
  }

  const firstSeparator = trimmedResponse.indexOf("|");
  if (firstSeparator <= 0) {
    return null;
  }

  const protocolPrefix = trimmedResponse.slice(0, firstSeparator);
  if (protocolPrefix !== LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX) {
    return null;
  }

  const secondSeparator = trimmedResponse.indexOf("|", firstSeparator + 1);
  const thirdSeparator = secondSeparator < 0 ? -1 : trimmedResponse.indexOf("|", secondSeparator + 1);
  if (secondSeparator < 0 || thirdSeparator < 0) {
    return null;
  }

  const operation = trimmedResponse.slice(firstSeparator + 1, secondSeparator);
  const status = trimmedResponse.slice(secondSeparator + 1, thirdSeparator);
  if (
    (operation !== LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_ECHO &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_LIST_DEVICES &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_LIST_COMPONENTS &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_LIST_PORTS &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_LIST_LINKS &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_DETAIL &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_READ_INTERFACE_STATUS &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_READ_PORT_POWER_STATE &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_ADD_DEVICE &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_CONNECT_DEVICES &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_SET_INTERFACE_IP &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_SET_DEFAULT_GATEWAY &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_ADD_STATIC_ROUTE &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_RUN_DEVICE_CLI &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_SET_PORT_POWER_STATE &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_RUN_PING &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_REMOVE_DEVICE &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_DELETE_LINK &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_GET_DEVICE_MODULE_LAYOUT &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_REMOVE_MODULE &&
      operation !== LOCAL_EXPERIMENTAL_OPERATION_ADD_MODULE_AT) ||
    status !== "ok"
  ) {
    return null;
  }

  return {
    operation,
    status: "ok",
    payload: trimmedResponse.slice(thirdSeparator + 1),
  };
}

function readHandshakeInstanceId(payload: string): string | null {
  const trimmedPayload = payload.trim();
  if (!trimmedPayload.startsWith("instance=")) {
    return null;
  }

  const instanceId = trimmedPayload.slice("instance=".length).trim();
  if (instanceId.length === 0 || instanceId === "unknown") {
    return null;
  }

  return instanceId;
}

function resolveSessionConnectHost(address: string): string {
  return address === "::1" || address.startsWith("tcp6:") ? "::1" : "127.0.0.1";
}

function summarizeObservedBytes(chunk: Buffer, maxLength: number): string {
  const printable = chunk
    .toString("utf8")
    .replace(/[^\x20-\x7E]+/g, " ")
    .trim();

  if (printable.length > 0) {
    return printable.slice(0, maxLength);
  }

  return chunk.toString("hex").slice(0, maxLength);
}

async function createHandshake(runtime: PacketTracerHostRuntime): Promise<ExAppHandshake> {
  return (await createHandshakeWithStatus(runtime)).handshake;
}

async function createHandshakeWithStatus(
  runtime: PacketTracerHostRuntime
): Promise<{
  handshake: ExAppHandshake;
  observation: ExAppSessionObservation | null;
  status: PacketTracerRuntimeStatusResult | null;
}> {
  try {
    const status = await runtime.status();
    const observation = await discoverPacketTracerExAppSession(status);
    const handshake = exAppHandshakeSchema.parse({
      protocolVersion: "2026-04-15",
      backend: "exapp",
      readiness: mapStatusToHandshakeReadiness(status, observation),
      backendRequestId: createBackendRequestId(status, observation),
      details: createHandshakeDetails(status, observation),
    });

    return { handshake, observation, status };
  } catch (error) {
    const handshake = exAppHandshakeSchema.parse({
      protocolVersion: "2026-04-15",
      backend: "exapp",
      readiness: "degraded",
      backendRequestId: createBackendRequestId("failed"),
        details: {
          executable: PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE,
          sessionDiscovery: "packet-tracer-ipc-port-range",
          sessionDiscoveryState: "degraded",
          ipcPortRange: describePacketTracerIpcPortRange(),
          experimentalInvokePayloadFeature: EXPERIMENTAL_LOCAL_MESSAGE_FEATURE,
          experimentalInvokePayloadMode: EXPERIMENTAL_LOCAL_MESSAGE_MODE,
          experimentalInvokePayloadTransportModel: "local-experimental-exapp-string-message",
          experimentalInvokePayloadOfficial: "false",
          experimentalInvokePayloadOptInField: "payload.experimental",
          experimentalInvokePayloadReadOnlyOnly: "true",
          experimentalInvokePayloadOperations: "handshake,echo,list_devices,list_components,list_ports,list_links,get_device_detail,read_interface_status,read_port_power_state",
          experimentalInvokePayloadPreferredProofOperations: "list_devices,list_components,list_ports,list_links,get_device_detail,read_interface_status,read_port_power_state,packettracer_status",
          experimentalInvokePayloadWriteEnabled: "false",
          experimentalInvokePayloadStatus: "disabled-runtime-observation-failed",
          runtimeProbe: "error",
        reason: formatError(error),
      },
    });

    return { handshake, observation: null, status: null };
  }
}

function mapStatusToHandshakeReadiness(
  status: PacketTracerRuntimeStatusResult,
  observation: ExAppSessionObservation
): BridgeReadinessState {
  if (observation.state === "degraded") {
    return "degraded";
  }

  if (observation.state === "connected") {
    return "ready";
  }

  if (!status.isInstalled || status.phase === "not-installed") {
    return "unavailable";
  }

  if (status.phase === "failed") {
    return "degraded";
  }

  if (status.phase === "launching") {
    return "booting";
  }

  return "not-ready";
}

function createHandshakeDetails(
  status: PacketTracerRuntimeStatusResult,
  observation: ExAppSessionObservation
): Record<string, string> {
  const details: Record<string, string> = {
    executable: PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE,
    sessionDiscovery: observation.method,
    sessionDiscoveryState: observation.state,
    ipcPortRange: describePacketTracerIpcPortRange(),
    runtimePhase: status.phase,
    runtimeProbe: status.probe,
    isInstalled: String(status.isInstalled),
    isRunning: String(status.isRunning),
    canLaunch: String(status.canLaunch),
    recoveryAvailable: String(status.recoveryAvailable),
    processCount: String(status.processes.length),
    launcherPath: status.paths.launcherPath,
    appImagePath: status.paths.appImagePath,
    resetHelperPath: status.paths.resetHelperPath,
    launcherAvailable: String(status.paths.launcherAvailable),
    appImageAvailable: String(status.paths.appImageAvailable),
    resetHelperAvailable: String(status.paths.resetHelperAvailable),
    experimentalInvokePayloadFeature: EXPERIMENTAL_LOCAL_MESSAGE_FEATURE,
    experimentalInvokePayloadMode: EXPERIMENTAL_LOCAL_MESSAGE_MODE,
    experimentalInvokePayloadTransportModel: "local-experimental-exapp-string-message",
    experimentalInvokePayloadOfficial: "false",
    experimentalInvokePayloadOptInField: "payload.experimental",
    experimentalInvokePayloadReadOnlyOnly: "true",
    experimentalInvokePayloadOperations: "handshake,echo,list_devices,list_components,list_ports,list_links,get_device_detail,read_interface_status,read_port_power_state",
    experimentalInvokePayloadPreferredProofOperations: "list_devices,list_components,list_ports,list_links,get_device_detail,read_interface_status,read_port_power_state,packettracer_status",
    experimentalInvokePayloadWriteEnabled: "false",
    experimentalInvokePayloadStatus:
      observation.method === "local-experimental-bridge-range" && observation.state === "connected"
        ? "available-with-opt-in"
        : "disabled-until-local-experimental-bridge-observed",
    ipcListenerCount: String(observation.listeners.length),
    ipcReachable: String(observation.isReachable),
    ...(observation.sessionId ? { sessionId: observation.sessionId } : {}),
    ...(observation.instanceId ? { instanceId: observation.instanceId } : {}),
    ...(observation.selectedListener
      ? {
          ipcListenerPort: String(observation.selectedListener.port),
          ipcListenerAddress: observation.selectedListener.address,
          ipcListenerPid: String(observation.selectedListener.pid),
          ipcListenerInode: observation.selectedListener.inode,
          sessionIdentitySource:
            observation.method === "local-experimental-bridge-range"
              ? "observed-local-experimental-bridge-endpoint"
              : "observed-ipc-listener-endpoint",
          instanceIdentitySource:
            observation.method === "local-experimental-bridge-range"
              ? "local-experimental-handshake-reply"
              : "observed-packet-tracer-process-pid",
        }
      : {}),
    ...(observation.reason ? { ipcReason: observation.reason } : {}),
    ...(status.problem
      ? {
          runtimeProblemCode: status.problem.code,
          runtimeProblemMessage: status.problem.message,
        }
      : {}),
  };

  if (observation.selectedListener?.address.startsWith("tcp6:")) {
    details.ipcListenerAddressFamily = "tcp6";
  } else if (observation.selectedListener) {
    details.ipcListenerAddressFamily = "tcp4";
  }

  return details;
}

function createCapabilitiesPayload(handshake: ExAppHandshake) {
  return createExAppCapabilityResult({
    readiness: handshake.readiness,
    details: handshake.details,
  });
}

function createReadStatusPayload(
  handshake: ExAppHandshake,
  status: PacketTracerRuntimeStatusResult | null
): BridgeReadStatusSuccessPayload {
  return createReadStatusResult({
    handshakeReadiness: handshake.readiness,
    backendRequestId: handshake.backendRequestId,
    handshakeDetails: handshake.details,
    runtimeStatus: status,
    metadata: {
      executable: PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE,
      sessionDiscovery: handshake.details.sessionDiscovery ?? "packet-tracer-ipc-port-range",
    },
  });
}

async function discoverPacketTracerExAppSession(
  status: PacketTracerRuntimeStatusResult
): Promise<ExAppSessionObservation> {
  const localExperimentalBridge = await discoverLocalExperimentalBridge();
  if (localExperimentalBridge !== null) {
    return {
      method: "local-experimental-bridge-range",
      state: "connected",
      listeners: [localExperimentalBridge],
      selectedListener: localExperimentalBridge,
      isReachable: true,
      sessionId: `local-experimental-bridge:${localExperimentalBridge.address}:${localExperimentalBridge.port}`,
      instanceId: localExperimentalBridge.instanceId ?? "",
      reason: undefined,
    };
  }

  if (!status.isInstalled || status.phase === "not-installed") {
    return {
      method: "packet-tracer-ipc-port-range",
      state: "unavailable",
      listeners: [],
      selectedListener: null,
      isReachable: false,
      sessionId: "",
      instanceId: "",
      reason: "packet-tracer-not-installed",
    };
  }

  if (!status.isRunning || status.processes.length === 0) {
    return {
      method: "packet-tracer-ipc-port-range",
      state: "disconnected",
      listeners: [],
      selectedListener: null,
      isReachable: false,
      sessionId: "",
      instanceId: "",
      reason: "packet-tracer-not-running",
    };
  }

  if (process.platform === "win32") {
    return {
      method: "packet-tracer-ipc-port-range",
      state: "disconnected",
      listeners: [],
      selectedListener: null,
      isReachable: false,
      sessionId: "",
      instanceId: "",
      reason: "ipc-owner-observation-unsupported-on-win32",
    };
  }

  try {
    const listeners = await discoverPacketTracerOwnedIpcListeners(status.processes.map((process) => process.pid));
    const selectedListener = listeners[0] ?? null;

    if (selectedListener === null) {
      return {
        method: "packet-tracer-ipc-port-range",
        state: "disconnected",
        listeners,
        selectedListener: null,
        isReachable: false,
        sessionId: "",
        instanceId: "",
        reason: "ipc-listener-not-observed",
      };
    }

    const isReachable = await probeLocalIpcPort(selectedListener.port);
    const identity = createObservedSessionIdentity(selectedListener);

    return {
      method: "packet-tracer-ipc-port-range",
      state: isReachable ? "connected" : "listening",
      listeners,
      selectedListener,
      isReachable,
      sessionId: identity.sessionId,
      instanceId: identity.instanceId,
      reason: isReachable ? undefined : "ipc-listener-not-reachable",
    };
  } catch (error) {
    return {
      method: "packet-tracer-ipc-port-range",
      state: "degraded",
      listeners: [],
      selectedListener: null,
      isReachable: false,
      sessionId: "",
      instanceId: "",
      reason: `ipc-observation-failed:${formatError(error)}`,
    };
  }
}

async function discoverLocalExperimentalBridge(): Promise<ExAppIpcListenerObservation | null> {
  const preferredPort = parsePreferredLocalExperimentalBridgePort();
  if (preferredPort !== null) {
    for (const host of LOCAL_PROBE_HOSTS) {
      const handshake = await probeLocalExperimentalBridge(
        host,
        preferredPort,
        LOCAL_EXPERIMENTAL_HANDSHAKE_TIMEOUT_MS
      );
      if (handshake !== null) {
        const observedBridge: ExAppIpcListenerObservation = {
          pid: 0,
          port: preferredPort,
          inode: `local-experimental-bridge:${host}:${preferredPort}`,
          address: host,
          ...(handshake.instanceId !== null ? { instanceId: handshake.instanceId } : {}),
        };
        lastObservedLocalExperimentalBridge = observedBridge;
        lastObservedLocalExperimentalBridgeMisses = 0;
        return observedBridge;
      }
    }
  }

  const cachedBridge = await probeCachedLocalExperimentalBridge();
  if (cachedBridge !== null) {
    return cachedBridge;
  }

  for (const host of LOCAL_PROBE_HOSTS) {
    for (
      let port = LOCAL_EXPERIMENTAL_BRIDGE_PORT_RANGE.first;
      port <= LOCAL_EXPERIMENTAL_BRIDGE_PORT_RANGE.last;
      port += 1
    ) {
      const handshake = await probeLocalExperimentalBridge(host, port, LOCAL_EXPERIMENTAL_SCAN_TIMEOUT_MS);
      if (handshake !== null) {
        const observedBridge: ExAppIpcListenerObservation = {
          pid: 0,
          port,
          inode: `local-experimental-bridge:${host}:${port}`,
          address: host,
          ...(handshake.instanceId !== null ? { instanceId: handshake.instanceId } : {}),
        };
        lastObservedLocalExperimentalBridge = observedBridge;
        lastObservedLocalExperimentalBridgeMisses = 0;
        return observedBridge;
      }
    }
  }

  return null;
}

async function probeCachedLocalExperimentalBridge(): Promise<ExAppIpcListenerObservation | null> {
  if (lastObservedLocalExperimentalBridge == null) {
    return null;
  }

  const handshake = await probeLocalExperimentalBridge(
    lastObservedLocalExperimentalBridge.address,
    lastObservedLocalExperimentalBridge.port
  );

  if (handshake === null) {
    lastObservedLocalExperimentalBridgeMisses += 1;

    if (lastObservedLocalExperimentalBridgeMisses >= LOCAL_EXPERIMENTAL_MAX_CACHE_MISSES) {
      lastObservedLocalExperimentalBridge = null;
      lastObservedLocalExperimentalBridgeMisses = 0;
    }

    return null;
  }

  lastObservedLocalExperimentalBridge = {
    pid: lastObservedLocalExperimentalBridge.pid,
    port: lastObservedLocalExperimentalBridge.port,
    inode: lastObservedLocalExperimentalBridge.inode,
    address: lastObservedLocalExperimentalBridge.address,
    ...(handshake.instanceId !== null ? { instanceId: handshake.instanceId } : {}),
  };
  lastObservedLocalExperimentalBridgeMisses = 0;
  return lastObservedLocalExperimentalBridge;
}

async function probeLocalExperimentalBridge(
  host: string,
  port: number,
  timeoutMs = LOCAL_EXPERIMENTAL_HANDSHAKE_TIMEOUT_MS
): Promise<LocalExperimentalBridgeProbeResult | null> {
  return new Promise((resolve) => {
    const socket = new Socket();
    let settled = false;
    let observed = "";

    const finish = (value: LocalExperimentalBridgeProbeResult | null) => {
      if (settled) {
        return;
      }

      settled = true;
      socket.removeAllListeners();
      socket.destroy();
      resolve(value);
    };

    socket.setTimeout(timeoutMs);
    socket.once("connect", () => {
      socket.write(
        `${LOCAL_EXPERIMENTAL_PROTOCOL_PREFIX}|${LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE}\n`,
        "utf8"
      );
    });
    socket.on("data", (chunk: Buffer) => {
      observed += chunk.toString("utf8");
      const parsedReply = parseLocalExperimentalReply(observed);
      if (
        parsedReply?.operation === LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE
      ) {
        finish({
          instanceId: readHandshakeInstanceId(parsedReply.payload),
        });
      }
    });
    socket.once("end", () => {
      const parsedReply = parseLocalExperimentalReply(observed);
      if (
        parsedReply?.operation === LOCAL_EXPERIMENTAL_OPERATION_HANDSHAKE
      ) {
        finish({
          instanceId: readHandshakeInstanceId(parsedReply.payload),
        });
        return;
      }

      finish(null);
    });
    socket.once("timeout", () => finish(null));
    socket.once("error", () => finish(null));
    socket.connect(port, host);
  });
}

function createObservedSessionIdentity(
  listener: ExAppIpcListenerObservation
): Pick<ExAppSessionObservation, "sessionId" | "instanceId"> {
  return {
    sessionId: `ipc-endpoint:${listener.address}:${listener.port}`,
    instanceId: `packet-tracer-pid:${listener.pid}`,
  };
}

async function discoverPacketTracerOwnedIpcListeners(
  pids: number[]
): Promise<ExAppIpcListenerObservation[]> {
  const listenersByInode = await readPacketTracerIpcListenersByInode();
  const observations = new Map<string, ExAppIpcListenerObservation>();

  for (const pid of new Set(pids)) {
    const directoryEntries = await readdir(`/proc/${pid}/fd`);
    for (const entry of directoryEntries) {
      let target: string;
      try {
        target = await readlink(`/proc/${pid}/fd/${entry}`);
      } catch {
        continue;
      }

      const inodeMatch = target.match(/^socket:\[(\d+)\]$/);
      if (!inodeMatch) {
        continue;
      }

      const listener = listenersByInode.get(inodeMatch[1]);
      if (!listener) {
        continue;
      }

      observations.set(`${pid}:${listener.inode}`, {
        pid,
        port: listener.port,
        inode: listener.inode,
        address: listener.address,
      });
    }
  }

  return [...observations.values()].sort((left, right) => left.port - right.port || left.pid - right.pid);
}

async function readPacketTracerIpcListenersByInode(): Promise<Map<string, ProcNetListenerRecord>> {
  const records = new Map<string, ProcNetListenerRecord>();

  for (const procNetPath of ["/proc/net/tcp", "/proc/net/tcp6"]) {
    let content: string;
    try {
      content = await readFile(procNetPath, "utf8");
    } catch {
      continue;
    }

    for (const record of parseProcNetListeners(content)) {
      if (!isPacketTracerIpcPort(record.port)) {
        continue;
      }

      records.set(record.inode, record);
    }
  }

  return records;
}

function parseProcNetListeners(content: string): ProcNetListenerRecord[] {
  const records: ProcNetListenerRecord[] = [];

  for (const line of content.split(/\r?\n/).slice(1)) {
    const columns = line.trim().split(/\s+/);
    if (columns.length < 10) {
      continue;
    }

    if (columns[3] !== "0A") {
      continue;
    }

    const [addressHex, portHex] = columns[1].split(":");
    if (!addressHex || !portHex) {
      continue;
    }

    records.push({
      address: decodeProcNetAddress(addressHex),
      port: Number.parseInt(portHex, 16),
      inode: columns[9],
    });
  }

  return records;
}

function decodeProcNetAddress(hex: string): string {
  if (hex.length === 8) {
    const octets = hex.match(/../g);
    if (!octets) {
      return hex;
    }

    return octets
      .reverse()
      .map((octet) => Number.parseInt(octet, 16))
      .join(".");
  }

  if (hex.length === 32) {
    if (/^0+$/.test(hex)) {
      return "::";
    }

    return `tcp6:${hex}`;
  }

  return hex;
}

async function probeLocalIpcPort(port: number): Promise<boolean> {
  for (const host of LOCAL_PROBE_HOSTS) {
    if (await probeTcpPort(host, port)) {
      return true;
    }
  }

  return false;
}

function probeTcpPort(host: string, port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = new Socket();

    const settle = (value: boolean): void => {
      socket.removeAllListeners();
      socket.destroy();
      resolve(value);
    };

    socket.setTimeout(250);
    socket.once("connect", () => {
      settle(true);
    });
    socket.once("timeout", () => {
      settle(false);
    });
    socket.once("error", () => {
      settle(false);
    });

    socket.connect(port, host);
  });
}

function isPacketTracerIpcPort(port: number): boolean {
  return port >= PACKET_TRACER_IPC_PORT_RANGE.first && port <= PACKET_TRACER_IPC_PORT_RANGE.last;
}

function describePacketTracerIpcPortRange(): string {
  return `${PACKET_TRACER_IPC_PORT_RANGE.first}-${PACKET_TRACER_IPC_PORT_RANGE.last}`;
}

async function readJsonFromStdin(stdin: NodeJS.ReadableStream): Promise<unknown> {
  return new Promise((resolve, reject) => {
    let buffer = "";

    stdin.setEncoding("utf8");
    stdin.resume();
    stdin.on("data", (chunk: string) => {
      buffer += chunk;
    });
    stdin.once("end", () => {
      try {
        resolve(JSON.parse(buffer));
      } catch (error) {
        reject(error);
      }
    });
    stdin.once("error", reject);
  });
}

function writeJson(stdout: NodeJS.WritableStream, payload: unknown): void {
  stdout.write(JSON.stringify(payload));
}

function resolveMode(argv: string[]): ExAppBridgeMode | null {
  if (argv.includes(EXAPP_BRIDGE_MODE_ARGS.handshake)) {
    return "handshake";
  }

  if (argv.includes(EXAPP_BRIDGE_MODE_ARGS.invoke)) {
    return "invoke";
  }

  return null;
}

function createBackendRequestId(
  statusOrPhase: PacketTracerRuntimeStatusResult | string,
  observation?: ExAppSessionObservation
): string {
  if (typeof statusOrPhase === "string") {
    return `${PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE}:${statusOrPhase}`;
  }

  const suffix = observation?.selectedListener
    ? `${observation.state}:${observation.selectedListener.port}`
    : observation?.state ?? statusOrPhase.phase;

  return `${PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE}:${suffix}`;
}

function durationSince(now: () => number, startedAt: number): number {
  return Math.max(0, Math.round(now() - startedAt));
}

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

class ExAppSessionAttemptUnavailableError extends Error {
  constructor(
    readonly host: string,
    readonly port: number,
    error: unknown
  ) {
    super(formatError(error));
  }
}

class ExAppSessionAttemptTimeoutError extends Error {
  constructor(
    readonly host: string,
    readonly port: number,
    readonly timeoutMs: number
  ) {
    super(`Session contact attempt exceeded ${timeoutMs}ms.`);
  }
}
