import { Readable, Writable } from "node:stream";

import { createBridgeRequest } from "./bridge.js";
import { runPacketTracerExAppBridgeExecutable } from "./exappBridgeExecutable.js";
import { EXAPP_BRIDGE_MODE_ARGS } from "./exappBridgeCli.js";
import type { BridgeInvokeRequest, BridgeResponse } from "../bridge/index.js";

async function main(): Promise<void> {
  const echoPayload = process.argv[2] ?? "hello from local experimental proof";
  const handshakeResponse = await invokeLocalExperimental({
    operation: "handshake",
    correlationId: "local-experimental-handshake-proof",
  });
  const listDevicesResponse = await invokeLocalExperimental({
    operation: "list_devices",
    correlationId: "local-experimental-list-devices-proof",
  });
  const listComponentsResponse = await invokeLocalExperimental({
    operation: "list_components",
    correlationId: "local-experimental-list-components-proof",
  });
  const listPortsResponse = await invokeLocalExperimental({
    operation: "list_ports",
    correlationId: "local-experimental-list-ports-proof",
  });
  const listLinksResponse = await invokeLocalExperimental({
    operation: "list_links",
    correlationId: "local-experimental-list-links-proof",
  });
  const deviceDetailResponse = await invokeLocalExperimental({
    operation: "get_device_detail",
    selector: "0",
    correlationId: "local-experimental-get-device-detail-proof",
  });
  const interfaceStatusResponse = await invokeLocalExperimental({
    operation: "read_interface_status",
    selector: "0",
    correlationId: "local-experimental-read-interface-status-proof",
  });
  const echoResponse = await invokeLocalExperimental({
    operation: "echo",
    payload: echoPayload,
    correlationId: "local-experimental-echo-proof",
  });

  const parsedDeviceList = readInvokeStdoutJson(listDevicesResponse);
  const parsedComponentList = readInvokeStdoutJson(listComponentsResponse);
  const parsedPortList = readInvokeStdoutJson(listPortsResponse);
  const parsedLinkList = readInvokeStdoutJson(listLinksResponse);
  const parsedDeviceDetail = readInvokeStdoutJson(deviceDetailResponse);
  const parsedInterfaceStatus = readInvokeStdoutJson(interfaceStatusResponse);

  process.stdout.write(
    `${JSON.stringify(
      {
        proofMode: "local-experimental-active-java-exapp",
        officialProtocolSupport: false,
        echoPayload,
        handshakeResponse,
        listDevicesResponse,
        parsedDeviceList,
        listComponentsResponse,
        parsedComponentList,
        listPortsResponse,
        parsedPortList,
        listLinksResponse,
        parsedLinkList,
        deviceDetailResponse,
        parsedDeviceDetail,
        interfaceStatusResponse,
        parsedInterfaceStatus,
        echoResponse,
      },
      null,
      2
    )}\n`
  );
}

async function invokeLocalExperimental({
  operation,
  payload,
  selector,
  category,
  correlationId,
}: {
  operation: "handshake" | "echo" | "list_devices" | "list_components" | "list_ports" | "list_links" | "get_device_detail" | "read_interface_status";
  payload?: string;
  selector?: string;
  category?: string;
  correlationId: string;
}): Promise<BridgeResponse> {
  const request = createBridgeRequest({
    correlationId,
    backend: "exapp",
    operation: "invoke",
    timeoutMs: operation === "list_components" ? 10_000 : 1_500,
    payload: {
      kind: "invoke",
      experimental:
        operation === "handshake"
          ? {
              feature: "local-experimental-message",
              mode: "string",
              operation: "handshake",
              allowNonReadOnly: false,
            }
          : operation === "list_devices"
            ? {
                feature: "local-experimental-message",
                mode: "string",
                operation: "list_devices",
                allowNonReadOnly: false,
              }
          : operation === "list_components"
            ? {
                feature: "local-experimental-message",
                mode: "string",
                operation: "list_components",
                ...(category !== undefined ? { category } : {}),
                allowNonReadOnly: false,
              }
           : operation === "list_ports"
             ? {
                 feature: "local-experimental-message",
                 mode: "string",
                 operation: "list_ports",
                 allowNonReadOnly: false,
               }
          : operation === "list_links"
            ? {
                feature: "local-experimental-message",
                mode: "string",
                operation: "list_links",
                allowNonReadOnly: false,
              }
          : operation === "get_device_detail"
            ? {
                feature: "local-experimental-message",
                mode: "string",
                operation: "get_device_detail",
                selector: selector ?? "0",
                allowNonReadOnly: false,
              }
          : operation === "read_interface_status"
            ? {
                feature: "local-experimental-message",
                mode: "string",
                operation: "read_interface_status",
                selector: selector ?? "0",
                allowNonReadOnly: false,
              }
          : {
              feature: "local-experimental-message",
              mode: "string",
              operation: "echo",
              payload: payload ?? "",
              allowNonReadOnly: false,
            },
    },
  }) as BridgeInvokeRequest;

  let bridgeStdout = "";
  const bridgeStdoutCollector = new Writable({
    write(chunk, _encoding, callback) {
      bridgeStdout += chunk.toString("utf8");
      callback();
    },
  });

  await runPacketTracerExAppBridgeExecutable({
    argv: [EXAPP_BRIDGE_MODE_ARGS.invoke],
    stdin: Readable.from([JSON.stringify(request)]),
    stdout: bridgeStdoutCollector,
    now: () => Date.now(),
  });

  return JSON.parse(bridgeStdout) as BridgeResponse;
}

function readInvokeStdoutJson(response: BridgeResponse): unknown {
  if (!response.ok || response.operation !== "invoke") {
    return null;
  }

  return JSON.parse(response.result.stdout);
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`${message}\n`);
  process.exitCode = 1;
});
