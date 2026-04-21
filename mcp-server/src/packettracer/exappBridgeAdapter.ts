import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { z } from "zod";

import {
  createBridgeSuccessResponse,
  createCanonicalBridgeErrorResponse,
  type BridgeReadinessState,
  type BridgeRequest,
  type BridgeResponse,
  type BridgeReadStatusSuccessPayload,
  type PacketTracerBridgeAdapter,
  validateBridgeRequest,
  validateBridgeResponse,
} from "../bridge/index.js";
import {
  createPacketTracerHostRuntime,
  type PacketTracerHostRuntime,
  type PacketTracerRuntimeStatusResult,
} from "./runtime/index.js";
import { EXAPP_BRIDGE_MODE_ARGS, exAppHandshakeSchema, type ExAppHandshake } from "./exappBridgeCli.js";
import { createExAppCapabilityResult } from "./exappCapabilityResult.js";
import { createReadStatusResult } from "./readStatusResult.js";

export const PACKET_TRACER_EXAPP_TARGET_ENV = {
  command: "PACKET_TRACER_EXAPP_BRIDGE_COMMAND",
  argsJson: "PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON",
  cwd: "PACKET_TRACER_EXAPP_BRIDGE_CWD",
} as const;

export interface ExAppBridgeAdapterOptions {
  command: string;
  args?: string[];
  cwd?: string;
  env?: NodeJS.ProcessEnv;
  runtime?: PacketTracerHostRuntime;
}

interface SpawnProcessOptions {
  command: string;
  args: string[];
  cwd?: string;
  env?: NodeJS.ProcessEnv;
}

interface SpawnedProcess {
  child: ChildProcessWithoutNullStreams;
  ready: Promise<void>;
}

interface ExAppBridgeAdapterDependencies {
  spawnProcess: (options: SpawnProcessOptions) => SpawnedProcess;
  now: () => number;
}

type ExAppHandshakeReadinessGate = Extract<BridgeReadinessState, "booting" | "not-ready" | "degraded">;

export class ExAppBridgeAdapter implements PacketTracerBridgeAdapter {
  readonly backend = "exapp" as const;

  private readiness: BridgeReadinessState = "not-ready";
  private readonly args: string[];
  private readonly runtime: PacketTracerHostRuntime;
  private readonly dependencies: ExAppBridgeAdapterDependencies;
  private readonly command: string;
  private readonly cwd?: string;
  private readonly env?: NodeJS.ProcessEnv;

  constructor(
    options: ExAppBridgeAdapterOptions,
    dependencies: Partial<ExAppBridgeAdapterDependencies> = {}
  ) {
    this.command = options.command;
    this.args = options.args ?? [];
    this.cwd = options.cwd;
    this.env = options.env;
    this.runtime = options.runtime ?? createPacketTracerHostRuntime();
    this.dependencies = {
      spawnProcess: dependencies.spawnProcess ?? spawnExAppProcess,
      now: dependencies.now ?? (() => Date.now()),
    };
  }

  getReadiness(): BridgeReadinessState {
    return this.readiness;
  }

  async invoke(request: BridgeRequest): Promise<BridgeResponse> {
    const validatedRequest = validateBridgeRequest(request);
    const startedAt = this.dependencies.now();

    if (validatedRequest.backend !== this.backend) {
      this.readiness = "unavailable";
      return createCanonicalBridgeErrorResponse(validatedRequest, {
        code: "unavailable-backend",
        readiness: this.readiness,
        durationMs: this.durationSince(startedAt),
        message: `ExApp bridge adapter is bound to ${this.backend}, but received ${validatedRequest.backend}.`,
        retryable: false,
        details: {
          adapterBackend: this.backend,
          requestBackend: validatedRequest.backend,
        },
      });
    }

    try {
      const response = await this.handleRequest(validatedRequest, startedAt);
      this.readiness = response.readiness;
      return validateBridgeResponse(response);
    } catch (error) {
      return this.normalizeError(validatedRequest, error, startedAt);
    }
  }

  private async handleRequest(request: BridgeRequest, startedAt: number): Promise<BridgeResponse> {
    const handshake = await this.readHandshake(startedAt, request);
    this.readiness = handshake.readiness;

    switch (request.operation) {
      case "invoke":
        this.assertHandshakeReadyForInvoke(handshake);
        return this.readInvokeResponse(request, startedAt);
      case "query_capabilities":
        return createBridgeSuccessResponse(
          request,
          handshake.readiness,
          this.createCapabilitiesResult(request, handshake),
          this.durationSince(startedAt)
        );
      case "read_status":
        return createBridgeSuccessResponse(
          request,
          handshake.readiness,
          await this.createReadStatusResult(request, handshake),
          this.durationSince(startedAt)
        );
    }
  }

