import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { registerResources } from "./resources/registerResources.js";
import { SERVER_NAME, SERVER_VERSION } from "./serverMetadata.js";
import { registerTools } from "./tools/registerTools.js";
import { createPacketTracerMcpService } from "./packettracer/mcpService.js";

export function createServer(): McpServer {
  const server = new McpServer({
    name: SERVER_NAME,
    version: SERVER_VERSION,
  });
  const service = createPacketTracerMcpService();

  registerTools(server, service);
  registerResources(server, service);

  return server;
}
