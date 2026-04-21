import { createBridgeErrorResponse, createBridgeSuccessResponse, type BridgeRequest } from "../bridge/index.js";

const HANDSHAKE_FLAG = "--packet-tracer-bridge-handshake";
const INVOKE_FLAG = "--packet-tracer-bridge-invoke";

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const mode = args.find((value) => value === HANDSHAKE_FLAG || value === INVOKE_FLAG);
  const scenario = args.find((value) => value !== HANDSHAKE_FLAG && value !== INVOKE_FLAG) ?? "success";

  if (mode === HANDSHAKE_FLAG) {
    writeJson({
      protocolVersion: "2026-04-15",
      backend: "exapp",
      readiness: scenario === "booting" ? "booting" : "ready",
      backendRequestId: `worker-${scenario}`,
      details: { scenario, mode: "handshake" },
    });
    return;
  }

  if (mode !== INVOKE_FLAG) {
    throw new Error(`Expected ${HANDSHAKE_FLAG} or ${INVOKE_FLAG}.`);
  }

  const request = await readRequest();

  if (scenario === "timeout") {
    await sleep(request.timeoutMs + 25);
  }

  if (scenario === "malformed") {
    writeJson({ not: "a-bridge-response" });
    return;
  }

  const response =
    scenario === "failure"
      ? createBridgeErrorResponse(
          request,
          "degraded",
          {
            code: "malformed-response",
            message: "Forced worker failure.",
            retryable: false,
            details: {
              scenario,
            },
          },
          7
        )
      : createBridgeSuccessResponse(
          request,
          "ready",
          {
            stdout:
              request.operation === "invoke"
                ? `[exapp:${scenario}] ${request.operation} ${request.payload.entrypoint ?? ""}`.trim()
                : `[exapp:${scenario}] ${request.operation}`,
            stderr: "",
            exitCode: 0,
            backendRequestId: `worker-${scenario}`,
            metadata: {
              adapter: "exapp-smoke-worker",
              scenario,
            },
          },
          7
        );

  writeJson(response);
}

function readRequest(): Promise<BridgeRequest> {
  return new Promise((resolve, reject) => {
    let buffer = "";

    process.stdin.setEncoding("utf8");
    process.stdin.resume();
    process.stdin.on("data", (chunk: string) => {
      buffer += chunk;
    });
    process.stdin.once("end", () => {
      try {
        resolve(JSON.parse(buffer) as BridgeRequest);
      } catch (error) {
        reject(error);
      }
    });
    process.stdin.once("error", reject);
  });
}

function writeJson(payload: unknown): void {
  process.stdout.write(JSON.stringify(payload));
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`${message}\n`);
  process.exitCode = 1;
});