  private async readHandshake(startedAt: number, request: BridgeRequest): Promise<ExAppHandshake> {
    const payload = await this.runBridgeProcess("handshake", {
      timeoutMs: this.remainingBudget(startedAt, request.timeoutMs, "handshake"),
    });

    try {
      return exAppHandshakeSchema.parse(payload);
    } catch (error) {
      if (error instanceof z.ZodError) {
        throw new ExAppBridgeMalformedResponseError(
          "The Packet Tracer backend returned a malformed response.",
          {
            stage: "handshake",
            reason: summarizeZodError(error),
          }
        );
      }

      throw error;
    }
  }

  private assertHandshakeReadyForInvoke(handshake: ExAppHandshake): void {
    if (handshake.readiness === "ready") {
      return;
    }

    if (handshake.readiness === "unavailable") {
      throw new ExAppBridgeUnavailableError("The requested Packet Tracer backend is unavailable.", {
        stage: "handshake",
        ...handshake.details,
        backendRequestId: handshake.backendRequestId ?? "",
      });
    }

    throw new ExAppBridgeNotReadyError(
      this.mapHandshakeNotReadyReadiness(handshake.readiness),
      {
        stage: "handshake",
        handshakeReadiness: handshake.readiness,
        ...handshake.details,
      },
      handshake.backendRequestId
    );
  }

  private mapHandshakeNotReadyReadiness(
    readiness: ExAppHandshakeReadinessGate
  ): Extract<BridgeReadinessState, "booting" | "not-ready"> {
    return readiness === "booting" ? "booting" : "not-ready";
  }

  private async readInvokeResponse(
    request: BridgeRequest,
    startedAt: number
  ): Promise<BridgeResponse> {
    const payload = await this.runBridgeProcess("response", {
      timeoutMs: this.remainingBudget(startedAt, request.timeoutMs, "response"),
      stdin: JSON.stringify(request),
    });

    let response: BridgeResponse;
    try {
      response = validateBridgeResponse(payload);
    } catch (error) {
      if (error instanceof z.ZodError) {
        throw new ExAppBridgeMalformedResponseError(
          "The Packet Tracer backend returned a malformed response.",
          {
            stage: "response",
            reason: summarizeZodError(error),
          }
        );
      }

      throw error;
    }

    if (response.backend !== this.backend) {
      throw new ExAppBridgeMalformedResponseError(
        `ExApp bridge response backend ${response.backend} did not match expected ${this.backend}.`,
        {
          stage: "response",
          expectedBackend: this.backend,
          responseBackend: response.backend,
        }
      );
    }

    if (response.correlationId !== request.correlationId) {
      throw new ExAppBridgeMalformedResponseError(
        `ExApp bridge response correlationId ${response.correlationId} did not match request ${request.correlationId}.`,
        {
          stage: "response",
          expectedCorrelationId: request.correlationId,
          responseCorrelationId: response.correlationId,
        }
      );
    }

    if (response.operation !== request.operation) {
      throw new ExAppBridgeMalformedResponseError(
        `ExApp bridge response operation ${response.operation} did not match request ${request.operation}.`,
        {
          stage: "response",
          expectedOperation: request.operation,
          responseOperation: response.operation,
        }
      );
    }

    return response;
  }

  private createCapabilitiesResult(
    request: Extract<BridgeRequest, { operation: "query_capabilities" }>,
    handshake: ExAppHandshake
  ) {
    void request;

    return createExAppCapabilityResult({
      readiness: handshake.readiness,
      details: handshake.details,
    });
  }

  private async createReadStatusResult(
    request: Extract<BridgeRequest, { operation: "read_status" }>,
    handshake: ExAppHandshake
  ): Promise<BridgeReadStatusSuccessPayload> {
    return createReadStatusResult({
      handshakeReadiness: handshake.readiness,
      backendRequestId: handshake.backendRequestId,
      handshakeDetails: handshake.details,
      runtimeStatus: request.payload.includeRuntimeDiagnostics
        ? await this.readRuntimeStatus()
        : null,
      includeRuntimeDiagnostics: request.payload.includeRuntimeDiagnostics,
      includeHandshakeDetails: request.payload.includeHandshakeDetails,
    });
  }

  private async readRuntimeStatus(): Promise<PacketTracerRuntimeStatusResult | null> {
    try {
      return await this.runtime.status();
    } catch {
      return null;
    }
  }

