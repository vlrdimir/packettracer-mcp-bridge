import {
  BRIDGE_ERROR_CODES,
  BRIDGE_PROTOCOL_VERSION,
  BRIDGE_READINESS_STATES,
  BRIDGE_TIMEOUT_POLICY,
} from "../bridge/index.js";
import { PACKET_TRACER_TOOL_NAMES } from "./mcpService.js";
import { PACKET_TRACER_EXAPP_TARGET_ENV } from "./exappBridgeAdapter.js";
import {
  EXAPP_BRIDGE_MODE_ARGS,
  PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE,
} from "./exappBridgeCli.js";

export const PACKET_TRACER_NAMESPACE = {
  name: "packettracer",
  version: "v1",
  publicSurfacePolicy: {
    scope: "compact-orchestrator-focused",
    mainResource: "server://info",
    internalBridgeOperationsArePublicTools: false,
  },
  tools: PACKET_TRACER_TOOL_NAMES,
  publicTools: {
    packettracer_launch: {
      governsPhase: "runtime-start",
      owner: "host-orchestrator",
      purpose: "Launch the host Packet Tracer runtime before ExApp-backed work.",
    },
    packettracer_execute: {
      governsPhase: ["observe", "configure", "verify"],
      owner: "host-orchestrator",
      purpose: "Dispatch ExApp-backed requests through the internal invoke bridge path.",
      internalOperation: "invoke",
    },
    packettracer_reset: {
      governsPhase: "operational-reset",
      owner: "host-orchestrator",
      purpose: "Run the host login reset helper as manual-only operational work.",
    },
    packettracer_status: {
      governsPhase: ["runtime", "readiness", "verify"],
      owner: "host-orchestrator",
      purpose: "Report host runtime state and ExApp-backed readiness context for orchestration.",
      internalOperation: "read_status",
    },
  },
  toolPolicies: {
    packettracer_reset: {
      verification: "operational-manual-only",
      safeForStaticSmoke: false,
      reason: "Runs the host login reset helper and changes Packet Tracer state.",
    },
  },
  architecture: {
    executionModel: "exapp-only",
    publicControlPlane: {
      owner: "host-orchestrator",
      responsibilities: [
        "expose only compact MCP tools and server://info metadata",
        "launch and inspect the host Packet Tracer runtime",
        "route observe, configure, and verify requests through the ExApp bridge",
      ],
    },
    phaseToolMatrix: [
      {
        phase: "runtime-start",
        publicTools: ["packettracer_launch"],
        internalOwner: "host-orchestrator",
        internalOperations: ["launch"],
      },
      {
        phase: "readiness",
        publicTools: ["packettracer_status"],
        internalOwner: "exapp",
        internalOperations: ["read_status"],
      },
      {
        phase: "observe-configure-verify",
        publicTools: ["packettracer_execute", "packettracer_status"],
        internalOwner: "exapp",
        internalOperations: ["invoke", "read_status"],
      },
      {
        phase: "operational-reset",
        publicTools: ["packettracer_reset"],
        internalOwner: "host-orchestrator",
        internalOperations: ["resetLoginState"],
      },
    ],
  },
  bridge: {
    description: "Host-to-executor bridge contract boundary",
    protocolVersion: BRIDGE_PROTOCOL_VERSION,
    activeBackend: "exapp",
    nextPhaseOperations: ["query_capabilities", "invoke", "read_status"],
    authorityModel: {
      readinessSource: "exapp-handshake",
      runtimeRole: "diagnostic-only",
    },
    repoPaths: {
      workspaceRoot: "packettracer-mcp-bridge/",
      mcpServerDocs: "packettracer-mcp-bridge/mcp-server/README.md",
      exappDocs: "packettracer-mcp-bridge/apps/README.md",
    },
    operationModels: {
      query_capabilities: {
        requestPayload: "structured-capability-query",
        successResult: "capability-schema-v1",
      },
      invoke: {
        requestPayload: "structured-entrypoint-invoke",
        successResult: "stdout-stderr-exit-envelope",
        experimentalPayloadMode: {
          feature: "local-experimental-message",
          mode: "string",
          transportModel: "local experimental ExApp string message",
          status: "non-official-disabled-by-default",
          optInField: "payload.experimental",
          preferredProofOperations: ["list_devices", "list_components", "list_ports", "list_links", "get_device_detail", "read_interface_status", "read_port_power_state"],
          restrictions: ["explicit-opt-in-only", "supported-operations-handshake-echo-list_devices-list_components-list_ports-list_links-get_device_detail-read_interface_status-read_port_power_state-add_device-connect_devices-set_interface_ip-set_default_gateway-add_static_route-run_device_cli-set_port_power_state-run_ping-probe_terminal_transcript-remove_device-delete_link-get_device_module_layout-add_module-remove_module-and-add_module_at"],
        },
      },
      read_status: {
        requestPayload: "structured-status-read",
        successResult: "bridge-readiness-plus-runtime-diagnostics-with-disagreement-signaling",
      },
    },
    readinessStates: BRIDGE_READINESS_STATES,
    errorCodes: BRIDGE_ERROR_CODES,
    timeoutPolicy: BRIDGE_TIMEOUT_POLICY,
    exapp: {
      targetEnv: PACKET_TRACER_EXAPP_TARGET_ENV,
      executableName: PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE,
      handshakeCommandArg: EXAPP_BRIDGE_MODE_ARGS.handshake,
      handshakeStdin: "none-required",
      handshakeStdout: "stdout-single-json",
      invokeCommandArg: EXAPP_BRIDGE_MODE_ARGS.invoke,
      requestEncoding: "stdin-single-json",
      responseEncoding: "stdout-single-json",
    },
  },
  runtime: {
    description: "Host Packet Tracer runtime adapter boundary",
    operations: ["resolvePaths", "status", "launch", "resetLoginState"],
  },
} as const;
