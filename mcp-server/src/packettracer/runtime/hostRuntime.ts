import { execFile, spawn } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import { access, constants } from "node:fs/promises";

const WINDOWS_PACKET_TRACER_LAUNCHER_CANDIDATES = [
  "C:\\Program Files\\Cisco Packet Tracer\\bin\\PacketTracer.exe",
  "C:\\Program Files\\Cisco Packet Tracer 9.0\\bin\\PacketTracer.exe",
  "C:\\Program Files\\Cisco Packet Tracer 8.2.2\\bin\\PacketTracer.exe",
  "C:\\Program Files\\Cisco Packet Tracer 8.2.0\\bin\\PacketTracer.exe",
  "C:\\Program Files (x86)\\Cisco Packet Tracer\\bin\\PacketTracer.exe",
] as const;

export const DEFAULT_PACKET_TRACER_PATHS = {
  launcherPath: "/usr/bin/packettracer",
  resetHelperPath: "/usr/bin/packettracer-reset-login-state",
  appImagePath: "/usr/lib/packettracer/packettracer.AppImage",
} as const;

export const PACKET_TRACER_RUNTIME_PATH_ENV = {
  launcherPath: "PACKET_TRACER_LAUNCHER_PATH",
  resetHelperPath: "PACKET_TRACER_RESET_HELPER_PATH",
  appImagePath: "PACKET_TRACER_APPIMAGE_PATH",
} as const;

const DEFAULT_READINESS_TIMEOUT_MS = 5_000;
const DEFAULT_READINESS_POLL_INTERVAL_MS = 250;

export type PacketTracerRuntimePhase =
  | "not-installed"
  | "launching"
  | "ready"
  | "already-running"
  | "failed";

export type PacketTracerRuntimeFailureCode =
  | "launcher-missing"
  | "appimage-missing"
  | "reset-helper-missing"
  | "runtime-misconfigured"
  | "process-probe-failed"
  | "launch-spawn-failed"
  | "readiness-timeout"
  | "reset-command-failed";

export interface PacketTracerRuntimeProblem {
  code: PacketTracerRuntimeFailureCode;
  message: string;
}

export interface PacketTracerRuntimeOptions {
  launcherPath?: string;
  resetHelperPath?: string;
  appImagePath?: string;
  platform?: NodeJS.Platform;
  readinessTimeoutMs?: number;
  readinessPollIntervalMs?: number;
  cwd?: string;
  env?: NodeJS.ProcessEnv;
}

export interface PacketTracerRuntimeResolvedPaths {
  launcherPath: string;
  resetHelperPath: string;
  appImagePath: string;
  launcherAvailable: boolean;
  resetHelperAvailable: boolean;
  appImageAvailable: boolean;
}

export interface PacketTracerRuntimeProcessMatch {
  pid: number;
  args: string;
  kind: "launcher" | "appimage";
}

export interface PacketTracerRuntimeStatusResult {
  operation: "status";
  phase: Exclude<PacketTracerRuntimePhase, "already-running">;
  isInstalled: boolean;
  isRunning: boolean;
  canLaunch: boolean;
  recoveryAvailable: boolean;
  probe: "idle" | "launcher-process-detected" | "appimage-process-detected";
  processes: PacketTracerRuntimeProcessMatch[];
  paths: PacketTracerRuntimeResolvedPaths;
  problem?: PacketTracerRuntimeProblem;
}

export interface PacketTracerRuntimeLaunchResult {
  operation: "launch";
  phase: PacketTracerRuntimePhase;
  isInstalled: boolean;
  launched: boolean;
  processes: PacketTracerRuntimeProcessMatch[];
  paths: PacketTracerRuntimeResolvedPaths;
  problem?: PacketTracerRuntimeProblem;
}

export interface PacketTracerRuntimeResetResult {
  operation: "reset";
  state: "completed" | "not-installed" | "failed";
  ok: boolean;
  exitCode: number | null;
  stdout: string;
  stderr: string;
  paths: PacketTracerRuntimeResolvedPaths;
  problem?: PacketTracerRuntimeProblem;
}

