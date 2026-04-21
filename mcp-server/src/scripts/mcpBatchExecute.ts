import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

interface BatchFileShape {
  steps?: BatchStepShape[];
}

interface BatchStepShape {
  label?: string;
  request?: Record<string, JsonValue>;
  entrypoint?: string;
  args?: string[];
  stdin?: string;
  timeoutMs?: number;
  experimental?: Record<string, JsonValue>;
  commands?: string[];
}

async function main(): Promise<void> {
  const { batchPath, continueOnError, serverCommand, serverArgs } = parseArgs(process.argv.slice(2));
  const steps = await loadSteps(batchPath);

  const transport = new StdioClientTransport({
    command: serverCommand,
    args: serverArgs,
    cwd: process.cwd(),
    stderr: "pipe",
  });

  const client = new Client({ name: "packettracer-batch-client", version: "1.0.0" });
  await client.connect(transport);

  let failures = 0;

  try {
    for (let index = 0; index < steps.length; index += 1) {
      const step = steps[index];
      const label = step.label?.trim() || `step-${index + 1}`;
      const request = normalizeRequest(step);

      process.stdout.write(`\n=== ${label} ===\n`);
      process.stdout.write(`${JSON.stringify(request, null, 2)}\n`);

      const result = await client.callTool({
        name: "packettracer_execute",
        arguments: request,
      });

      process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);

      const ok = extractOk(result);
      if (!ok) {
        failures += 1;
        if (!continueOnError) {
          throw new Error(`Batch stopped at ${label}: packettracer_execute returned ok=false.`);
        }
      }
    }
  } finally {
    await client.close();
  }

  process.stdout.write(`\nBatch complete. failures=${failures}\n`);
  if (failures > 0) {
    process.exitCode = 1;
  }
}

function parseArgs(argv: string[]) {
  let batchPath = "";
  let continueOnError = false;
  let serverCommand = process.execPath;
  let serverArgs = [path.resolve(scriptPackageRoot(), "dist/index.js")];

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (arg === "--continue-on-error") {
      continueOnError = true;
      continue;
    }

    if (arg === "--server-command") {
      serverCommand = requireValue(argv, ++index, arg);
      serverArgs = [];
      continue;
    }

    if (arg === "--server-arg") {
      serverArgs.push(requireValue(argv, ++index, arg));
      continue;
    }

    if (!batchPath) {
      batchPath = arg;
      continue;
    }

    throw new Error(`Unexpected argument '${arg}'.`);
  }

  if (!batchPath) {
    throw new Error([
      "Usage:",
      "  npm run batch:mcp -- <batch.json>",
      "  npm run batch:mcp -- <batch.json> --continue-on-error",
      "  npm run batch:mcp -- <batch.json> --server-command node --server-arg dist/index.js",
      "",
      "Tips:",
      "- Build first with: npm run build",
      "- If a step has experimental.commands as an array, it will be converted to b64:<...> automatically for run_device_cli / probe_terminal_transcript.",
    ].join("\n"));
  }

  return { batchPath: path.resolve(process.cwd(), batchPath), continueOnError, serverCommand, serverArgs };
}

function requireValue(argv: string[], index: number, flag: string): string {
  const value = argv[index];
  if (!value) {
    throw new Error(`Missing value for ${flag}.`);
  }
  return value;
}

async function loadSteps(batchPath: string): Promise<BatchStepShape[]> {
  const raw = await readFile(batchPath, "utf8");
  const parsed = JSON.parse(raw) as JsonValue;

  if (Array.isArray(parsed)) {
    return parsed as BatchStepShape[];
  }

  if (parsed && typeof parsed === "object" && Array.isArray((parsed as BatchFileShape).steps)) {
    return (parsed as BatchFileShape).steps as BatchStepShape[];
  }

  throw new Error(`Batch file '${batchPath}' must be a JSON array or an object with a 'steps' array.`);
}

function normalizeRequest(step: BatchStepShape): Record<string, JsonValue> {
  const request = { ...(step.request ?? step) } as Record<string, JsonValue>;
  delete request.label;
  delete request.request;
  delete request.commands;

  const experimental = request.experimental;
  const topLevelCommands = step.commands;

  if (experimental && typeof experimental === "object" && !Array.isArray(experimental)) {
    const cloned = { ...experimental } as Record<string, JsonValue>;
    if (!request.entrypoint) {
      request.entrypoint = "local-experimental";
    }
    if (!cloned.feature) {
      cloned.feature = "local-experimental-message";
    }
    if (!cloned.mode) {
      cloned.mode = "string";
    }

    const commands = Array.isArray(topLevelCommands)
      ? topLevelCommands
      : Array.isArray(cloned.commands)
        ? (cloned.commands as string[])
        : null;

    if (
      commands &&
      (cloned.operation === "run_device_cli" || cloned.operation === "probe_terminal_transcript")
    ) {
      cloned.command = encodeCommands(commands);
      delete cloned.commands;
    }

    request.experimental = cloned;
  }

  return request;
}

function encodeCommands(commands: string[]): string {
  const joined = commands.join("\n");
  return `b64:${Buffer.from(joined, "utf8").toString("base64")}`;
}

function extractOk(result: unknown): boolean {
  if (!result || typeof result !== "object") {
    return true;
  }

  const structured = (result as { structuredContent?: { ok?: boolean } }).structuredContent;
  if (structured && typeof structured.ok === "boolean") {
    return structured.ok;
  }

  return true;
}

function scriptPackageRoot(): string {
  const filename = fileURLToPath(import.meta.url);
  return path.resolve(path.dirname(filename), "..", "..");
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`${message}\n`);
  process.exitCode = 1;
});
