---
name: packettracer-mcp-bridge
description: Use the Packet Tracer MCP local-experimental workflow safely with correct invoke actions, sequencing, and verification guidance.
---

# Packet Tracer MCP Skills Guide

This file is a practical guide for using `packettracer_execute` with the repo-local experimental invoke path:

- `payload.experimental.feature = "local-experimental-message"`
- `payload.experimental.mode = "string"`

It explains **which invoke actions are available**, **what each one is good for**, and **which action should be preferred for a given use case**.

This guide now distinguishes two related but different surfaces:

- **Host-side MCP invoke path** — what `packettracer_execute.payload.experimental` currently validates in `mcp-server/src/tools/packetTracerTools.ts`
- **PT-side local experimental bridge** — what the Java ExApp can now accept directly on its localhost bridge after the PT-side bridge patch/rebuild

> Important: this path is still **non-official**, **local-only**, and **opt-in**. Use it as a repo-local automation surface, not as a claim about official Cisco Packet Tracer protocol support.

> Important implementation note: the PT-side ExApp now has additional local-bridge operations for module work (`add_module`, `remove_module`, `get_device_module_layout`, `add_module_at`) and for mutation/config work (`add_device`, `connect_devices`, `set_interface_ip`, `set_default_gateway`, `add_static_route`, `run_device_cli`). This guide calls out where PT-side support already exists but host-side MCP wiring still has not been extended.

---

## Quick decision guide

Use this first when choosing an action.

### Best action by intent

- **Need router CLI output** → `run_device_cli`
- **Need PC command output** → `run_device_cli`
- **Need rich PC terminal transcript / buffer details** → `probe_terminal_transcript`
- **Need router submode workflow (e.g. OSPF `router ospf` then `network ... area`)** → `probe_terminal_transcript` with `b64:` multiline CLI batch
- **Need authoritative topology or interface proof** → `list_links`, `get_device_detail`, `read_interface_status`, `read_port_power_state`
- **Need to add/remove/connect devices** → `add_device`, `remove_device`, `connect_devices`, `delete_link`
- **Need to assign IPs / gateway / routes** → `set_interface_ip`, `set_default_gateway`, `run_device_cli` for router static routes
- **Need ICMP proof from a PC terminal** → `probe_terminal_transcript` with `ping ...`
- **Need ICMP helper surface** → `run_ping`

### Preferred mental model

- `run_device_cli` = **single-command result**
- `probe_terminal_transcript` = **terminal observation / transcript-ish view**
- `probe_terminal_transcript` (router submode case) = **stateful CLI path for command sequences that must stay in a child config mode**
- `list_*` / `read_*` / `get_device_detail` = **structured state proof**

### Capability split: host MCP vs PT-side local bridge

#### Host-side MCP invoke path (current `packettracer_execute.payload.experimental` schema)

Currently validated through `mcp-server/src/tools/packetTracerTools.ts`:

- `handshake`
- `echo`
- `list_devices`
- `list_components`
- `list_ports`
- `list_links`
- `get_device_detail`
- `read_interface_status`
- `read_port_power_state`
- `add_device`
- `connect_devices`
- `set_interface_ip`
- `set_default_gateway`
- `add_static_route`
- `run_device_cli`
- `set_port_power_state`
- `run_ping`
- `probe_terminal_transcript`
- `remove_device`
- `delete_link`
- `get_device_module_layout`
- `add_module`
- `remove_module`
- `add_module_at`

#### PT-side local experimental bridge (current Java ExApp after PT-side patch/rebuild)

Currently callable directly on the localhost PT-side bridge:

- all read-only operations above
- all mutation/config/CLI operations above

Still operationally caveated even though now host-MCP-wired:

- `delete_link` — exposed, but can return `ok=false` depending on Packet Tracer runtime state/link identity
- `add_module`, `remove_module`, `add_module_at` — exposed, but success depends on model/slot compatibility and may still return `ok=false`

Interpretation:

- `packettracer_execute` can now invoke the full local experimental operation set documented here
- direct PT-side localhost bridge use is still useful for debugging raw payloads and bypassing MCP tool schema if you are iterating on PT-side behavior

---