interface PacketTracerRuntimeDependencies {
  listProcesses: () => Promise<PacketTracerRuntimeProcessMatch[]>;
  spawnLauncher: (command: string, args: string[], options: SpawnCommandOptions) => Promise<void>;
  runResetHelper: (command: string, args: string[], options: SpawnCommandOptions) => Promise<CommandResult>;
  sleep: (ms: number) => Promise<void>;
}

interface SpawnCommandOptions {
  cwd?: string;
  env?: NodeJS.ProcessEnv;
}

interface CommandResult {
  exitCode: number | null;
  stdout: string;
  stderr: string;
}

interface PacketTracerProcessProbe {
  phase: Extract<PacketTracerRuntimePhase, "launching" | "ready">;
  isRunning: true;
  probe: PacketTracerRuntimeStatusResult["probe"];
  processes: PacketTracerRuntimeProcessMatch[];
}

export interface PacketTracerHostRuntime {
  resolvePaths(): Promise<PacketTracerRuntimeResolvedPaths>;
  status(): Promise<PacketTracerRuntimeStatusResult>;
  launch(args?: string[]): Promise<PacketTracerRuntimeLaunchResult>;
  resetLoginState(): Promise<PacketTracerRuntimeResetResult>;
}

export function createPacketTracerHostRuntime(
  options: PacketTracerRuntimeOptions = {},
  dependencies: Partial<PacketTracerRuntimeDependencies> = {}
): PacketTracerHostRuntime {
  const runtimePlatform = options.platform ?? process.platform;
  const runtimeEnv = options.env ?? process.env;
  const defaultPaths = resolveDefaultPacketTracerPaths(runtimePlatform, runtimeEnv);

  const resolvedOptions = {
    launcherPath:
      options.launcherPath ??
      readOptionalEnvPath(runtimeEnv, PACKET_TRACER_RUNTIME_PATH_ENV.launcherPath) ??
      defaultPaths.launcherPath,
    resetHelperPath:
      options.resetHelperPath ??
      readOptionalEnvPath(runtimeEnv, PACKET_TRACER_RUNTIME_PATH_ENV.resetHelperPath) ??
      defaultPaths.resetHelperPath,
    appImagePath:
      options.appImagePath ??
      readOptionalEnvPath(runtimeEnv, PACKET_TRACER_RUNTIME_PATH_ENV.appImagePath) ??
      defaultPaths.appImagePath,
    platform: runtimePlatform,
    readinessTimeoutMs: options.readinessTimeoutMs ?? DEFAULT_READINESS_TIMEOUT_MS,
    readinessPollIntervalMs:
      options.readinessPollIntervalMs ?? DEFAULT_READINESS_POLL_INTERVAL_MS,
    cwd: options.cwd,
    env: runtimeEnv,
  };

  const runtimeDependencies: PacketTracerRuntimeDependencies = {
    listProcesses: dependencies.listProcesses ?? (() => listPacketTracerProcesses(resolvedOptions)),
    spawnLauncher: dependencies.spawnLauncher ?? spawnDetachedCommand,
    runResetHelper: dependencies.runResetHelper ?? runExecFile,
    sleep: dependencies.sleep ?? delay,
  };

  return {
    async resolvePaths() {
      return resolvePacketTracerPaths(resolvedOptions);
    },

    async status() {
      const paths = await resolvePacketTracerPaths(resolvedOptions);
      const configuration = assessLaunchConfiguration(paths, resolvedOptions.platform);

      if (configuration.kind === "not-installed") {
        return {
          operation: "status",
          phase: "not-installed",
          isInstalled: false,
          isRunning: false,
          canLaunch: false,
          recoveryAvailable: paths.resetHelperAvailable,
          probe: "idle",
          processes: [],
          paths,
        };
      }

      if (configuration.kind === "failed") {
        return {
          operation: "status",
          phase: "failed",
          isInstalled: false,
          isRunning: false,
          canLaunch: false,
          recoveryAvailable: paths.resetHelperAvailable,
          probe: "idle",
          processes: [],
          paths,
          problem: configuration.problem,
        };
      }

      try {
        const processes = await runtimeDependencies.listProcesses();
        if (processes.length === 0) {
          return {
            operation: "status",
            phase: "ready",
            isInstalled: true,
            isRunning: false,
            canLaunch: true,
            recoveryAvailable: paths.resetHelperAvailable,
            probe: "idle",
            processes: [],
            paths,
          };
        }

        const probe = classifyProcessProbe(processes);
        return {
          operation: "status",
          phase: probe.phase,
          isInstalled: true,
          isRunning: probe.isRunning,
          canLaunch: false,
          recoveryAvailable: paths.resetHelperAvailable,
          probe: probe.probe,
          processes: probe.processes,
          paths,
        };
      } catch (error) {
        return {
          operation: "status",
          phase: "failed",
          isInstalled: true,
          isRunning: false,
          canLaunch: false,
          recoveryAvailable: paths.resetHelperAvailable,
          probe: "idle",
          processes: [],
          paths,
          problem: createProblem(
            "process-probe-failed",
            `Failed to probe Packet Tracer processes: ${formatError(error)}`
          ),
        };
      }
    },

    async launch(args: string[] = []) {
      const paths = await resolvePacketTracerPaths(resolvedOptions);
      const configuration = assessLaunchConfiguration(paths, resolvedOptions.platform);

      if (configuration.kind === "not-installed") {
        return {
          operation: "launch",
          phase: "not-installed",
          isInstalled: false,
          launched: false,
          processes: [],
          paths,
        };
      }

      if (configuration.kind === "failed") {
        return {
          operation: "launch",
          phase: "failed",
          isInstalled: false,
          launched: false,
          processes: [],
          paths,
          problem: configuration.problem,
        };
      }

      const currentStatus = await this.status();
      if (currentStatus.phase === "launching" || currentStatus.isRunning) {
        return {
          operation: "launch",
          phase: "already-running",
          isInstalled: true,
          launched: false,
          processes: currentStatus.processes,
          paths,
        };
      }

      if (currentStatus.phase === "failed") {
        return {
          operation: "launch",
          phase: "failed",
          isInstalled: true,
          launched: false,
          processes: currentStatus.processes,
          paths,
          problem: currentStatus.problem,
        };
      }

      try {
        await runtimeDependencies.spawnLauncher(paths.launcherPath, args, {
          cwd: resolvedOptions.cwd,
          env: resolvedOptions.env,
        });
      } catch (error) {
        return {
          operation: "launch",
          phase: "failed",
          isInstalled: true,
          launched: false,
          processes: [],
          paths,
          problem: createProblem(
            "launch-spawn-failed",
            `Failed to start Packet Tracer launcher: ${formatError(error)}`
          ),
        };
      }

      const deadline = Date.now() + resolvedOptions.readinessTimeoutMs;
      while (Date.now() < deadline) {
        await runtimeDependencies.sleep(resolvedOptions.readinessPollIntervalMs);

        try {
          const probe = classifyProcessProbe(await runtimeDependencies.listProcesses());
          if (probe.phase === "ready") {
            return {
              operation: "launch",
              phase: "ready",
              isInstalled: true,
              launched: true,
              processes: probe.processes,
              paths,
            };
          }
        } catch (error) {
          return {
            operation: "launch",
            phase: "failed",
            isInstalled: true,
            launched: true,
            processes: [],
            paths,
            problem: createProblem(
              "process-probe-failed",
              `Failed to probe Packet Tracer readiness: ${formatError(error)}`
            ),
          };
        }
      }

      try {
        const processes = await runtimeDependencies.listProcesses();
        if (processes.length === 0) {
          return {
            operation: "launch",
            phase: "failed",
            isInstalled: true,
            launched: true,
            processes: [],
            paths,
            problem: createProblem(
              "readiness-timeout",
              "Packet Tracer launcher started, but no launcher or AppImage process was observed before the readiness timeout elapsed."
            ),
          };
        }

        const probe = classifyProcessProbe(processes);
        return {
          operation: "launch",
          phase: probe.phase,
          isInstalled: true,
          launched: true,
          processes: probe.processes,
          paths,
          problem:
            probe.phase === "launching"
              ? createProblem(
                  "readiness-timeout",
                  "Packet Tracer launcher started, but the AppImage was not observed before the readiness timeout elapsed."
                )
              : undefined,
        };
      } catch (error) {
        return {
          operation: "launch",
          phase: "failed",
          isInstalled: true,
          launched: true,
          processes: [],
          paths,
          problem: createProblem(
            "process-probe-failed",
            `Failed to perform final readiness probe: ${formatError(error)}`
          ),
        };
      }
    },

    async resetLoginState() {
      const paths = await resolvePacketTracerPaths(resolvedOptions);
      const launchConfiguration = assessLaunchConfiguration(paths, resolvedOptions.platform);

      if (launchConfiguration.kind === "not-installed" && !paths.resetHelperAvailable) {
        return {
          operation: "reset",
          state: "not-installed",
          ok: false,
          exitCode: null,
          stdout: "",
          stderr: "",
          paths,
        };
      }

      if (!paths.resetHelperAvailable) {
        return {
          operation: "reset",
          state: "failed",
          ok: false,
          exitCode: null,
          stdout: "",
          stderr: "",
          paths,
          problem: createProblem(
            "reset-helper-missing",
            `Packet Tracer reset helper not found at ${paths.resetHelperPath}`
          ),
        };
      }

      try {
        const result = await runtimeDependencies.runResetHelper(paths.resetHelperPath, [], {
          cwd: resolvedOptions.cwd,
          env: resolvedOptions.env,
        });

        if (result.exitCode === 0) {
          return {
            operation: "reset",
            state: "completed",
            ok: true,
            exitCode: 0,
            stdout: result.stdout,
            stderr: result.stderr,
            paths,
          };
        }

        return {
          operation: "reset",
          state: "failed",
          ok: false,
          exitCode: result.exitCode,
          stdout: result.stdout,
          stderr: result.stderr,
          paths,
          problem: createProblem(
            "reset-command-failed",
            `Packet Tracer reset helper exited with code ${String(result.exitCode)}`
          ),
        };
      } catch (error) {
        return {
          operation: "reset",
          state: "failed",
          ok: false,
          exitCode: null,
          stdout: "",
          stderr: "",
          paths,
          problem: createProblem(
            "reset-command-failed",
            `Failed to execute Packet Tracer reset helper: ${formatError(error)}`
          ),
        };
      }
    },
  };
}

