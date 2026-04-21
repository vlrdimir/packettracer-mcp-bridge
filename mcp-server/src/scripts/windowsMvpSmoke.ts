import path from "node:path";
import { fileURLToPath } from "node:url";
import { existsSync, statSync } from "node:fs";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

interface CliArgs {
  serverCommand: string;
  serverArgs: string[];
  allowNonWin32: boolean;
  jsonOutput: boolean;
}

interface SmokeIssue {
  level: "error" | "warning";
  code: string;
  message: string;
  fix?: string;
}

interface SmokeDiagnosis {
  summary: string[];
  errors: string[];
  hints: string[];
}

interface PreflightResult {
  errors: SmokeIssue[];
  warnings: SmokeIssue[];
}

interface JsonStepReport {
  ok: boolean;
  skipped?: boolean;
  error?: string;
  payload?: Record<string, JsonValue>;
  diagnosis?: SmokeDiagnosis;
}

interface JsonSmokeReport {
  ok: boolean;
  mode: "windows-mvp-smoke";
  format: "json";
  generatedAt: string;
  platform: NodeJS.Platform;
  preflight: {
    ok: boolean;
    errors: SmokeIssue[];
    warnings: SmokeIssue[];
  };
  steps: {
    status: JsonStepReport;
    execute: JsonStepReport;
  };
  fatal?: string;
}

const BRIDGE_PORT_RANGE = {
  min: 39_150,
  max: 39_159,
} as const;

const REQUIRED_ENV_VARS = {
  bridgeCommand: "PACKET_TRACER_EXAPP_BRIDGE_COMMAND",
  bridgeCwd: "PACKET_TRACER_EXAPP_BRIDGE_CWD",
  bridgePort: "PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT",
} as const;

const OPTIONAL_ENV_VARS = {
  bridgeArgsJson: "PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON",
  launcherPath: "PACKET_TRACER_LAUNCHER_PATH",
  resetHelperPath: "PACKET_TRACER_RESET_HELPER_PATH",
  appImagePath: "PACKET_TRACER_APPIMAGE_PATH",
} as const;

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));

  if (process.platform !== "win32" && !args.allowNonWin32) {
    const platformMessage =
      "windowsMvpSmoke is intended for Windows hosts. Re-run with --allow-non-win32 if you want to exercise it on another platform.";
    if (args.jsonOutput) {
      emitJsonReport({
        ok: false,
        mode: "windows-mvp-smoke",
        format: "json",
        generatedAt: new Date().toISOString(),
        platform: process.platform,
        preflight: {
          ok: false,
          errors: [],
          warnings: [],
        },
        steps: {
          status: {
            ok: false,
            skipped: true,
            error: platformMessage,
          },
          execute: {
            ok: false,
            skipped: true,
            error: "Skipped because platform check failed.",
          },
        },
        fatal: platformMessage,
      });
      process.exitCode = 1;
      return;
    }

    throw new Error(
      platformMessage
    );
  }

  const preflight = collectPreflightChecks();

  if (args.jsonOutput) {
    await runJsonSmoke(args, preflight);
    return;
  }

  process.stdout.write("[preflight] Checking required environment and paths...\n");
  enforcePreflightChecks(preflight);

  const transport = new StdioClientTransport({
    command: args.serverCommand,
    args: args.serverArgs,
    cwd: process.cwd(),
    stderr: "pipe",
  });

  const client = new Client({ name: "packettracer-windows-smoke", version: "1.0.0" });
  await client.connect(transport);

  try {
    process.stdout.write("[1/2] Calling packettracer_status...\n");
    const statusPayload = await callPacketTracerStatus(client);
    process.stdout.write(`${JSON.stringify(statusPayload, null, 2)}\n`);

    const statusDiagnosis = diagnoseStatusPayload(statusPayload);
    process.stdout.write(`${formatDiagnosis("packettracer_status diagnosis", statusDiagnosis)}\n`);

    if (statusDiagnosis.errors.length > 0) {
      throw new Error("packettracer_status failed diagnostics. See diagnosis block above.");
    }

    process.stdout.write("[2/2] Calling packettracer_execute (read-only list_devices)...\n");
    const executePayload = await callPacketTracerExecuteReadOnly(client);
    process.stdout.write(`${JSON.stringify(executePayload, null, 2)}\n`);

    const executeDiagnosis = diagnoseExecutePayload(executePayload);
    process.stdout.write(`${formatDiagnosis("packettracer_execute diagnosis", executeDiagnosis)}\n`);

    if (executeDiagnosis.errors.length > 0) {
      throw new Error("packettracer_execute failed diagnostics. See diagnosis block above.");
    }

    process.stdout.write("Windows MVP smoke test passed.\n");
  } finally {
    await client.close();
  }
}

