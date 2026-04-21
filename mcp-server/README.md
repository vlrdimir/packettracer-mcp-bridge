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

PowerShell equivalent:

```powershell
$env:PACKET_TRACER_EXAPP_BRIDGE_COMMAND = "$PWD/dist/packettracer-exapp-bridge.js"
$env:PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON = '[]'
$env:PACKET_TRACER_EXAPP_BRIDGE_CWD = "$PWD"
```

Optional host runtime overrides (useful on Windows or custom installs):

- `PACKET_TRACER_LAUNCHER_PATH`
- `PACKET_TRACER_RESET_HELPER_PATH`
- `PACKET_TRACER_APPIMAGE_PATH`

On Windows, `hostRuntime` will also try common `PacketTracer.exe` install locations automatically before falling back to `PACKET_TRACER_LAUNCHER_PATH` overrides.

## Runtime requirements

The host runtime adapter targets the repo-approved Packet Tracer wrapper paths:

- `/usr/bin/packettracer`
- `/usr/bin/packettracer-reset-login-state`
- `/usr/lib/packettracer/packettracer.AppImage`

On Linux, those paths remain the default assumptions.

On Windows, runtime launch only requires a valid Packet Tracer launcher executable (for example `PacketTracer.exe`). AppImage and reset helper paths are optional there.

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

PowerShell equivalent:

```powershell
$env:PACKET_TRACER_EXAPP_BRIDGE_COMMAND = "$PWD/dist/packettracer-exapp-bridge.js"
$env:PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON = '[]'
$env:PACKET_TRACER_EXAPP_BRIDGE_CWD = "$PWD"
$env:PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT = '39150'
npm start
```

Replace the port value with the one printed by the PT-side ExApp log.

## Example `mcp.json` configuration

If your MCP client expects a Cursor-style or IDE-agent JSON server config, start from `packettracer-mcp-bridge/mcp-server/mcp.json.example`.

Base example:

```json
{
  "servers": {
    "packettracer": {
      "type": "stdio",
      "command": "node",
      "args": [
        "<ABSOLUTE_PATH_TO_REPO>/mcp-server/dist/index.js"
      ],
      "env": {
        "PACKET_TRACER_EXAPP_BRIDGE_COMMAND": "node",
        "PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON": "[\"<ABSOLUTE_PATH_TO_REPO>/mcp-server/dist/packettracer-exapp-bridge.js\"]",
        "PACKET_TRACER_EXAPP_BRIDGE_CWD": "<ABSOLUTE_PATH_TO_REPO>/mcp-server",
        "PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT": "39150",
        "PACKET_TRACER_LAUNCHER_PATH": "<ABSOLUTE_PATH_TO_PACKETTRACER_EXE>"
      }
    }
  }
}
```

Replace:

- `<ABSOLUTE_PATH_TO_REPO>` with your local clone path
- `39150` with the exact ExApp bridge port printed by Packet Tracer
- `<ABSOLUTE_PATH_TO_PACKETTRACER_EXE>` with your `PacketTracer.exe` path on Windows

The example uses `command: "node"` plus the built `dist/index.js` and `dist/packettracer-exapp-bridge.js` files, so run `npm install` and `npm run build` in `mcp-server/` first.

OS-specific note:

- On **Windows**, keep `PACKET_TRACER_LAUNCHER_PATH` and point it at `PacketTracer.exe` if auto-discovery is not enough.
- On **Linux**, you can remove `PACKET_TRACER_LAUNCHER_PATH` entirely and optionally add `PACKET_TRACER_APPIMAGE_PATH` if you want to override the default AppImage location.

## Windows smoke test

After `npm install` and `npm run build`, you can run a focused Windows smoke check for:

1. `packettracer_status`
2. `packettracer_execute` read-only (`experimental.operation=list_devices`)

From `packettracer-mcp-bridge/mcp-server/`:

```powershell
npm run smoke:windows
```

The script performs a preflight first (important env + path checks), then fails fast with step-by-step diagnostics when readiness is not `ready`.

That preflight validates the bridge command/cwd/port setup, checks that `PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON` is valid JSON when present, and expects the ExApp bridge port to stay in the `39150-39159` range.

For machine-readable output (CI/log parser), use JSON mode:

```powershell
npx tsx src/scripts/windowsMvpSmoke.ts --json
```

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