async function resolvePacketTracerPaths(
  options: Required<Pick<PacketTracerRuntimeOptions, "launcherPath" | "resetHelperPath" | "appImagePath">>
): Promise<PacketTracerRuntimeResolvedPaths> {
  const [launcherAvailable, resetHelperAvailable, appImageAvailable] = await Promise.all([
    isExecutable(options.launcherPath),
    isExecutable(options.resetHelperPath),
    isExecutable(options.appImagePath),
  ]);

  return {
    launcherPath: options.launcherPath,
    resetHelperPath: options.resetHelperPath,
    appImagePath: options.appImagePath,
    launcherAvailable,
    resetHelperAvailable,
    appImageAvailable,
  };
}

function resolveDefaultPacketTracerPaths(
  platform: NodeJS.Platform,
  env: NodeJS.ProcessEnv
): Required<Pick<PacketTracerRuntimeOptions, "launcherPath" | "resetHelperPath" | "appImagePath">> {
  if (platform === "win32") {
    return {
      launcherPath: resolveWindowsLauncherPath(env),
      resetHelperPath: "",
      appImagePath: "",
    };
  }

  return {
    launcherPath: DEFAULT_PACKET_TRACER_PATHS.launcherPath,
    resetHelperPath: DEFAULT_PACKET_TRACER_PATHS.resetHelperPath,
    appImagePath: DEFAULT_PACKET_TRACER_PATHS.appImagePath,
  };
}

