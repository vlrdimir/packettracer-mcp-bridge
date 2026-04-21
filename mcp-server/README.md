# Packet Tracer MCP Server

This package contains the stdio MCP server for the ExApp-only Packet Tracer workflow under `packettracer-mcp-bridge/`.

This README is intentionally focused on two things:

- the MCP contract exposed by this package
- how to run the server against the PT-side ExApp

## MCP contract

The public tool surface is exactly:

- `packettracer_launch`
- `packettracer_execute`
- `packettracer_reset`
- `packettracer_status`

`server://info` is the public metadata surface for the execution model, runtime adapter metadata, bridge contract, and tool catalog.

## Tool responsibilities

| Public tool | Governs phase(s) | Public owner | Internal bridge handoff |
| --- | --- | --- | --- |
| `packettracer_launch` | `runtime-start` | host/orchestrator | starts the host runtime before ExApp-backed work |
| `packettracer_execute` | `observe`, `configure`, `verify` | host/orchestrator | routes through internal `invoke` to the selected ExApp bridge target |
| `packettracer_reset` | operational-reset | host/orchestrator | host-only manual helper; not part of the safe runtime flow |
| `packettracer_status` | `runtime`, `readiness`, `verify` | host/orchestrator | surfaces runtime diagnostics plus ExApp `read_status` context |

This is why internal verbs such as `add_device`, `connect_devices`, `run_device_cli`, `list_devices`, and `list_links` are not public MCP tools even when the bridge can call matching internal operations.

## Internal bridge contract

The MCP server talks to the PT-side app through the internal ExApp bridge contract.

The next-phase bridge operations are:

- `query_capabilities`
- `invoke`
- `read_status`

The bridge executable supports two CLI modes:

- handshake command: `<bridge> --packet-tracer-bridge-handshake`
  - stdin: none required
  - stdout: exactly one handshake JSON payload
- invoke command: `<bridge> --packet-tracer-bridge-invoke`
  - stdin: exactly one `BridgeRequest` JSON document
  - stdout: exactly one `BridgeResponse` JSON payload

Business-level failures should still prefer canonical JSON envelopes on stdout instead of relying only on non-zero exit codes.

## Runtime model

The active runtime flow is:

1. `packettracer_launch` can start the Packet Tracer host runtime
2. `packettracer_status` reads host runtime state plus ExApp bridge readiness
3. `packettracer_execute` routes bridge-backed work through the ExApp

## Build and start

Run from `packettracer-mcp-bridge/mcp-server/`:

```bash
npm install
npm run build
npm start
```

Optional development entrypoint:

```bash
npm run dev
```

## Required environment variables

The MCP server needs the ExApp bridge target configured in its shell:

- `PACKET_TRACER_EXAPP_BRIDGE_COMMAND`
- `PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON`
- `PACKET_TRACER_EXAPP_BRIDGE_CWD`

The built bridge executable in this package is:

```text
dist/packettracer-exapp-bridge.js
```

Because that file has a node shebang and the build marks it executable, the simplest local setup is:

```bash
export PACKET_TRACER_EXAPP_BRIDGE_COMMAND="$PWD/dist/packettracer-exapp-bridge.js"
export PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON='[]'
export PACKET_TRACER_EXAPP_BRIDGE_CWD="$PWD"
```

## Runtime requirements

The host runtime adapter targets the repo-approved Packet Tracer wrapper paths:

- `/usr/bin/packettracer`
- `/usr/bin/packettracer-reset-login-state`
- `/usr/lib/packettracer/packettracer.AppImage`

`packettracer_launch`, `packettracer_reset`, and `packettracer_status` act on those host-side paths. `packettracer_execute` additionally requires one configured ExApp bridge target.

## Match the logged ExApp bridge port

The PT-side ExApp binds a localhost bridge on `127.0.0.1:39150-39159` and logs the chosen port inside Packet Tracer.

If you want MCP to attach to the exact listener shown in the Packet Tracer log, also export:

```bash
export PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT=39150
```

Replace `39150` with the actual port printed by the ExApp log, for example:

```text
Local experimental host bridge bound at 127.0.0.1:39150.
```

If `PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT` is unset, the bridge executable falls back to scanning the local range.

## Run the server

Typical local sequence from `packettracer-mcp-bridge/mcp-server/`:

```bash
npm install
npm run build
export PACKET_TRACER_EXAPP_BRIDGE_COMMAND="$PWD/dist/packettracer-exapp-bridge.js"
export PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON='[]'
export PACKET_TRACER_EXAPP_BRIDGE_CWD="$PWD"
export PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT=39150
npm start
```

Replace the port value with the one printed by the PT-side ExApp log.

## AI coding agent setup

If you use a coding AI agent (Cursor, Claude Code, Codex CLI, or similar), add and follow this skill:

- `packettracer-mcp-bridge/mcp-server/SKILL.md`

That skill document encodes the expected tool flow, safe sequencing, and MCP usage rules for this server package.

Install examples with `npx skills`:

```bash
# From GitHub (target only the mcp-server skill directory)
npx skills add https://github.com/vlrdimir/packettracer-mcp-bridge/tree/master/mcp-server --skill packettracer-mcp-bridge

# From local clone
npx skills add ./mcp-server --skill packettracer-mcp-bridge
```

## Operator notes

Suggested usage order:

1. install and launch the PT-side ExApp from `packettracer-mcp-bridge/apps/`
2. confirm the ExApp log line that prints `127.0.0.1:3915X`
3. export the bridge env vars in the MCP server shell
4. start the MCP server
5. use `packettracer_status` first to confirm bridge readiness

### Readiness

- require ExApp `read_status` to report a ready bridge before active work
- treat the ExApp handshake as the authoritative readiness signal

### Config

- run through `packettracer_execute`
- treat ExApp as the only configuration backend
- keep router examples aligned with catalog-backed serial-capable routers that use `HWIC-2T` when serial links are part of the plan

### Verify

- prefer structured proof first, then CLI and transcript evidence
- use `packettracer_status` when you need bridge readiness plus host runtime diagnostics

## Phase and internal-operation matrix

| Phase | Public tool entrypoint | Internal owner | Internal bridge artifact or operation |
| --- | --- | --- | --- |
| `runtime-start` | `packettracer_launch` | host/orchestrator | `launch` |
| `readiness` | `packettracer_status` | ExApp | `read_status` |
| `observe` | `packettracer_execute`, `packettracer_status` | ExApp | `invoke`, `read_status` |
| `configure` | `packettracer_execute` | ExApp | `invoke` |
| `verify` | `packettracer_execute`, `packettracer_status` | ExApp | `invoke`, `read_status` |
| `operational-reset` | `packettracer_reset` | host/orchestrator | `resetLoginState` |

## Timeout policy

The Packet Tracer bridge contract uses a fail-closed timeout policy:

- default: `30000` ms
- minimum: `100` ms
- maximum: `120000` ms

`packettracer_execute.timeoutMs` is clamped to that range.

## Notes

- `packettracer_reset` is manual-only operational work, not a harmless smoke step.
- The public documentation path for this package is `packettracer-mcp-bridge/mcp-server/README.md`.
- The PT-side companion documentation path is `packettracer-mcp-bridge/apps/README.md`.
