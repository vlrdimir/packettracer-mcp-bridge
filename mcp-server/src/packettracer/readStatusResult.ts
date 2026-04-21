import type {
  BridgeReadStatusSuccessPayload,
  BridgeReadinessState,
} from "../bridge/index.js";
import type { PacketTracerRuntimeStatusResult } from "./runtime/index.js";

type RuntimeReadinessHint = BridgeReadinessState | "unknown";
type RuntimeReadinessAgreement = "match" | "mismatch" | "unknown" | "not-requested";

interface CreateReadStatusResultOptions {
  handshakeReadiness: BridgeReadinessState;
  backendRequestId?: string;
  handshakeDetails: Record<string, string>;
  runtimeStatus: PacketTracerRuntimeStatusResult | null;
  includeRuntimeDiagnostics?: boolean;
  includeHandshakeDetails?: boolean;
  metadata?: Record<string, string>;
}

export function createReadStatusResult({
  handshakeReadiness,
  backendRequestId,
  handshakeDetails,
  runtimeStatus,
  includeRuntimeDiagnostics = true,
  includeHandshakeDetails = true,
  metadata = {},
}: CreateReadStatusResultOptions): BridgeReadStatusSuccessPayload {
  const runtimeDiagnostics = includeRuntimeDiagnostics
    ? createRuntimeDiagnostics(runtimeStatus)
    : undefined;
  const runtimeReadinessHint = runtimeDiagnostics
    ? inferRuntimeReadinessHint(runtimeDiagnostics)
    : "unknown";
  const runtimeReadinessAgreement = inferRuntimeReadinessAgreement(
    handshakeReadiness,
    runtimeDiagnostics,
    runtimeReadinessHint
  );
  const disagreementReason =
    runtimeReadinessAgreement === "mismatch"
      ? `bridge-handshake:${handshakeReadiness};host-runtime:${runtimeReadinessHint}`
      : undefined;
  const bridgeDetails = includeHandshakeDetails ? { ...handshakeDetails } : {};

  if (runtimeDiagnostics && includeHandshakeDetails) {
    bridgeDetails.runtimeDiagnosticsObserved = String(runtimeDiagnostics.observed);
    bridgeDetails.runtimeReadinessHint = runtimeReadinessHint;
    bridgeDetails.runtimeReadinessAgreement = runtimeReadinessAgreement;

    if (disagreementReason) {
      bridgeDetails.runtimeDisagreement = "true";
      bridgeDetails.runtimeDisagreementReason = disagreementReason;
    }
  }

  return {
    bridge: {
      readiness: handshakeReadiness,
      ...(backendRequestId ? { backendRequestId } : {}),
      details: bridgeDetails,
    },
    ...(runtimeDiagnostics ? { runtime: runtimeDiagnostics } : {}),
    metadata: {
      authority: "bridge-handshake",
      runtimeDiagnosticsIncluded: String(includeRuntimeDiagnostics),
      runtimeReadinessHint,
      runtimeReadinessAgreement,
      diagnosticState: inferDiagnosticState(runtimeDiagnostics, runtimeReadinessAgreement),
      ...(disagreementReason
        ? {
            runtimeDisagreement: "true",
            runtimeDisagreementReason: disagreementReason,
          }
        : runtimeDiagnostics
          ? {
              runtimeDisagreement: "false",
            }
          : {}),
      ...metadata,
    },
  };
}

function createRuntimeDiagnostics(
  status: PacketTracerRuntimeStatusResult | null
): NonNullable<BridgeReadStatusSuccessPayload["runtime"]> {
  if (status) {
    return {
      observed: true,
      phase: status.phase,
      isInstalled: status.isInstalled,
      isRunning: status.isRunning,
      canLaunch: status.canLaunch,
      recoveryAvailable: status.recoveryAvailable,
      probe: status.probe,
    };
  }

  return {
    observed: false,
    phase: "unknown",
    isInstalled: false,
    isRunning: false,
    canLaunch: false,
    recoveryAvailable: false,
    probe: "runtime-status-unavailable",
  };
}

function inferRuntimeReadinessHint(
  runtimeDiagnostics: NonNullable<BridgeReadStatusSuccessPayload["runtime"]>
): RuntimeReadinessHint {
  if (!runtimeDiagnostics.observed) {
    return "unknown";
  }

  if (!runtimeDiagnostics.isInstalled || runtimeDiagnostics.phase === "not-installed") {
    return "unavailable";
  }

  if (runtimeDiagnostics.phase === "failed") {
    return "degraded";
  }

  if (runtimeDiagnostics.phase === "launching") {
    return "booting";
  }

  if (runtimeDiagnostics.isRunning) {
    return "ready";
  }

  return "not-ready";
}

function inferRuntimeReadinessAgreement(
  handshakeReadiness: BridgeReadinessState,
  runtimeDiagnostics: NonNullable<BridgeReadStatusSuccessPayload["runtime"]> | undefined,
  runtimeReadinessHint: RuntimeReadinessHint
): RuntimeReadinessAgreement {
  if (!runtimeDiagnostics) {
    return "not-requested";
  }

  if (!runtimeDiagnostics.observed || runtimeReadinessHint === "unknown") {
    return "unknown";
  }

  return handshakeReadiness === runtimeReadinessHint ? "match" : "mismatch";
}

function inferDiagnosticState(
  runtimeDiagnostics: NonNullable<BridgeReadStatusSuccessPayload["runtime"]> | undefined,
  runtimeReadinessAgreement: RuntimeReadinessAgreement
): string {
  if (!runtimeDiagnostics) {
    return "runtime-diagnostics-omitted";
  }

  if (!runtimeDiagnostics.observed) {
    return "runtime-unobserved";
  }

  return runtimeReadinessAgreement === "mismatch" ? "degraded" : "ok";
}