function resolveWindowsLauncherPath(env: NodeJS.ProcessEnv): string {
  const programFiles = readOptionalEnvPath(env, "ProgramFiles");
  const programFilesX86 = readOptionalEnvPath(env, "ProgramFiles(x86)");

  const envDerivedCandidates = [
    ...(programFiles
      ? [
          `${programFiles}\\Cisco Packet Tracer\\bin\\PacketTracer.exe`,
          `${programFiles}\\Cisco Packet Tracer 9.0\\bin\\PacketTracer.exe`,
          `${programFiles}\\Cisco Packet Tracer 8.2.2\\bin\\PacketTracer.exe`,
          `${programFiles}\\Cisco Packet Tracer 8.2.0\\bin\\PacketTracer.exe`,
        ]
      : []),
    ...(programFilesX86
      ? [
          `${programFilesX86}\\Cisco Packet Tracer\\bin\\PacketTracer.exe`,
          `${programFilesX86}\\Cisco Packet Tracer 9.0\\bin\\PacketTracer.exe`,
          `${programFilesX86}\\Cisco Packet Tracer 8.2.2\\bin\\PacketTracer.exe`,
          `${programFilesX86}\\Cisco Packet Tracer 8.2.0\\bin\\PacketTracer.exe`,
        ]
      : []),
  ];

  const candidate = readFirstExistingWindowsLauncherPath([
    ...envDerivedCandidates,
    ...WINDOWS_PACKET_TRACER_LAUNCHER_CANDIDATES,
  ]);
  return candidate ?? WINDOWS_PACKET_TRACER_LAUNCHER_CANDIDATES[0];
}

