import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { type PacketTracerMcpService } from "../packettracer/mcpService.js";
import { registerPacketTracerTools } from "./packetTracerTools.js";

export function registerTools(
  server: McpServer,
  service: PacketTracerMcpService
): void {
  registerPacketTracerTools(server, service);
}