  private normalizeError(
    request: BridgeRequest,
    error: unknown,
    startedAt: number
  ): BridgeResponse {
    if (error instanceof ExAppBridgeTimeoutError) {
      this.readiness = "degraded";
      return createCanonicalBridgeErrorResponse(request, {
        code: "timeout",
        readiness: this.readiness,
        durationMs: this.durationSince(startedAt),
        details: {
          stage: error.stage,
          timeoutMs: String(request.timeoutMs),
          stageTimeoutMs: String(error.timeoutMs),
        },
      });
    }

    if (error instanceof ExAppBridgeNotReadyError) {
      this.readiness = error.readiness;
      return createCanonicalBridgeErrorResponse(request, {
        code: "not-ready",
        readiness: this.readiness,
        durationMs: this.durationSince(startedAt),
        details: {
          ...error.details,
          readiness: error.readiness,
          backendRequestId: error.backendRequestId ?? "",
        },
      });
    }

    if (error instanceof ExAppBridgeUnavailableError) {
      this.readiness = "unavailable";
      return createCanonicalBridgeErrorResponse(request, {
        code: "unavailable-backend",
        readiness: this.readiness,
        durationMs: this.durationSince(startedAt),
        message: error.message,
        details: error.details,
      });
    }

    if (error instanceof ExAppBridgeMalformedResponseError || error instanceof z.ZodError) {
      this.readiness = "degraded";
      return createCanonicalBridgeErrorResponse(request, {
        code: "malformed-response",
        readiness: this.readiness,
        durationMs: this.durationSince(startedAt),
        message: "The Packet Tracer backend returned a malformed response.",
        details:
          error instanceof ExAppBridgeMalformedResponseError
            ? error.details
            : {
                reason: summarizeZodError(error),
              },
      });
    }

    this.readiness = "unavailable";
    return createCanonicalBridgeErrorResponse(request, {
      code: "unavailable-backend",
      readiness: this.readiness,
      durationMs: this.durationSince(startedAt),
      message: `Failed to start ExApp bridge process: ${formatError(error)}`,
      details: {
        command: this.command,
      },
    });
  }

  private async withTimeout<T>(promise: Promise<T>, timeoutMs: number, error: Error): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const timeout = setTimeout(() => reject(error), timeoutMs);

      promise.then(
        (value) => {
          clearTimeout(timeout);
          resolve(value);
        },
        (reason) => {
          clearTimeout(timeout);
          reject(reason);
        }
      );
    });
  }

  private async runBridgeProcess(
    stage: "handshake" | "response",
    options: { timeoutMs: number; stdin?: string }
  ): Promise<unknown> {
    const modeArg =
      stage === "handshake" ? EXAPP_BRIDGE_MODE_ARGS.handshake : EXAPP_BRIDGE_MODE_ARGS.invoke;
    const processHandle = this.dependencies.spawnProcess({
      command: this.command,
      args: [...this.args, modeArg],
      cwd: this.cwd,
      env: this.env,
    });

    return new Promise<unknown>((resolve, reject) => {
      let stdout = "";
      let stderr = "";
      let settled = false;

      const finish = (callback: () => void) => {
        if (settled) {
          return;
        }

        settled = true;
        cleanup();
        callback();
      };

      const onStdout = (chunk: Buffer) => {
        stdout += chunk.toString("utf8");
      };

      const onStderr = (chunk: Buffer) => {
        stderr += chunk.toString("utf8");
      };

      const onExit = (code: number | null, signal: NodeJS.Signals | null) => {
        finish(() => {
          if (code !== 0) {
            reject(
              new ExAppBridgeUnavailableError(
                `ExApp bridge process exited during ${stage} without completing the CLI contract.`,
                {
                  stage,
                  exitCode: String(code ?? "null"),
                  signal: signal ?? "none",
                  stderr: stderr.trim(),
                }
              )
            );
            return;
          }

          try {
            resolve(parseJsonPayload(stdout, stage));
          } catch (error) {
            reject(error);
          }
        });
      };

      const onError = (error: Error) => {
        finish(() => {
          reject(
            new ExAppBridgeUnavailableError(
              `Failed to spawn ExApp bridge process: ${error.message}`,
              {
                stage,
                command: this.command,
              }
            )
          );
        });
      };

      const timeout = setTimeout(() => {
        finish(() => {
          if (!processHandle.child.killed) {
            processHandle.child.kill();
          }

          reject(new ExAppBridgeTimeoutError(stage, options.timeoutMs));
        });
      }, options.timeoutMs);

      const cleanup = () => {
        clearTimeout(timeout);
        processHandle.child.stdout.off("data", onStdout);
        processHandle.child.stderr.off("data", onStderr);
        processHandle.child.off("exit", onExit);
        processHandle.child.off("error", onError);
      };

      processHandle.child.stdout.on("data", onStdout);
      processHandle.child.stderr.on("data", onStderr);
      processHandle.child.once("exit", onExit);
      processHandle.child.once("error", onError);

      this.withTimeout(
        processHandle.ready,
        options.timeoutMs,
        new ExAppBridgeTimeoutError(stage, options.timeoutMs)
      ).then(
        () => {
          if (options.stdin !== undefined) {
            processHandle.child.stdin.end(options.stdin);
            return;
          }

          processHandle.child.stdin.end();
        },
        (error) => {
          finish(() => {
            if (!processHandle.child.killed) {
              processHandle.child.kill();
            }

            reject(error);
          });
        }
      );
    });
  }

  private durationSince(startedAt: number): number {
    return Math.max(0, Math.round(this.dependencies.now() - startedAt));
  }

  private remainingBudget(
    startedAt: number,
    timeoutMs: number,
    stage: "handshake" | "response"
  ): number {
    const remainingMs = timeoutMs - this.durationSince(startedAt);
    if (remainingMs <= 0) {
      throw new ExAppBridgeTimeoutError(stage, 0);
    }

    return remainingMs;
  }
}