## Reliability tiers

These tiers reflect observed behavior in this repo.

### Tier 1 - Preferred proof surfaces

Use these when you need reliable state verification.

- `list_devices`
- `list_components`
- `list_ports`
- `list_links`
- `get_device_detail`
- `read_interface_status`
- `read_port_power_state`

Why: these return structured JSON and are the safest basis for automation assertions.

### Tier 2 - Best-effort helper surfaces

Useful, but not the primary source of truth when structured proof is available.

- `run_device_cli`
- `run_ping`

Why: they can return highly useful output, especially for routers, but they are still closer to process/CLI behavior than pure state proof.

### Tier 3 - Diagnostic-only surfaces

- `probe_terminal_transcript`

Why: terminal and console-buffer behavior differs by device family.

Observed repo guidance:

- **PCs**: very useful, often gives real terminal output
- **Routers**: best-effort only; do not rely on it as the primary source of command output

---

## Available invoke actions

## 1) Session / bridge sanity

### `handshake`

Use when:

- checking that the local experimental bridge is reachable
- confirming the active ExApp instance identity

Good for:

- readiness checks
- bridge discovery validation

Avoid when:

- you need topology or device proof

### `echo`

Use when:

- checking payload transport end-to-end
- validating host-to-PT string roundtrip

Good for:

- smoke tests
- payload formatting checks

Avoid when:

- you need real Packet Tracer state

---

## 2) Read-only structured state

### `list_devices`

Use when:

- enumerating current topology nodes
- resolving runtime-created names like `Router0`, `PC2`, etc.

### `list_components`

Use when:

- discovering supported device models
- discovering valid connection types like `ETHERNET_STRAIGHT`, `ETHERNET_CROSS`, `AUTO`

Especially useful for:

- choosing valid `connectionType` values for `connect_devices`

### `list_ports`

Use when:

- discovering exact port names (`GigabitEthernet0/1`, `FastEthernet0`)

### `list_links`

Use when:

- verifying physical topology
- checking whether links were really created
- getting `linkIndex` values for `delete_link`

### `get_device_detail`

Use when:

- inspecting one device deeply
- checking neighbors and port linkage

### `read_interface_status`

Use when:

- checking whether an interface is linked / up / protocol-up
- verifying port light state after connections or power changes

### `read_port_power_state`

Use when:

- checking whether a port is powered on
- understanding why a linked interface is still down

---

## 3) Topology mutation

### `add_device`

Use when:

- creating routers, PCs, or other supported devices in the logical workspace

Typical use:

- build test topologies from scratch

Confirmed PT-side bridge payload format:

```text
local-experimental|add_device|<deviceType>|<model>|<x>|<y>
```

Example:

```text
local-experimental|add_device|ROUTER|1841|100|100
local-experimental|add_device|SWITCH|2960-24TT|320|240
local-experimental|add_device|PC|PC-PT|220|100
```

Important:

- the bridge returns the created runtime selector (for example `Router0`, `Switch1`, `PC0`)
- follow-up operations must use that returned selector, not the requested model string

### `remove_device`

Use when:

- cleaning stale or ghost devices
- rebuilding a broken topology side from scratch

### `connect_devices`

Use when:

- creating physical links between devices

Important:

- prefer valid connection types from `list_components`
- observed valid examples include:
  - `ETHERNET_STRAIGHT`
  - `ETHERNET_CROSS`
  - `AUTO`

Recommended follow-up:

- immediately verify with `list_links` and `read_interface_status`

Confirmed PT-side bridge payload format:

```text
local-experimental|connect_devices|<leftDeviceSelector>|<leftPortSelector>|<rightDeviceSelector>|<rightPortSelector>|<connectionType?>
```

Example:

```text
local-experimental|connect_devices|Router0|FastEthernet0/0|PC0|FastEthernet0|ETHERNET_STRAIGHT
```

Important:

- use the runtime-created device selectors returned by `add_device`
- use exact port names from `get_device_detail` or `list_ports`
- `list_links` is the stronger proof surface after creation; switch-local linked flags can lag

### `delete_link`

Use when:

- cleaning broken or duplicate links
- resetting a segment without removing devices