function parseArgs(argv: string[]): CliArgs {
  if (argv.includes("--help") || argv.includes("-h")) {
    printUsage();
    process.exit(0);
  }

  let serverCommand = process.execPath;
  let serverArgs = [path.resolve(scriptPackageRoot(), "dist/index.js")];
  let allowNonWin32 = false;
  let jsonOutput = false;

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (arg === "--allow-non-win32") {
      allowNonWin32 = true;
      continue;
    }

    if (arg === "--json") {
      jsonOutput = true;
      continue;
    }

    if (arg === "--server-command") {
      serverCommand = requireArgValue(argv, ++index, arg);
      serverArgs = [];
      continue;
    }

    if (arg === "--server-arg") {
      serverArgs.push(requireArgValue(argv, ++index, arg));
      continue;
    }

    throw new Error(`Unexpected argument '${arg}'. Use --help for usage.`);
  }

  return { serverCommand, serverArgs, allowNonWin32, jsonOutput };
}

function requireArgValue(argv: string[], index: number, flag: string): string {
  const value = argv[index];
  if (!value) {
    throw new Error(`Missing value for ${flag}.`);
  }

  return value;
}

function printUsage(): void {
  process.stdout.write(
    [
      "Usage:",
      "  npm run smoke:windows",
      "  npm run smoke:windows -- --server-command node --server-arg dist/index.js",
      "",
      "Options:",
      "  --server-command <cmd>    Override MCP server command",
      "  --server-arg <value>      Append MCP server args (repeatable)",
      "  --allow-non-win32         Run script outside Windows host",
      "  --json                    Emit machine-readable JSON report",
      "",
      "This script first checks required env vars and path health, then runs:",
      "  1) packettracer_status",
      "  2) packettracer_execute (read-only list_devices)",
    ].join("\n") + "\n"
  );
}

function scriptPackageRoot(): string {
  const filename = fileURLToPath(import.meta.url);
  return path.resolve(path.dirname(filename), "..", "..");
}

function readStructuredObject(result: unknown): Record<string, JsonValue> {
  if (!result || typeof result !== "object") {
    throw new Error("MCP tool call returned a non-object response.");
  }

  const structuredContent = (result as { structuredContent?: unknown }).structuredContent;
  if (!structuredContent || typeof structuredContent !== "object" || Array.isArray(structuredContent)) {
    throw new Error("MCP tool call did not return structuredContent object.");
  }

  return structuredContent as Record<string, JsonValue>;
}

async function callPacketTracerStatus(client: Client): Promise<Record<string, JsonValue>> {
  const statusResult = await client.callTool({
    name: "packettracer_status",
    arguments: {},
  });

  return readStructuredObject(statusResult);
}

async function callPacketTracerExecuteReadOnly(client: Client): Promise<Record<string, JsonValue>> {
  const executeResult = await client.callTool({
    name: "packettracer_execute",
    arguments: {
      entrypoint: "local-experimental",
      timeoutMs: 30_000,
      experimental: {
        feature: "local-experimental-message",
        mode: "string",
        operation: "list_devices",
        allowNonReadOnly: false,
      },
    },
  });

  return readStructuredObject(executeResult);
}

