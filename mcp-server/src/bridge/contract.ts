import { z } from "zod";

export const BRIDGE_PROTOCOL_VERSION = "2026-04-15" as const;
export const DEFAULT_BRIDGE_TIMEOUT_MS = 30_000;
export const MIN_BRIDGE_TIMEOUT_MS = 100;
export const MAX_BRIDGE_TIMEOUT_MS = 120_000;

export const bridgeBackendIdSchema = z.enum(["exapp"]);
export type BridgeBackendId = z.infer<typeof bridgeBackendIdSchema>;

export const bridgeReadinessStateSchema = z.enum([
  "booting",
  "ready",
  "not-ready",
  "degraded",
  "unavailable",
]);
export type BridgeReadinessState = z.infer<typeof bridgeReadinessStateSchema>;

export const bridgeOperationSchema = z.enum(["query_capabilities", "invoke", "read_status"]);
export type BridgeOperation = z.infer<typeof bridgeOperationSchema>;

export const bridgeErrorCodeSchema = z.enum([
  "timeout",
  "unavailable-backend",
  "malformed-response",
  "not-ready",
]);
export type BridgeErrorCode = z.infer<typeof bridgeErrorCodeSchema>;

export const bridgeTimeoutPolicySchema = z
  .object({
    defaultMs: z.literal(DEFAULT_BRIDGE_TIMEOUT_MS),
    minMs: z.literal(MIN_BRIDGE_TIMEOUT_MS),
    maxMs: z.literal(MAX_BRIDGE_TIMEOUT_MS),
    strategy: z.literal("fail-closed"),
  })
  .readonly();
export type BridgeTimeoutPolicy = z.infer<typeof bridgeTimeoutPolicySchema>;

export const BRIDGE_TIMEOUT_POLICY: BridgeTimeoutPolicy = {
  defaultMs: DEFAULT_BRIDGE_TIMEOUT_MS,
  minMs: MIN_BRIDGE_TIMEOUT_MS,
  maxMs: MAX_BRIDGE_TIMEOUT_MS,
  strategy: "fail-closed",
};

export const bridgeInvokePayloadSchema = z
  .object({
    kind: z.literal("invoke").default("invoke"),
    entrypoint: z.string().min(1).optional(),
    args: z.array(z.string()).default([]),
    stdin: z.string().optional(),
    values: z.record(z.string(), z.union([z.string(), z.number(), z.boolean()])).optional(),
    experimental: z
      .discriminatedUnion("operation", [
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("handshake"),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("echo"),
            payload: z.string(),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("list_devices"),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("list_components"),
            category: z.string().min(1).optional(),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("list_ports"),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("list_links"),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("get_device_detail"),
            selector: z.string().min(1),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("read_interface_status"),
            selector: z.string().min(1),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("read_port_power_state"),
            deviceSelector: z.string().min(1),
            portSelector: z.string().min(1),
            allowNonReadOnly: z.literal(false).default(false),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("add_device"),
            deviceType: z.string().min(1),
            model: z.string().min(1),
            x: z.number(),
            y: z.number(),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("connect_devices"),
            leftDeviceSelector: z.string().min(1),
            leftPortSelector: z.string().min(1),
            rightDeviceSelector: z.string().min(1),
            rightPortSelector: z.string().min(1),
            connectionType: z.string().min(1).optional(),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("set_interface_ip"),
            deviceSelector: z.string().min(1),
            portSelector: z.string().min(1),
            ipAddress: z.string().min(1),
            subnetMask: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("set_default_gateway"),
            deviceSelector: z.string().min(1),
            gateway: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("add_static_route"),
            deviceSelector: z.string().min(1),
            network: z.string().min(1),
            subnetMask: z.string().min(1),
            nextHop: z.string().min(1),
            portSelector: z.string().optional(),
            adminDistance: z.number().int().positive().optional(),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("run_device_cli"),
            deviceSelector: z.string().min(1),
            cliMode: z.string().min(1).optional(),
            command: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("set_port_power_state"),
            deviceSelector: z.string().min(1),
            portSelector: z.string().min(1),
            powerOn: z.boolean().optional(),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("run_ping"),
            deviceSelector: z.string().min(1),
            destinationIpAddress: z.string().min(1),
            repeatCount: z.number().int().positive().optional(),
            timeoutSeconds: z.number().int().positive().optional(),
            packetSize: z.number().int().positive().optional(),
            sourcePortName: z.string().optional(),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("probe_terminal_transcript"),
            deviceSelector: z.string().min(1),
            cliMode: z.string().min(1).optional(),
            command: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("remove_device"),
            selector: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("delete_link"),
            selector: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("get_device_module_layout"),
            deviceSelector: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("add_module"),
            deviceSelector: z.string().min(1),
            slot: z.string().min(1),
            moduleType: z.string().min(1),
            model: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("remove_module"),
            deviceSelector: z.string().min(1),
            slot: z.string().min(1),
            moduleType: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
        z
          .object({
            feature: z.literal("local-experimental-message"),
            mode: z.literal("string"),
            operation: z.literal("add_module_at"),
            deviceSelector: z.string().min(1),
            parentModulePath: z.string().optional(),
            slotIndex: z.number().int().min(0),
            model: z.string().min(1),
            allowNonReadOnly: z.literal(true).default(true),
          })
          .strict(),
      ])
      .optional(),
  })
  .strict();