Recommended follow-up:

- verify with `list_links`

### Module operations (PT-side status)

PT-side local bridge support now exists for:

- `add_module`
- `remove_module`
- `get_device_module_layout`
- `add_module_at`

These are useful for:

- discovering module trees and slot structure
- inserting modules such as `HWIC-2T` or `NIM-2T`
- exposing serial or extra interface ports for follow-up topology experiments

However, treat them carefully in this guide:

- if host-side `mcp-server/src` has not yet been updated to accept these operations in `payload.experimental`, they are **not yet callable through the MCP server**
- in that case they are only usable through the PT-side local bridge for direct experimentation

Recommended workflow once host-side wiring is added:

1. `get_device_module_layout`
2. `add_module_at` (preferred over raw slot-string insertion)
3. `list_ports`
4. `connect_devices`
5. `list_links`

---

## 4) Configuration actions

### `set_interface_ip`

Use when:

- assigning IPv4 addresses and subnet masks to router or PC interfaces

Good for:

- fast bootstrap of lab addressing

Confirmed PT-side bridge payload format:

```text
local-experimental|set_interface_ip|<device>|<port>|<ip>|<mask>
```

Example:

```text
local-experimental|set_interface_ip|Router2|FastEthernet0/1|10.10.10.1|255.255.255.0
```

Observed behavior:

- PT-side bridge returns a structured mutation result with `appliedVia`
- more deterministic than relying only on multi-line CLI for interface addressing

### `set_port_power_state`

Use when:

- forcing used interfaces on after wiring
- recovering links that exist but stay `OFF_LIGHT` / `portUp=false`

Recommended follow-up:

- `read_interface_status`

### `set_default_gateway`

Use when:

- configuring PCs or hosts with a default gateway

Confirmed PT-side bridge payload format:

```text
local-experimental|set_default_gateway|<device>|<gateway>
```

### `add_static_route`

Use when:

- attempting a structured static-route apply

Observed repo guidance:

- treat this as a helper first
- if it reports failure from the routing-process path, prefer `run_device_cli` in router `global` mode for the route command

Confirmed PT-side bridge payload format:

```text
local-experimental|add_static_route|<device>|<network>|<mask>|<nextHop>|<port?>|<adminDistance?>
```

Observed PT-side behavior:

- operation is exposed on the PT-side bridge
- routing-process apply can still return `ok=false`; in that case router CLI remains the fallback path

---

## 5) Command and terminal actions

### `run_device_cli`

Best for:

- **router CLI output**
- one-off router commands whose output you want directly

Observed behavior:

- strongly reliable on routers
- excellent for commands like:
  - `show ip interface brief`
  - `show ip route`
  - `show interfaces`
- also useful in `global` mode for configuration commands like:
  - `ip route ...`

Use when:

- you want the result of **one router command**

PT-side bridge payload format:

```text
local-experimental|run_device_cli|<device>|<mode?>|<command>
```

For multi-commandline batches over the PT-side bridge, encode the command segment as:

```text
b64:<base64-of-utf8-command-with-embedded-newlines>
```

Example multi-command payload before base64 encoding:

```text
show ip interface brief
show ip route
```

or in global mode:

```text
interface FastEthernet0/1
ip address 10.10.10.1 255.255.255.0
no shutdown
end
show ip interface brief
```

Avoid when:

- you need terminal history/buffer behavior
- you want structured topology proof rather than command text
- you need submode-dependent router command chains that must remain in `config-router` / child config contexts across multiple lines

Observed PT-side behavior:

- multi-command CLI batches are accepted when sent through `b64:` encoding
- global-mode multiline execution can still return abbreviated success output, so follow with structured reads or a second show command
- per-command calls in `global` mode do not reliably preserve child config mode context; this can cause valid follow-up commands (for example `network ... area ...` after `router ospf 1`) to fail with `ERROR_INVALID`

### `probe_terminal_transcript`

Best for:

- **PC terminal output**
- terminal-like command capture where `consoleOutputDelta` / `rawOutput` matter

Observed behavior:

- works very well on PCs
- can return real transcript-like output for commands such as:
  - `ipconfig`
  - `arp -a`
  - `ping ...`