async function runJsonSmoke(args: CliArgs, preflight: PreflightResult): Promise<void> {
  const report: JsonSmokeReport = {
    ok: false,
    mode: "windows-mvp-smoke",
    format: "json",
    generatedAt: new Date().toISOString(),
    platform: process.platform,
    preflight: {
      ok: preflight.errors.length === 0,
      errors: preflight.errors,
      warnings: preflight.warnings,
    },
    steps: {
      status: {
        ok: false,
      },
      execute: {
        ok: false,
      },
    },
  };

  if (preflight.errors.length > 0) {
    report.steps.status = {
      ok: false,
      skipped: true,
      error: "Skipped because preflight failed.",
    };
    report.steps.execute = {
      ok: false,
      skipped: true,
      error: "Skipped because preflight failed.",
    };
    report.fatal = "Preflight failed. See preflight.errors.";
    emitJsonReport(report);
    process.exitCode = 1;
    return;
  }

  const transport = new StdioClientTransport({
    command: args.serverCommand,
    args: args.serverArgs,
    cwd: process.cwd(),
    stderr: "pipe",
  });

  const client = new Client({ name: "packettracer-windows-smoke", version: "1.0.0" });

  try {
    await client.connect(transport);

    try {
      const statusPayload = await callPacketTracerStatus(client);
      const statusDiagnosis = diagnoseStatusPayload(statusPayload);
      const statusOk = statusDiagnosis.errors.length === 0;

      report.steps.status = {
        ok: statusOk,
        payload: statusPayload,
        diagnosis: statusDiagnosis,
      };

      if (!statusOk) {
        report.steps.execute = {
          ok: false,
          skipped: true,
          error: "Skipped because packettracer_status diagnostics failed.",
        };
        report.fatal = "packettracer_status failed diagnostics.";
        report.ok = false;
        emitJsonReport(report);
        process.exitCode = 1;
        return;
      }
    } catch (error) {
      report.steps.status = {
        ok: false,
        error: `packettracer_status call failed: ${formatError(error)}`,
      };
      report.steps.execute = {
        ok: false,
        skipped: true,
        error: "Skipped because packettracer_status call failed.",
      };
      report.fatal = "packettracer_status call failed.";
      report.ok = false;
      emitJsonReport(report);
      process.exitCode = 1;
      return;
    }

    try {
      const executePayload = await callPacketTracerExecuteReadOnly(client);
      const executeDiagnosis = diagnoseExecutePayload(executePayload);
      const executeOk = executeDiagnosis.errors.length === 0;

      report.steps.execute = {
        ok: executeOk,
        payload: executePayload,
        diagnosis: executeDiagnosis,
      };

      report.ok = executeOk;
      if (!executeOk) {
        report.fatal = "packettracer_execute failed diagnostics.";
        process.exitCode = 1;
      }

      emitJsonReport(report);
      return;
    } catch (error) {
      report.steps.execute = {
        ok: false,
        error: `packettracer_execute call failed: ${formatError(error)}`,
      };
      report.fatal = "packettracer_execute call failed.";
      report.ok = false;
      emitJsonReport(report);
      process.exitCode = 1;
      return;
    }
  } finally {
    await client.close().catch(() => undefined);
  }
}