export type BridgeInvokePayload = z.infer<typeof bridgeInvokePayloadSchema>;

export const bridgeQueryCapabilitiesPayloadSchema = z
  .object({
    kind: z.literal("query_capabilities").default("query_capabilities"),
    includeCatalog: z.boolean().default(true),
    includeRuntimeDiagnostics: z.boolean().default(true),
  })
  .strict();
export type BridgeQueryCapabilitiesPayload = z.infer<typeof bridgeQueryCapabilitiesPayloadSchema>;

export const bridgeReadStatusPayloadSchema = z
  .object({
    kind: z.literal("read_status").default("read_status"),
    includeRuntimeDiagnostics: z.boolean().default(true),
    includeHandshakeDetails: z.boolean().default(true),
  })
  .strict();
export type BridgeReadStatusPayload = z.infer<typeof bridgeReadStatusPayloadSchema>;

export const bridgeRequestPayloadSchema = z.union([
  bridgeInvokePayloadSchema,
  bridgeQueryCapabilitiesPayloadSchema,
  bridgeReadStatusPayloadSchema,
]);
export type BridgeRequestPayload = z.infer<typeof bridgeRequestPayloadSchema>;

const bridgeRequestBaseSchema = z.object({
  protocolVersion: z.literal(BRIDGE_PROTOCOL_VERSION),
  correlationId: z.string().min(1),
  backend: bridgeBackendIdSchema,
  timeoutMs: z.number().int().min(MIN_BRIDGE_TIMEOUT_MS).max(MAX_BRIDGE_TIMEOUT_MS),
});

export const bridgeInvokeRequestSchema = bridgeRequestBaseSchema
  .extend({
    operation: z.literal("invoke"),
    payload: bridgeInvokePayloadSchema,
  })
  .strict();
export type BridgeInvokeRequest = z.infer<typeof bridgeInvokeRequestSchema>;

export const bridgeQueryCapabilitiesRequestSchema = bridgeRequestBaseSchema
  .extend({
    operation: z.literal("query_capabilities"),
    payload: bridgeQueryCapabilitiesPayloadSchema,
  })
  .strict();
export type BridgeQueryCapabilitiesRequest = z.infer<typeof bridgeQueryCapabilitiesRequestSchema>;

export const bridgeReadStatusRequestSchema = bridgeRequestBaseSchema
  .extend({
    operation: z.literal("read_status"),
    payload: bridgeReadStatusPayloadSchema,
  })
  .strict();
export type BridgeReadStatusRequest = z.infer<typeof bridgeReadStatusRequestSchema>;

export const bridgeRequestSchema = z.discriminatedUnion("operation", [
  bridgeInvokeRequestSchema,
  bridgeQueryCapabilitiesRequestSchema,
  bridgeReadStatusRequestSchema,
]);
export type BridgeRequest = z.infer<typeof bridgeRequestSchema>;

export const bridgeInvokeSuccessPayloadSchema = z
  .object({
    stdout: z.string(),
    stderr: z.string(),
    exitCode: z.number().int(),
    backendRequestId: z.string().min(1).optional(),
    metadata: z.record(z.string(), z.string()).default({}),
  })
  .strict();
export type BridgeInvokeSuccessPayload = z.infer<typeof bridgeInvokeSuccessPayloadSchema>;

export const bridgeCapabilitiesSessionSchema = z
  .object({
    canConnect: z.boolean(),
    canIdentifySession: z.boolean(),
    sessionId: z.string(),
    instanceId: z.string(),
    isRemote: z.boolean(),
  })
  .strict();

export const bridgeCapabilitiesRuntimeSchema = z
  .object({
    canMessagePt: z.boolean(),
    canInvokeNoop: z.boolean(),
    canEnumerateObjects: z.boolean(),
    canEnumeratePorts: z.boolean(),
    canEnumerateLinks: z.boolean(),
  })
  .strict();