- only best-effort on routers

Use when:

- you want what the terminal appears to show
- especially on PCs
- you need a router submode workflow executed as one stateful sequence (for example `enable` -> `configure terminal` -> `router ospf 1` -> `network ... area ...` -> `end`)

Important fields it can expose:

- `response`
- `rawOutput`
- `consoleOutputSnapshot`
- `consoleOutputDelta`
- `prompt`
- `commandLog`

Interpretation:

- `consoleOutputSnapshot` can contain broad console history
- `consoleOutputDelta` is usually the best representation of the latest terminal output

### `run_ping`

Best for:

- quick ping-helper experiments
- lightweight process-style ping checks

Use when:

- you want a ping-specific helper surface

Avoid when:

- you need authoritative terminal output from a PC
- in that case, prefer `probe_terminal_transcript` with a normal `ping ...` command

---

## Recommended patterns

## Pattern A - Build topology safely

1. `add_device`
2. `list_devices`
3. `list_ports`
4. `connect_devices`
5. `list_links`
6. `set_port_power_state`
7. `read_interface_status`

## Pattern B - Configure router-to-router lab

1. `set_interface_ip`
2. `set_default_gateway` for PCs
3. `run_device_cli` in `global` mode for static routes if needed
4. `run_device_cli` in `user` mode for verification (`show ip route`, `show ip interface brief`)

## Pattern C - Get command output

- **Router** → `run_device_cli`
- **PC (default command execution)** → `run_device_cli`
- **PC (need transcript-rich console observation)** → `probe_terminal_transcript`

## Pattern D - Verify end-to-end reachability

1. `list_links`
2. `read_interface_status`
3. `run_device_cli` on routers (`show ip route` / `show ip interface brief`)
4. `probe_terminal_transcript` on a PC with `ping ...`

## Pattern E - Router submode workflow (OSPF-safe)

Use when router configuration requires parent + child mode continuity and single-command global calls are rejected.

1. `probe_terminal_transcript` with `command: b64:<multiline-cli>`
2. include full chain in one batch:
   - `enable`
   - `configure terminal`
   - `router ospf 1`
   - `network ... area ...`
   - `end`
3. verify with `run_device_cli`:
   - `show ip ospf interface <port>`
   - `show ip ospf neighbor`
   - `show ip route`

---

## Known practical caveats

- Router transcript probing is not the primary output contract.
- PC terminal observation is much stronger than router transcript observation.
- `connect_devices` requires a valid `connectionType`; if in doubt, inspect `list_components` first.
- A link can exist before it is operational; use `set_port_power_state` and `read_interface_status` to finish validation.
- When routing-process-based static-route helpers report failure, router CLI in `global` mode can still succeed.
- If multiple resident ExApp bridges exist, host-side discovery may attach to the wrong one; check `packettracer_status` bridge details.
- Module-backed ports can now be created PT-side, but large-topology reliability is still limited by `createLink(...)` once a device needs a second link.
- Indexed module insertion (`add_module_at`) is more reliable than string slot guessing (`add_module`) for models such as `2911` and `ISR4321`.
- The host-side MCP schema in `src/tools/packetTracerTools.ts` still advertises the experimental path as read-only. PT-side mutation/config support exists, but MCP callers cannot use it until that schema and related namespace metadata are extended.
- `list_links` is currently a stronger post-mutation proof surface than switch-local `get_device_detail` linked flags.
- `run_device_cli` multi-command support requires `b64:` encoding because the localhost bridge accepts a single TCP request line per operation.
- For router submode flows (for example OSPF process + network statements), prefer one `probe_terminal_transcript` `b64:` batch rather than `run_device_cli` per-command in `global` mode.

---

## Short rule-of-thumb summary

- **Need proof of topology state** → `list_*`, `get_device_detail`, `read_*`
- **Need router command output** → `run_device_cli`
- **Need PC command output** → `run_device_cli`
- **Need PC terminal transcript details** → `probe_terminal_transcript`
- **Need to build/change topology** → `add_device`, `connect_devices`, `delete_link`, `remove_device`
- **Need to fix links that exist but stay dark** → `set_port_power_state` + `read_interface_status`
