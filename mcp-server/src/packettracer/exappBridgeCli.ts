import { z } from "zod";

import { BRIDGE_PROTOCOL_VERSION } from "../bridge/index.js";

export const PACKET_TRACER_EXAPP_BRIDGE_EXECUTABLE = "packettracer-exapp-bridge";

export const EXAPP_BRIDGE_MODE_ARGS = {
  handshake: "--packet-tracer-bridge-handshake",
  invoke: "--packet-tracer-bridge-invoke",
} as const;

export const exAppHandshakeSchema = z
  .object({
    protocolVersion: z.literal(BRIDGE_PROTOCOL_VERSION),
    backend: z.literal("exapp"),
    readiness: z.enum(["booting", "ready", "not-ready", "degraded", "unavailable"]),
    backendRequestId: z.string().min(1).optional(),
    details: z.record(z.string(), z.string()).default({}),
  })
  .strict();

export type ExAppHandshake = z.infer<typeof exAppHandshakeSchema>;