export const bridgeCapabilitiesTopologySchema = z
  .object({
    canCreateDevice: z.boolean(),
    canCreateLink: z.boolean(),
    canDeleteLink: z.boolean(),
    canAutoConnect: z.boolean(),
  })
  .strict();

export const bridgeCapabilitiesConfigSchema = z
  .object({
    canSetPortPower: z.boolean(),
    canSetBandwidth: z.boolean(),
    canSetClockRate: z.boolean(),
    canSetMacAddress: z.boolean(),
    canApplyCommandBatch: z.boolean(),
    canReadOperationalStatus: z.boolean(),
  })
  .strict();

export const bridgeCapabilitiesSchema = z
  .object({
    session: bridgeCapabilitiesSessionSchema,
    runtime: bridgeCapabilitiesRuntimeSchema,
    topology: bridgeCapabilitiesTopologySchema,
    config: bridgeCapabilitiesConfigSchema,
  })
  .strict();

export const bridgeCatalogDeviceSchema = z
  .object({
    typeId: z.string(),
    model: z.string(),
    displayName: z.string(),
    supportedModules: z.array(z.string()).default([]),
    requiredRuntimeFeatures: z.array(z.string()).default([]),
  })
  .strict();

export const bridgeCatalogCableSchema = z
  .object({
    connectionType: z.string(),
    displayName: z.string(),
    compatiblePortKinds: z.array(z.string()).default([]),
  })
  .strict();

export const bridgeCatalogConfigOperationSchema = z
  .object({
    operationId: z.string(),
    targetKind: z.enum(["device", "port", "link"]),
    mode: z.enum(["setter", "command", "template"]),
    requiredFields: z.array(z.string()).default([]),
  })
  .strict();

export const bridgeCapabilityCatalogSchema = z
  .object({
    devices: z.array(bridgeCatalogDeviceSchema).default([]),
    cables: z.array(bridgeCatalogCableSchema).default([]),
    configOperations: z.array(bridgeCatalogConfigOperationSchema).default([]),
  })
  .strict();

export const bridgeCapabilityLimitsSchema = z
  .object({
    maxRequestBytes: z.number().int().nonnegative(),
    maxResponseBytes: z.number().int().nonnegative(),
    timeoutMs: z.number().int().positive(),
  })
  .strict();

export const bridgeQueryCapabilitiesSuccessPayloadSchema = z
  .object({
    schemaVersion: z.literal("v1"),
    backend: bridgeBackendIdSchema,
    capabilities: bridgeCapabilitiesSchema,
    catalog: bridgeCapabilityCatalogSchema,
    limits: bridgeCapabilityLimitsSchema,
    notes: z.array(z.string()),
  })
  .strict();
export type BridgeQueryCapabilitiesSuccessPayload = z.infer<
  typeof bridgeQueryCapabilitiesSuccessPayloadSchema
>;

export const bridgeReadStatusRuntimeDiagnosticsSchema = z
  .object({
    observed: z.boolean(),
    phase: z.string(),
    isInstalled: z.boolean(),
    isRunning: z.boolean(),
    canLaunch: z.boolean(),
    recoveryAvailable: z.boolean(),
    probe: z.string(),
  })
  .strict();

export const bridgeReadStatusSuccessPayloadSchema = z
  .object({
    bridge: z
      .object({
        readiness: bridgeReadinessStateSchema,
        backendRequestId: z.string().optional(),
        details: z.record(z.string(), z.string()).default({}),
      })
      .strict(),
    runtime: bridgeReadStatusRuntimeDiagnosticsSchema.optional(),
    metadata: z.record(z.string(), z.string()).default({}),
  })
  .strict();
export type BridgeReadStatusSuccessPayload = z.infer<typeof bridgeReadStatusSuccessPayloadSchema>;

export const bridgeSuccessPayloadSchema = z.union([
  bridgeInvokeSuccessPayloadSchema,
  bridgeQueryCapabilitiesSuccessPayloadSchema,
  bridgeReadStatusSuccessPayloadSchema,
]);
export type BridgeSuccessPayload = z.infer<typeof bridgeSuccessPayloadSchema>;

export const bridgeErrorPayloadSchema = z
  .object({
    code: bridgeErrorCodeSchema,
    message: z.string().min(1),
    retryable: z.boolean(),
    details: z.record(z.string(), z.string()).default({}),
  })
  .strict();
export type BridgeErrorPayload = z.infer<typeof bridgeErrorPayloadSchema>;

