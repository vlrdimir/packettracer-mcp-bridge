import {
  createBridgeErrorResponse,
  type BridgeErrorCode,
  type BridgeErrorResponse,
  type BridgeReadinessState,
  type BridgeRequest,
} from "./contract.js";

export const BRIDGE_ERROR_DEFAULTS: Record<
  BridgeErrorCode,
  { message: string; retryable: boolean }
> = {
  timeout: {
    message: "Packet Tracer bridge request exceeded the configured timeout.",
    retryable: true,
  },
  "unavailable-backend": {
    message: "The requested Packet Tracer backend is unavailable.",
    retryable: true,
  },
  "malformed-response": {
    message: "The Packet Tracer backend returned a malformed response.",
    retryable: false,
  },
  "not-ready": {
    message: "The Packet Tracer backend is not ready to accept requests.",
    retryable: true,
  },
};

export interface CanonicalBridgeErrorOptions {
  code: BridgeErrorCode;
  readiness: BridgeReadinessState;
  durationMs: number;
  message?: string;
  retryable?: boolean;
  details?: Record<string, string>;
}

export function createCanonicalBridgeErrorResponse(
  request: BridgeRequest,
  options: CanonicalBridgeErrorOptions
): BridgeErrorResponse {
  const defaults = BRIDGE_ERROR_DEFAULTS[options.code];

  return createBridgeErrorResponse(
    request,
    options.readiness,
    {
      code: options.code,
      message: options.message ?? defaults.message,
      retryable: options.retryable ?? defaults.retryable,
      details: options.details ?? {},
    },
    options.durationMs
  );
}
