import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { PACKET_TRACER_NAMESPACE } from "../packettracer/namespace.js";
import {
  PACKET_TRACER_TOOL_NAMES,
  type PacketTracerMcpService,
} from "../packettracer/mcpService.js";
import { SERVER_NAME, SERVER_VERSION } from "../serverMetadata.js";

export function registerResources(
  server: McpServer,
  service: PacketTracerMcpService
): void {
  server.resource("info", "server://info", async (uri) => {
    const packetTracerCatalog = service.getCatalogResource();

    return {
      contents: [
        {
          uri: uri.href,
          mimeType: "application/json",
          text: JSON.stringify({
            name: SERVER_NAME,
            version: SERVER_VERSION,
            tools: PACKET_TRACER_TOOL_NAMES,
            packetTracerCatalog,
            packetTracer: PACKET_TRACER_NAMESPACE,
          }),
        },
      ],
    };
  });
}
