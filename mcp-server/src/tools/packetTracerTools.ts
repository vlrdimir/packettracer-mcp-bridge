import { z } from "zod";

import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { type PacketTracerMcpService } from "../packettracer/mcpService.js";

const launchSchema = {
  args: z.array(z.string()).optional().describe("Optional launcher arguments"),
};

const executeSchema = {
  entrypoint: z.string().min(1).describe("Command or entrypoint to execute inside Packet Tracer"),
  args: z.array(z.string()).optional().describe("Optional command arguments"),
  stdin: z.string().optional().describe("Optional stdin payload for the command"),
  timeoutMs: z.number().int().positive().optional().describe("Optional execution timeout override in milliseconds"),
  experimental: z
    .discriminatedUnion("operation", [
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("handshake"),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("echo"),
        payload: z.string(),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("list_devices"),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("list_components"),
        category: z.string().min(1).optional().describe("Optional device category filter such as iot, network_devices, wireless_devices, end_devices, security, collaboration, wan_emulation, or miscellaneous"),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("list_ports"),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("list_links"),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("get_device_detail"),
        selector: z.string().min(1),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("read_interface_status"),
        selector: z.string().min(1),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("read_port_power_state"),
        deviceSelector: z.string().min(1).describe("Device name or index already present in the topology"),
        portSelector: z.string().min(1).describe("Port/interface name on the selected device"),
        allowNonReadOnly: z.literal(false).default(false),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("add_device"),
        deviceType: z.string().min(1).describe("Packet Tracer device type enum such as ROUTER, SWITCH, or PC"),
        model: z.string().min(1).describe("Packet Tracer model string such as 1841, 2960-24TT, or PC-PT"),
        x: z.number().describe("Logical workspace x position"),
        y: z.number().describe("Logical workspace y position"),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("connect_devices"),
        leftDeviceSelector: z.string().min(1),
        leftPortSelector: z.string().min(1),
        rightDeviceSelector: z.string().min(1),
        rightPortSelector: z.string().min(1),
        connectionType: z.string().min(1).optional().describe("Optional connection type such as ETHERNET_STRAIGHT, ETHERNET_CROSS, or AUTO"),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("set_interface_ip"),
        deviceSelector: z.string().min(1),
        portSelector: z.string().min(1),
        ipAddress: z.string().min(1),
        subnetMask: z.string().min(1),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("set_default_gateway"),
        deviceSelector: z.string().min(1),
        gateway: z.string().min(1),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("add_static_route"),
        deviceSelector: z.string().min(1),
        network: z.string().min(1),
        subnetMask: z.string().min(1),
        nextHop: z.string().min(1),
        portSelector: z.string().optional(),
        adminDistance: z.number().int().positive().optional(),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("run_device_cli"),
        deviceSelector: z.string().min(1),
        cliMode: z.string().min(1).optional().describe("CLI mode such as user or global"),
        command: z.string().min(1).describe("Single-line CLI command or b64:<base64-of-multiline-commands>"),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("set_port_power_state"),
        deviceSelector: z.string().min(1),
        portSelector: z.string().min(1),
        powerOn: z.boolean().optional().describe("true for on/up, false for off/down; defaults to true"),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("run_ping"),
        deviceSelector: z.string().min(1),
        destinationIpAddress: z.string().min(1),
        repeatCount: z.number().int().positive().optional(),
        timeoutSeconds: z.number().int().positive().optional(),
        packetSize: z.number().int().positive().optional(),
        sourcePortName: z.string().optional(),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("probe_terminal_transcript"),
        deviceSelector: z.string().min(1),
        cliMode: z.string().min(1).optional(),
        command: z.string().min(1).describe("Single-line command or b64:<base64-of-multiline-commands>"),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("remove_device"),
        selector: z.string().min(1).describe("Runtime device selector such as Router0 or PC1"),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("delete_link"),
        selector: z.string().min(1).describe("Link index or leftDevice|rightDevice pair"),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("get_device_module_layout"),
        deviceSelector: z.string().min(1),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("add_module"),
        deviceSelector: z.string().min(1),
        slot: z.string().min(1),
        moduleType: z.string().min(1),
        model: z.string().min(1),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("remove_module"),
        deviceSelector: z.string().min(1),
        slot: z.string().min(1),
        moduleType: z.string().min(1),
        allowNonReadOnly: z.literal(true).default(true),
      }),
      z.object({
        feature: z.literal("local-experimental-message"),
        mode: z.literal("string"),
        operation: z.literal("add_module_at"),
        deviceSelector: z.string().min(1),
        parentModulePath: z.string().optional().describe("Parent module slot path; omit or empty for root module"),
        slotIndex: z.number().int().min(0),
        model: z.string().min(1),
        allowNonReadOnly: z.literal(true).default(true),
      }),
    ])
    .optional()
    .describe(
      "Optional explicit non-official local ExApp path. Host-side MCP now validates handshake, echo, list_devices, list_components, list_ports, list_links, get_device_detail, read_interface_status, read_port_power_state, add_device, connect_devices, set_interface_ip, set_default_gateway, add_static_route, run_device_cli, set_port_power_state, run_ping, probe_terminal_transcript, remove_device, delete_link, get_device_module_layout, add_module, remove_module, and add_module_at."
    ),
};

const statusSchema = {};

export function registerPacketTracerTools(
  server: McpServer,
  service: PacketTracerMcpService
): void {
  server.tool("packettracer_launch", "Launch the Packet Tracer host runtime for the deploy phase", launchSchema, async (input) => {
    const result = await service.launch(input);
    return createToolResponse(result);
  });

  server.tool(
    "packettracer_execute",
    "Execute an orchestrator request for observe/configure/verify phases through the selected backend",
    executeSchema,
    async (input) => {
      const result = await service.execute(input);
      return createToolResponse(result);
    }
  );

  server.tool(
    "packettracer_reset",
    "Reset Packet Tracer login state for manual-only operational reset work",
    {},
    async () => {
      const result = await service.reset();
      return createToolResponse(result);
    }
  );

  server.tool(
    "packettracer_status",
    "Report Packet Tracer runtime status and readiness-gate context for orchestration",
    statusSchema,
    async (input) => {
      const result = await service.status(input);
      return createToolResponse(result);
    }
  );
}

function createToolResponse<T extends object>(payload: T) {
  const structuredContent = payload as Record<string, unknown>;

  return {
    content: [{ type: "text" as const, text: JSON.stringify(payload, null, 2) }],
    structuredContent,
  };
}