const bridgeResponseBaseSchema = z
  .object({
    protocolVersion: z.literal(BRIDGE_PROTOCOL_VERSION),
    correlationId: z.string().min(1),
    backend: bridgeBackendIdSchema,
    operation: bridgeOperationSchema,
    readiness: bridgeReadinessStateSchema,
    durationMs: z.number().int().nonnegative(),
  })
  .strict();

export const bridgeInvokeSuccessResponseSchema = bridgeResponseBaseSchema
  .extend({
    operation: z.literal("invoke"),
    ok: z.literal(true),
    result: bridgeInvokeSuccessPayloadSchema,
  })
  .strict();

export const bridgeQueryCapabilitiesSuccessResponseSchema = bridgeResponseBaseSchema
  .extend({
    operation: z.literal("query_capabilities"),
    ok: z.literal(true),
    result: bridgeQueryCapabilitiesSuccessPayloadSchema,
  })
  .strict();

export const bridgeReadStatusSuccessResponseSchema = bridgeResponseBaseSchema
  .extend({
    operation: z.literal("read_status"),
    ok: z.literal(true),
    result: bridgeReadStatusSuccessPayloadSchema,
  })
  .strict();

export const bridgeSuccessResponseSchema = z.discriminatedUnion("operation", [
  bridgeInvokeSuccessResponseSchema,
  bridgeQueryCapabilitiesSuccessResponseSchema,
  bridgeReadStatusSuccessResponseSchema,
]);
export type BridgeSuccessResponse = z.infer<typeof bridgeSuccessResponseSchema>;

export const bridgeInvokeErrorResponseSchema = bridgeResponseBaseSchema
  .extend({
    operation: z.literal("invoke"),
    ok: z.literal(false),
    error: bridgeErrorPayloadSchema,
  })
  .strict();

export const bridgeQueryCapabilitiesErrorResponseSchema = bridgeResponseBaseSchema
  .extend({
    operation: z.literal("query_capabilities"),
    ok: z.literal(false),
    error: bridgeErrorPayloadSchema,
  })
  .strict();

export const bridgeReadStatusErrorResponseSchema = bridgeResponseBaseSchema
  .extend({
    operation: z.literal("read_status"),
    ok: z.literal(false),
    error: bridgeErrorPayloadSchema,
  })
  .strict();

export const bridgeErrorResponseSchema = z.discriminatedUnion("operation", [
  bridgeInvokeErrorResponseSchema,
  bridgeQueryCapabilitiesErrorResponseSchema,
  bridgeReadStatusErrorResponseSchema,
]);
export type BridgeErrorResponse = z.infer<typeof bridgeErrorResponseSchema>;

export const bridgeResponseSchema = z.union([
  bridgeSuccessResponseSchema,
  bridgeErrorResponseSchema,
]);
export type BridgeResponse = z.infer<typeof bridgeResponseSchema>;

export const BRIDGE_BACKEND_IDS = bridgeBackendIdSchema.options;
export const BRIDGE_READINESS_STATES = bridgeReadinessStateSchema.options;
export const BRIDGE_ERROR_CODES = bridgeErrorCodeSchema.options;

export interface PacketTracerBridgeAdapter {
  readonly backend: BridgeBackendId;
  getReadiness(): BridgeReadinessState;
  invoke(request: BridgeRequest): Promise<BridgeResponse>;
}

export function createBridgeSuccessResponse(
  request: BridgeRequest,
  readiness: BridgeReadinessState,
  result: BridgeSuccessPayload,
  durationMs: number
): BridgeSuccessResponse {
  return bridgeSuccessResponseSchema.parse({
    protocolVersion: BRIDGE_PROTOCOL_VERSION,
    correlationId: request.correlationId,
    backend: request.backend,
    operation: request.operation,
    readiness,
    durationMs,
    ok: true,
    result,
  });
}

export function createBridgeErrorResponse(
  request: BridgeRequest,
  readiness: BridgeReadinessState,
  error: BridgeErrorPayload,
  durationMs: number
): BridgeErrorResponse {
  return bridgeErrorResponseSchema.parse({
    protocolVersion: BRIDGE_PROTOCOL_VERSION,
    correlationId: request.correlationId,
    backend: request.backend,
    operation: request.operation,
    readiness,
    durationMs,
    ok: false,
    error,
  });
}

export function validateBridgeRequest(request: unknown): BridgeRequest {
  return bridgeRequestSchema.parse(request);
}

export function validateBridgeResponse(response: unknown): BridgeResponse {
  return bridgeResponseSchema.parse(response);
}