function readFirstExistingWindowsLauncherPath(candidates: readonly string[]): string | undefined {
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }

  return undefined;
}

function readOptionalEnvPath(env: NodeJS.ProcessEnv, name: string): string | undefined {
  const raw = env[name]?.trim();
  return raw && raw.length > 0 ? raw : undefined;
}

function assessLaunchConfiguration(
  paths: PacketTracerRuntimeResolvedPaths,
  platform: NodeJS.Platform
):
  | { kind: "valid" }
  | { kind: "not-installed" }
  | { kind: "failed"; problem: PacketTracerRuntimeProblem } {
  if (platform === "win32") {
    if (!paths.launcherAvailable) {
      return { kind: "not-installed" };
    }

    return { kind: "valid" };
  }

  const launchPathCount = Number(paths.launcherAvailable) + Number(paths.appImageAvailable);
  if (launchPathCount === 0) {
    return { kind: "not-installed" };
  }

  if (!paths.launcherAvailable && paths.appImageAvailable) {
    return {
      kind: "failed",
      problem: createProblem(
        "launcher-missing",
        `Packet Tracer launcher not found at ${paths.launcherPath}`
      ),
    };
  }

  if (paths.launcherAvailable && !paths.appImageAvailable) {
    return {
      kind: "failed",
      problem: createProblem(
        "appimage-missing",
        `Packet Tracer AppImage not found at ${paths.appImagePath}`
      ),
    };
  }

  if (!paths.resetHelperAvailable) {
    return {
      kind: "failed",
      problem: createProblem(
        "runtime-misconfigured",
        `Packet Tracer launcher paths are installed, but the reset helper is missing at ${paths.resetHelperPath}`
      ),
    };
  }

  return { kind: "valid" };
}

async function isExecutable(path: string): Promise<boolean> {
  if (path.trim().length === 0) {
    return false;
  }

  try {
    await access(path, constants.X_OK);
    return true;
  } catch {
    return false;
  }
}

async function listPacketTracerProcesses(
  options: Required<Pick<PacketTracerRuntimeOptions, "launcherPath" | "appImagePath" | "platform">>
): Promise<PacketTracerRuntimeProcessMatch[]> {
  if (options.platform === "win32") {
    return listPacketTracerProcessesOnWindows(options.launcherPath);
  }

  return listPacketTracerProcessesOnUnixLike(options);
}

async function listPacketTracerProcessesOnUnixLike(
  options: Required<Pick<PacketTracerRuntimeOptions, "launcherPath" | "appImagePath">>
): Promise<PacketTracerRuntimeProcessMatch[]> {
  const result = await runExecFile("ps", ["-eo", "pid=,args="]);
  const matches: PacketTracerRuntimeProcessMatch[] = [];

  for (const line of result.stdout.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed.length === 0) {
      continue;
    }

    const match = trimmed.match(/^(\d+)\s+(.*)$/);
    if (!match) {
      continue;
    }

    const pid = Number(match[1]);
    const args = match[2];

    if (args.includes(options.appImagePath)) {
      matches.push({ pid, args, kind: "appimage" });
      continue;
    }

    if (args.includes(options.launcherPath)) {
      matches.push({ pid, args, kind: "launcher" });
    }
  }

  return matches;
}

