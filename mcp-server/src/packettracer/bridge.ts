import {
  BRIDGE_PROTOCOL_VERSION,
  DEFAULT_BRIDGE_TIMEOUT_MS,
  type BridgeRequest,
  type BridgeRequestPayload,
  validateBridgeRequest,
} from "../bridge/index.js";

type BridgeRequestInput = Omit<BridgeRequest, "protocolVersion" | "timeoutMs" | "payload"> & {
  timeoutMs?: number;
  payload: BridgeRequestPayload | Record<string, unknown>;
};

export function createBridgeRequest(input: BridgeRequestInput): BridgeRequest {
  const normalizedTimeoutMs = input.timeoutMs ?? DEFAULT_BRIDGE_TIMEOUT_MS;

  switch (input.operation) {
    case "invoke":
      return validateBridgeRequest({
        protocolVersion: BRIDGE_PROTOCOL_VERSION,
        correlationId: input.correlationId,
        backend: input.backend,
        operation: input.operation,
        timeoutMs: normalizedTimeoutMs,
        payload: normalizeBridgePayload(input.operation, input.payload),
      });
    case "query_capabilities":
      return validateBridgeRequest({
        protocolVersion: BRIDGE_PROTOCOL_VERSION,
        correlationId: input.correlationId,
        backend: input.backend,
        operation: input.operation,
        timeoutMs: normalizedTimeoutMs,
        payload: normalizeBridgePayload(input.operation, input.payload),
      });
    case "read_status":
      return validateBridgeRequest({
        protocolVersion: BRIDGE_PROTOCOL_VERSION,
        correlationId: input.correlationId,
        backend: input.backend,
        operation: input.operation,
        timeoutMs: normalizedTimeoutMs,
        payload: normalizeBridgePayload(input.operation, input.payload),
      });
  }
}

function normalizeBridgePayload(
  operation: BridgeRequest["operation"],
  payload: BridgeRequestInput["payload"]
): BridgeRequestPayload {
  if (operation === "invoke") {
    return {
      kind: "invoke",
      args: [],
      ...(payload as Record<string, unknown>),
    } as BridgeRequestPayload;
  }

  if (operation === "query_capabilities") {
    return {
      kind: "query_capabilities",
      includeCatalog: true,
      includeRuntimeDiagnostics: true,
      ...(payload as Record<string, unknown>),
    } as BridgeRequestPayload;
  }

  return {
    kind: "read_status",
    includeRuntimeDiagnostics: true,
    includeHandshakeDetails: true,
    ...(payload as Record<string, unknown>),
  } as BridgeRequestPayload;
}
