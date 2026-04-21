#!/usr/bin/env node

import { runPacketTracerExAppBridgeExecutable } from "./packettracer/exappBridgeExecutable.js";

runPacketTracerExAppBridgeExecutable().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`${message}\n`);
  process.exitCode = 1;
});