export function createExAppBridgeAdapter(
  options: ExAppBridgeAdapterOptions,
  dependencies: Partial<ExAppBridgeAdapterDependencies> = {}
): ExAppBridgeAdapter {
  return new ExAppBridgeAdapter(options, dependencies);
}

export function resolveExAppBridgeTargetFromEnv(
  env: NodeJS.ProcessEnv = process.env
): ExAppBridgeAdapterOptions | null {
  const command = env[PACKET_TRACER_EXAPP_TARGET_ENV.command]?.trim();
  if (!command) {
    return null;
  }

  const argsValue = env[PACKET_TRACER_EXAPP_TARGET_ENV.argsJson]?.trim();
  const cwd = env[PACKET_TRACER_EXAPP_TARGET_ENV.cwd]?.trim();

  return {
    command,
    args: parseTargetArgs(argsValue),
    ...(cwd && cwd.length > 0 ? { cwd } : {}),
    env,
  };
}

function spawnExAppProcess(options: SpawnProcessOptions): SpawnedProcess {
  const child = spawn(options.command, options.args, {
    cwd: options.cwd,
    env: options.env,
    stdio: ["pipe", "pipe", "pipe"],
  });

  const ready = new Promise<void>((resolve, reject) => {
    let settled = false;

    child.once("spawn", () => {
      settled = true;
      resolve();
    });

    child.once("error", (error) => {
      if (settled) {
        return;
      }

      settled = true;
      reject(error);
    });
  });

  return { child, ready };
}

function parseJsonPayload(payload: string, stage: string): unknown {
  const normalizedPayload = payload.trim();
  if (normalizedPayload.length === 0) {
    throw new ExAppBridgeMalformedResponseError(
      `ExApp ${stage} payload was empty.`,
      {
        stage,
      }
    );
  }

  try {
    return JSON.parse(normalizedPayload);
  } catch (error) {
    throw new ExAppBridgeMalformedResponseError(
      `Failed to parse ExApp ${stage} payload as JSON: ${formatError(error)}`,
      {
        stage,
      }
    );
  }
}

function parseTargetArgs(value: string | undefined): string[] {
  if (!value) {
    return [];
  }

  try {
    return z.array(z.string()).parse(JSON.parse(value));
  } catch {
    return [];
  }
}

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function summarizeZodError(error: z.ZodError): string {
  const issue = error.issues[0];
  if (!issue) {
    return "Schema validation failed.";
  }

  const path = issue.path.length > 0 ? issue.path.join(".") : "response";
  return `${path}: ${issue.message}`;
}

class ExAppBridgeTimeoutError extends Error {
  constructor(
    readonly stage: "handshake" | "response",
    readonly timeoutMs: number
  ) {
    super(`ExApp bridge ${stage} exceeded the configured timeout of ${timeoutMs}ms.`);
  }
}

class ExAppBridgeUnavailableError extends Error {
  constructor(
    message: string,
    readonly details: Record<string, string> = {}
  ) {
    super(message);
  }
}

class ExAppBridgeMalformedResponseError extends Error {
  constructor(
    message: string,
    readonly details: Record<string, string> = {}
  ) {
    super(message);
  }
}

class ExAppBridgeNotReadyError extends Error {
  constructor(
    readonly readiness: Extract<BridgeReadinessState, "booting" | "not-ready">,
    readonly details: Record<string, string> = {},
    readonly backendRequestId?: string
  ) {
    super(`ExApp bridge reported readiness ${readiness}.`);
  }
}
