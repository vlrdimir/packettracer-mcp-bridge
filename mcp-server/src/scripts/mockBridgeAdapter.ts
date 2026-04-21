import {
  createBridgeSuccessResponse,
  DEFAULT_BRIDGE_TIMEOUT_MS,
  type BridgeBackendId,
  type BridgeErrorCode,
  type BridgeReadinessState,
  type BridgeRequest,
  type BridgeResponse,
  type BridgeSuccessPayload,
  type PacketTracerBridgeAdapter,
  validateBridgeRequest,
  validateBridgeResponse,
} from "../bridge/contract.js";
import { BRIDGE_ERROR_DEFAULTS, createCanonicalBridgeErrorResponse } from "../bridge/errorResponses.js";

export interface MockBridgeAdapterOptions {
  backend: BridgeBackendId;
  readiness?: BridgeReadinessState;
  simulatedDurationMs?: number;
}

export interface MockBridgeFailureScenario {
  code: BridgeErrorCode;
  message?: string;
  retryable?: boolean;
  details?: Record<string, string>;
  readiness?: BridgeReadinessState;
}

export class MockBridgeAdapter implements PacketTracerBridgeAdapter {
  readonly backend: BridgeBackendId;

  private readonly readiness: BridgeReadinessState;
  private readonly simulatedDurationMs: number;

  constructor(options: MockBridgeAdapterOptions) {
    this.backend = options.backend;
    this.readiness = options.readiness ?? "ready";
    this.simulatedDurationMs = options.simulatedDurationMs ?? 0;
  }

  getReadiness(): BridgeReadinessState {
    return this.readiness;
  }

  async invoke(request: BridgeRequest): Promise<BridgeResponse> {
    const validatedRequest = validateBridgeRequest(request);

    if (validatedRequest.backend !== this.backend) {
      return validateBridgeResponse(
        createCanonicalBridgeErrorResponse(validatedRequest, {
          code: "unavailable-backend",
          readiness: "unavailable",
          durationMs: this.simulatedDurationMs,
          message: `Mock bridge adapter is bound to ${this.backend}, but received ${validatedRequest.backend}.`,
          retryable: false,
          details: {
            adapterBackend: this.backend,
            requestBackend: validatedRequest.backend,
          },
        })
      );
    }

    if (this.readiness === "booting" || this.readiness === "not-ready") {
      return validateBridgeResponse(
        createCanonicalBridgeErrorResponse(validatedRequest, {
          code: "not-ready",
          readiness: this.readiness,
          durationMs: this.simulatedDurationMs,
          details: {
            readiness: this.readiness,
          },
        })
      );
    }

    if (this.readiness === "unavailable") {
      return validateBridgeResponse(
        createCanonicalBridgeErrorResponse(validatedRequest, {
          code: "unavailable-backend",
          readiness: this.readiness,
          durationMs: this.simulatedDurationMs,
          details: {
            readiness: this.readiness,
          },
        })
      );
    }

    const response = createBridgeSuccessResponse(
      validatedRequest,
      this.readiness,
      createMockSuccessResult(validatedRequest, this.readiness),
      this.simulatedDurationMs
    );

    return validateBridgeResponse(response);
  }

  async fail(request: BridgeRequest, scenario: MockBridgeFailureScenario): Promise<BridgeResponse> {
    const validatedRequest = validateBridgeRequest(request);

    if (validatedRequest.backend !== this.backend) {
      return validateBridgeResponse(
        createCanonicalBridgeErrorResponse(validatedRequest, {
          code: "unavailable-backend",
          readiness: "unavailable",
          durationMs: this.simulatedDurationMs,
          message: `Mock bridge adapter is bound to ${this.backend}, but received ${validatedRequest.backend}.`,
          retryable: false,
          details: {
            adapterBackend: this.backend,
            requestBackend: validatedRequest.backend,
          },
        })
      );
    }

    const defaults = BRIDGE_ERROR_DEFAULTS[scenario.code];

    const response = createCanonicalBridgeErrorResponse(validatedRequest, {
      code: scenario.code,
      readiness: scenario.readiness ?? this.readiness,
      durationMs: this.simulatedDurationMs,
      message: scenario.message ?? defaults.message,
      retryable: scenario.retryable ?? defaults.retryable,
      details: scenario.details ?? {},
    });

    return validateBridgeResponse(response);
  }
}

export function createMockBridgeAdapter(options: MockBridgeAdapterOptions): MockBridgeAdapter {
  return new MockBridgeAdapter(options);
}

function createMockSuccessResult(
  request: BridgeRequest,
  readiness: BridgeReadinessState
): BridgeSuccessPayload {
  switch (request.operation) {
    case "invoke":
      return {
        stdout: `[mock:${request.backend}] ${request.operation} ${request.payload.entrypoint ?? ""}`.trim(),
        stderr: "",
        exitCode: 0,
        backendRequestId: `${request.backend}:${request.correlationId}`,
        metadata: {
          adapter: "mock",
        },
      };
    case "query_capabilities":
      return {
        schemaVersion: "v1",
        backend: request.backend,
        capabilities: {
          session: {
            canConnect: true,
            canIdentifySession: true,
            sessionId: "mock-session",
            instanceId: "mock-instance",
            isRemote: false,
          },
          runtime: {
            canMessagePt: true,
            canInvokeNoop: true,
            canEnumerateObjects: false,
            canEnumeratePorts: false,
            canEnumerateLinks: false,
          },
          topology: {
            canCreateDevice: false,
            canCreateLink: false,
            canDeleteLink: false,
            canAutoConnect: false,
          },
          config: {
            canSetPortPower: false,
            canSetBandwidth: false,
            canSetClockRate: false,
            canSetMacAddress: false,
            canApplyCommandBatch: false,
            canReadOperationalStatus: false,
          },
        },
        catalog: request.payload.includeCatalog
          ? {
              devices: [],
              cables: [],
              configOperations: [],
            }
          : {
              devices: [],
              cables: [],
              configOperations: [],
            },
        limits: {
          maxRequestBytes: 0,
          maxResponseBytes: 0,
          timeoutMs: DEFAULT_BRIDGE_TIMEOUT_MS,
        },
        notes: ["Mock adapter exposes conservative ExApp-only capabilities."],
      };
    case "read_status":
      return {
        bridge: {
          readiness,
          backendRequestId: `${request.backend}:${request.correlationId}`,
          details: {
            adapter: "mock",
          },
        },
        runtime: request.payload.includeRuntimeDiagnostics
          ? {
              observed: true,
              phase: "ready",
              isInstalled: true,
              isRunning: true,
              canLaunch: false,
              recoveryAvailable: true,
              probe: "mock-runtime",
            }
          : undefined,
        metadata: {
          adapter: "mock",
        },
      };
  }
}