async function listPacketTracerProcessesOnWindows(
  launcherPath: string
): Promise<PacketTracerRuntimeProcessMatch[]> {
  const normalizedLauncherPath = normalizeWindowsPathForComparison(launcherPath);
  const processImage = readProcessImageName(launcherPath) ?? "PacketTracer.exe";
  const result = await runExecFile(
    "powershell.exe",
    [
      "-NoProfile",
      "-Command",
      "$processImage = [string]$args[0]; $escapedProcessImage = $processImage.Replace(\"'\", \"''\"); Get-CimInstance Win32_Process -Filter (\"Name = '\" + $escapedProcessImage + \"'\") | Select-Object ProcessId, ExecutablePath | ConvertTo-Json -Compress",
      processImage,
    ]
  );

  if (result.exitCode !== 0) {
    throw new Error(result.stderr.trim() || `PowerShell process query failed with exit code ${result.exitCode}`);
  }

  const stdout = result.stdout.trim();
  if (stdout.length === 0) {
    return [];
  }

  const parsed = JSON.parse(stdout) as WindowsProcessRecord | WindowsProcessRecord[] | null;
  const records = Array.isArray(parsed) ? parsed : parsed ? [parsed] : [];

  const matches: PacketTracerRuntimeProcessMatch[] = [];
  for (const record of records) {
    const executablePath = normalizeWindowsPathForComparison(record.ExecutablePath);
    if (!executablePath || executablePath !== normalizedLauncherPath) {
      continue;
    }

    const pid = Number(record.ProcessId);
    if (!Number.isFinite(pid)) {
      continue;
    }

    matches.push({
      pid,
      args: record.ExecutablePath ?? launcherPath,
      kind: "appimage",
    });
  }

  return matches;
}

function readProcessImageName(path: string): string | null {
  const trimmedPath = path.trim();
  if (trimmedPath.length === 0) {
    return null;
  }

  const parts = trimmedPath.split(/[\\/]/);
  const imageName = parts[parts.length - 1]?.trim();
  return imageName && imageName.length > 0 ? imageName : null;
}

interface WindowsProcessRecord {
  ProcessId?: number | string;
  ExecutablePath?: string;
}

function normalizeWindowsPathForComparison(candidatePath: string | undefined): string | null {
  const trimmedPath = candidatePath?.trim();
  if (!trimmedPath) {
    return null;
  }

  return path.win32.normalize(trimmedPath).toLowerCase();
}

function classifyProcessProbe(
  processes: PacketTracerRuntimeProcessMatch[]
): PacketTracerProcessProbe {
  const appImageProcesses = processes.filter((process) => process.kind === "appimage");
  if (appImageProcesses.length > 0) {
    return {
      phase: "ready",
      isRunning: true,
      probe: "appimage-process-detected",
      processes: appImageProcesses,
    };
  }

  return {
    phase: "launching",
    isRunning: true,
    probe: "launcher-process-detected",
    processes: processes.filter((process) => process.kind === "launcher"),
  };
}

function spawnDetachedCommand(
  command: string,
  args: string[],
  options: SpawnCommandOptions
): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      detached: true,
      stdio: "ignore",
    });

    child.once("error", reject);
    child.once("spawn", () => {
      child.unref();
      resolve();
    });
  });
}

function runExecFile(
  command: string,
  args: string[],
  options: SpawnCommandOptions = {}
): Promise<CommandResult> {
  return new Promise((resolve, reject) => {
    execFile(
      command,
      args,
      {
        cwd: options.cwd,
        env: options.env,
        encoding: "utf8",
      },
      (error, stdout, stderr) => {
        const exitCode = error && "code" in error ? Number(error.code) || null : 0;

        if (error && exitCode === null) {
          reject(error);
          return;
        }

        resolve({
          exitCode,
          stdout,
          stderr,
        });
      }
    );
  });
}

function createProblem(
  code: PacketTracerRuntimeFailureCode,
  message: string
): PacketTracerRuntimeProblem {
  return { code, message };
}

function formatError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