function emitJsonReport(report: JsonSmokeReport): void {
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

function collectPreflightChecks(): PreflightResult {
  const issues: SmokeIssue[] = [];

  const bridgeCommand = readEnv(REQUIRED_ENV_VARS.bridgeCommand);
  const bridgeCwd = readEnv(REQUIRED_ENV_VARS.bridgeCwd);
  const bridgePort = readEnv(REQUIRED_ENV_VARS.bridgePort);
  const bridgeArgsJson = readEnv(OPTIONAL_ENV_VARS.bridgeArgsJson);
  const launcherPath = readEnv(OPTIONAL_ENV_VARS.launcherPath);
  const resetHelperPath = readEnv(OPTIONAL_ENV_VARS.resetHelperPath);
  const appImagePath = readEnv(OPTIONAL_ENV_VARS.appImagePath);

  if (!bridgeCommand) {
    issues.push({
      level: "error",
      code: "missing-env-bridge-command",
      message: `${REQUIRED_ENV_VARS.bridgeCommand} is not set.`,
      fix: `Set ${REQUIRED_ENV_VARS.bridgeCommand} to your built bridge executable (for example $PWD/dist/packettracer-exapp-bridge.js).`,
    });
  } else if (looksLikePathValue(bridgeCommand) && !pathExists(bridgeCommand)) {
    issues.push({
      level: "error",
      code: "bridge-command-not-found",
      message: `${REQUIRED_ENV_VARS.bridgeCommand} points to a path that does not exist: ${bridgeCommand}`,
      fix: "Run npm run build in mcp-server and point this env var to dist/packettracer-exapp-bridge.js.",
    });
  }

  if (!bridgeCwd) {
    issues.push({
      level: "error",
      code: "missing-env-bridge-cwd",
      message: `${REQUIRED_ENV_VARS.bridgeCwd} is not set.`,
      fix: `Set ${REQUIRED_ENV_VARS.bridgeCwd} to your mcp-server directory path.`,
    });
  } else if (!directoryExists(bridgeCwd)) {
    issues.push({
      level: "error",
      code: "bridge-cwd-not-found",
      message: `${REQUIRED_ENV_VARS.bridgeCwd} does not point to an existing directory: ${bridgeCwd}`,
      fix: "Use an absolute path or run from mcp-server and set PACKET_TRACER_EXAPP_BRIDGE_CWD=$PWD.",
    });
  }

  if (!bridgePort) {
    issues.push({
      level: "error",
      code: "missing-env-bridge-port",
      message: `${REQUIRED_ENV_VARS.bridgePort} is not set.`,
      fix: `Set ${REQUIRED_ENV_VARS.bridgePort} to the exact 3915X port printed in Packet Tracer ExApp log.`,
    });
  } else {
    const parsedPort = Number.parseInt(bridgePort, 10);
    if (!Number.isFinite(parsedPort)) {
      issues.push({
        level: "error",
        code: "invalid-env-bridge-port",
        message: `${REQUIRED_ENV_VARS.bridgePort} is not a valid number: ${bridgePort}`,
        fix: `Use a numeric value in ${BRIDGE_PORT_RANGE.min}-${BRIDGE_PORT_RANGE.max}.`,
      });
    } else if (parsedPort < BRIDGE_PORT_RANGE.min || parsedPort > BRIDGE_PORT_RANGE.max) {
      issues.push({
        level: "error",
        code: "bridge-port-out-of-range",
        message: `${REQUIRED_ENV_VARS.bridgePort}=${parsedPort} is outside expected range ${BRIDGE_PORT_RANGE.min}-${BRIDGE_PORT_RANGE.max}.`,
        fix: "Use the exact local experimental bridge port from Packet Tracer log.",
      });
    }
  }

  if (!bridgeArgsJson) {
    issues.push({
      level: "warning",
      code: "missing-env-bridge-args-json",
      message: `${OPTIONAL_ENV_VARS.bridgeArgsJson} is not set; default empty args will be used by server parsing.`,
      fix: `Set ${OPTIONAL_ENV_VARS.bridgeArgsJson}='[]' for explicitness.`,
    });
  } else {
    try {
      const parsed = JSON.parse(bridgeArgsJson) as unknown;
      if (!Array.isArray(parsed) || parsed.some((value) => typeof value !== "string")) {
        issues.push({
          level: "error",
          code: "invalid-env-bridge-args-json",
          message: `${OPTIONAL_ENV_VARS.bridgeArgsJson} must be a JSON array of strings.`,
          fix: `Set ${OPTIONAL_ENV_VARS.bridgeArgsJson}='[]'.`,
        });
      }
    } catch {
      issues.push({
        level: "error",
        code: "invalid-json-bridge-args",
        message: `${OPTIONAL_ENV_VARS.bridgeArgsJson} is not valid JSON.`,
        fix: `Set ${OPTIONAL_ENV_VARS.bridgeArgsJson}='[]'.`,
      });
    }
  }

  if (launcherPath && !pathExists(launcherPath)) {
    issues.push({
      level: "warning",
      code: "launcher-path-not-found",
      message: `${OPTIONAL_ENV_VARS.launcherPath} is set but path does not exist: ${launcherPath}`,
      fix: "Use valid PacketTracer.exe path or unset this variable.",
    });
  }

  if (resetHelperPath && !pathExists(resetHelperPath)) {
    issues.push({
      level: "warning",
      code: "reset-helper-path-not-found",
      message: `${OPTIONAL_ENV_VARS.resetHelperPath} is set but path does not exist: ${resetHelperPath}`,
      fix: "On Windows this can stay unset unless you provide your own reset helper.",
    });
  }

  if (appImagePath && !pathExists(appImagePath)) {
    issues.push({
      level: "warning",
      code: "appimage-path-not-found",
      message: `${OPTIONAL_ENV_VARS.appImagePath} is set but path does not exist: ${appImagePath}`,
      fix: "On Windows this can stay unset.",
    });
  }

  const errors = issues.filter((issue) => issue.level === "error");
  const warnings = issues.filter((issue) => issue.level === "warning");

  return { errors, warnings };
}

function enforcePreflightChecks(preflight: PreflightResult): void {
  if (preflight.errors.length > 0) {
    const blocks = [formatIssues("Preflight failed", preflight.errors)];
    if (preflight.warnings.length > 0) {
      blocks.push(formatIssues("Preflight warnings", preflight.warnings));
    }

    throw new Error(blocks.join("\n"));
  }

  if (preflight.warnings.length > 0) {
    process.stdout.write(`${formatIssues("Preflight warnings", preflight.warnings)}\n`);
  }

  process.stdout.write("[preflight] OK\n");
}

function diagnoseStatusPayload(payload: Record<string, JsonValue>): {
  summary: string[];
  errors: string[];
  hints: string[];
} {
  const summary: string[] = [];
  const errors: string[] = [];
  const hints: string[] = [];

  const phase = readStringAtPath(payload, ["phase"]);
  const isInstalled = readBooleanAtPath(payload, ["isInstalled"]);
  const isRunning = readBooleanAtPath(payload, ["isRunning"]);
  const canLaunch = readBooleanAtPath(payload, ["canLaunch"]);
  const bridgeReadiness = readStringAtPath(payload, ["bridge", "readiness"]);
  const bridgeDetected = readBooleanAtPath(payload, ["bridge", "detected"]);
  const bridgePort = readNumberAtPath(payload, ["bridge", "port"]);
  const selectionReason = readStringAtPath(payload, ["selection", "reason"]);
  const selectedBackend = readStringAtPath(payload, ["selection", "selectedBackend"]);
  const statusErrorCode = readStringAtPath(payload, ["error", "code"]);
  const statusErrorMessage = readStringAtPath(payload, ["error", "message"]);
  const launcherPath = readStringAtPath(payload, ["paths", "launcherPath"]);
  const launcherAvailable = readBooleanAtPath(payload, ["paths", "launcherAvailable"]);

  summary.push(`phase=${phase ?? "unknown"}`);
  summary.push(`isInstalled=${String(isInstalled ?? false)}`);
  summary.push(`isRunning=${String(isRunning ?? false)}`);
  summary.push(`canLaunch=${String(canLaunch ?? false)}`);
  summary.push(`bridge.readiness=${bridgeReadiness ?? "unknown"}`);
  summary.push(`bridge.detected=${String(bridgeDetected ?? false)}`);
  summary.push(`bridge.port=${bridgePort ?? "null"}`);

  if (selectedBackend !== "exapp") {
    errors.push(
      `Backend selection is not exapp (selectedBackend=${selectedBackend ?? "null"}, reason=${selectionReason ?? "unknown"}).`
    );
    hints.push("Set PACKET_TRACER_EXAPP_BRIDGE_COMMAND, PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON, and PACKET_TRACER_EXAPP_BRIDGE_CWD.");
  }

  if (bridgeReadiness !== "ready") {
    errors.push(`Bridge readiness is '${bridgeReadiness ?? "unknown"}', expected 'ready'.`);
    hints.push(
      "Open Packet Tracer, launch PT ExApp, then ensure PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT matches the logged port."
    );
  }

  if (phase === "not-installed" || isInstalled === false) {
    errors.push("Packet Tracer runtime is reported as not installed.");
    hints.push(
      `Verify PacketTracer.exe location and set PACKET_TRACER_LAUNCHER_PATH (current=${launcherPath ?? "unset"}, available=${String(launcherAvailable ?? false)}).`
    );
  }

  if (statusErrorCode || statusErrorMessage) {
    summary.push(`status.error=${statusErrorCode ?? "unknown"}: ${statusErrorMessage ?? "(no message)"}`);
  }

  return { summary, errors, hints };
}

function diagnoseExecutePayload(payload: Record<string, JsonValue>): {
  summary: string[];
  errors: string[];
  hints: string[];
} {
  const summary: string[] = [];
  const errors: string[] = [];
  const hints: string[] = [];

  const ok = payload.ok === true;
  const readiness = readStringAtPath(payload, ["readiness"]);
  const backendRequestId = readStringAtPath(payload, ["backendRequestId"]);
  const errorCode = readStringAtPath(payload, ["error", "code"]);
  const errorMessage = readStringAtPath(payload, ["error", "message"]);
  const entrypoint = readStringAtPath(payload, ["result", "entrypoint"]);

  summary.push(`ok=${String(ok)}`);
  summary.push(`readiness=${readiness ?? "unknown"}`);
  summary.push(`backendRequestId=${backendRequestId ?? "(none)"}`);
  summary.push(`entrypoint=${entrypoint ?? "(none)"}`);

  if (!ok) {
    errors.push(`Execute result returned ok=false (${errorCode ?? "unknown-error"}).`);
    if (errorMessage) {
      summary.push(`error.message=${errorMessage}`);
    }

    if (errorCode === "not-ready" || readiness === "not-ready" || readiness === "booting") {
      hints.push("Bridge is not ready yet. Re-check packettracer_status and ExApp launch state.");
    }

    if (errorCode === "unavailable-backend" || readiness === "unavailable") {
      hints.push("Verify bridge target env vars and PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT.");
    }

    if (errorCode === "timeout") {
      hints.push("Increase timeoutMs or verify local bridge responsiveness from Packet Tracer log.");
    }
  }

  return { summary, errors, hints };
}

function formatDiagnosis(
  title: string,
  diagnosis: { summary: string[]; errors: string[]; hints: string[] }
): string {
  const lines = [`${title}:`];
  for (const item of diagnosis.summary) {
    lines.push(`- ${item}`);
  }

  if (diagnosis.errors.length > 0) {
    lines.push("- errors:");
    for (const error of diagnosis.errors) {
      lines.push(`  - ${error}`);
    }
  }

  if (diagnosis.hints.length > 0) {
    lines.push("- hints:");
    for (const hint of diagnosis.hints) {
      lines.push(`  - ${hint}`);
    }
  }

  return lines.join("\n");
}

function formatIssues(title: string, issues: SmokeIssue[]): string {
  const lines = [`${title}:`];
  for (const issue of issues) {
    lines.push(`- [${issue.code}] ${issue.message}`);
    if (issue.fix) {
      lines.push(`  fix: ${issue.fix}`);
    }
  }

  return lines.join("\n");
}

function readEnv(name: string): string | undefined {
  const raw = process.env[name]?.trim();
  return raw && raw.length > 0 ? raw : undefined;
}

function looksLikePathValue(value: string): boolean {
  return value.includes("\\") || value.includes("/") || value.endsWith(".js") || value.endsWith(".exe");
}

function pathExists(value: string): boolean {
  const resolved = path.isAbsolute(value) ? value : path.resolve(process.cwd(), value);
  return existsSync(resolved);
}

function directoryExists(value: string): boolean {
  const resolved = path.isAbsolute(value) ? value : path.resolve(process.cwd(), value);
  try {
    return existsSync(resolved) && statSync(resolved).isDirectory();
  } catch {
    return false;
  }
}

function readBooleanAtPath(
  value: Record<string, JsonValue>,
  pathSegments: readonly string[]
): boolean | undefined {
  const target = readValueAtPath(value, pathSegments);
  return typeof target === "boolean" ? target : undefined;
}

function readNumberAtPath(
  value: Record<string, JsonValue>,
  pathSegments: readonly string[]
): number | undefined {
  const target = readValueAtPath(value, pathSegments);
  return typeof target === "number" ? target : undefined;
}

function readValueAtPath(
  value: Record<string, JsonValue>,
  pathSegments: readonly string[]
): JsonValue | undefined {
  let cursor: JsonValue = value;
  for (const segment of pathSegments) {
    if (!cursor || typeof cursor !== "object" || Array.isArray(cursor)) {
      return undefined;
    }

    cursor = (cursor as Record<string, JsonValue>)[segment];
  }

  return cursor;
}

function readStringAtPath(
  value: Record<string, JsonValue>,
  pathSegments: readonly string[]
): string | undefined {
  const cursor = readValueAtPath(value, pathSegments);
  return typeof cursor === "string" && cursor.length > 0 ? cursor : undefined;
}

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`${message}\n`);
  process.exitCode = 1;
});
