import {
  DEFAULT_BRIDGE_TIMEOUT_MS,
  type BridgeQueryCapabilitiesSuccessPayload,
  type BridgeReadinessState,
} from "../bridge/index.js";

type ExAppHandshakeDetails = Record<string, string>;

interface CreateExAppCapabilityResultOptions {
  readiness: BridgeReadinessState;
  details: ExAppHandshakeDetails;
}

export function createExAppCapabilityResult(
  options: CreateExAppCapabilityResultOptions
): BridgeQueryCapabilitiesSuccessPayload {
  const sessionObserved = hasObservedReachableSession(options.details);
  const sessionIdentity = readObservedSessionIdentity(options.details);

  return {
    schemaVersion: "v1",
    backend: "exapp",
    capabilities: {
      session: {
        canConnect: sessionObserved,
        canIdentifySession: sessionIdentity.observed,
        sessionId: sessionIdentity.sessionId,
        instanceId: sessionIdentity.instanceId,
        isRemote: false,
      },
      runtime: {
        canMessagePt: false,
        canInvokeNoop: false,
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
    catalog: {
      devices: [],
      cables: [],
      configOperations: [],
    },
    limits: {
      maxRequestBytes: 0,
      maxResponseBytes: 0,
      timeoutMs: DEFAULT_BRIDGE_TIMEOUT_MS,
    },
    notes: createCapabilityNotes(options.readiness, sessionObserved, sessionIdentity.observed),
  };
}

function hasObservedReachableSession(details: ExAppHandshakeDetails): boolean {
  return details.sessionDiscoveryState === "connected" && details.ipcReachable === "true";
}

function readObservedSessionIdentity(details: ExAppHandshakeDetails): {
  observed: boolean;
  sessionId: string;
  instanceId: string;
} {
  const sessionId = details.sessionId ?? "";
  const instanceId = details.instanceId ?? "";

  return {
    observed: sessionId.length > 0 && instanceId.length > 0,
    sessionId,
    instanceId,
  };
}

function createCapabilityNotes(
  readiness: CreateExAppCapabilityResultOptions["readiness"],
  sessionObserved: boolean,
  sessionIdentityObserved: boolean
): string[] {
  const notes = [
    "Capability booleans stay false unless the ExApp bridge can prove them from current bridge/session observations.",
    "Catalogs stay empty until the bridge can discover canonical Packet Tracer device, module, cable, or config-operation inventories.",
  ];

  if (sessionObserved) {
    notes.push(
      "Session connectivity is backed by a successful local reachability probe against the currently observed ExApp endpoint."
    );
  } else {
    notes.push(
      `Session connectivity remains conservative because the current handshake readiness (${readiness}) does not prove a reachable Packet Tracer IPC session.`
    );
  }

  if (!sessionIdentityObserved) {
    notes.push(
      "Session identifiers stay empty unless the bridge can derive them from an observed listener endpoint and owning Packet Tracer PID."
    );
  }

  notes.push(
    "A local-only non-official experimental message mode exists only behind payload.experimental with feature 'local-experimental-message' and mode 'string'."
  );
  notes.push(
    "For automation proof, prefer structured state surfaces such as list_devices, list_components, list_ports, list_links, get_device_detail, read_interface_status, read_port_power_state, and packettracer_status.bridge metadata."
  );
  notes.push(
    sessionObserved
        ? "That experimental mode is disabled by default, limited to handshake, echo, list_devices, list_components, list_ports, list_links, get_device_detail, read_interface_status, and read_port_power_state, and must not be treated as official Cisco payload protocol support."
        : `That experimental mode remains unavailable until the bridge can prove a reachable Packet Tracer IPC session; current readiness is ${readiness}.`
  );

  return notes;
}
