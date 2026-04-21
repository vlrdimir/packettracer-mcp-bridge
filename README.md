# Packet Tracer MCP Bridge

This workspace contains the ExApp-only Packet Tracer bridge:

- a host-side stdio MCP server
- a PT-side Java ExApp that runs inside Packet Tracer

Together they let MCP clients launch Packet Tracer, inspect bridge readiness, and execute bridge-backed operations through the PT-side app.

## Demo

Demo preview (click to open full video):

<div align="center">
  <a href="https://cdn.dontdemoit.com/export-1776745274354.mp4">
    <img src="./assets/demo-full.gif" alt="Packet Tracer MCP Bridge demo" style="width: 100%; max-width: 960px; height: auto;" />
  </a>
</div>

This is the best place to put the video because it is the landing page for the whole bridge workspace.

## Tested platform

Current end-to-end testing for this workspace has been done on **Arch Linux**. Windows host-runtime support is now available as an MVP path, but still needs broader validation.

The local Packet Tracer base install used during testing follows this project and article lineage:

- reference article: [Cisco Packet Tracer 9.0.0 Installation on All Linux Distributions](https://fr0stb1rd.gitlab.io/posts/cisco-packet-tracer-9-0-0-installation-on-any-linux/)

In practice, this workspace currently assumes a Linux-style Packet Tracer install because the host runtime and packaging helpers are documented against paths such as:

- `/usr/bin/packettracer`
- `/usr/bin/packettracer-reset-login-state`
- `/usr/lib/packettracer/packettracer.AppImage`

Other Linux setups may work, but they should be treated as **not yet validated** unless you verify the same runtime paths or adapt the local wrappers.

Windows now has a documented MVP runtime path for `mcp-server` (launcher-based flow plus PowerShell environment setup), but ExApp packaging/build docs remain Linux-first.

## Layout

- `packettracer-mcp-bridge/mcp-server/` - stdio MCP server, MCP contract, and ExApp bridge launcher
- `packettracer-mcp-bridge/apps/` - PT-side Java ExApp, `.pta` packaging flow, and Packet Tracer-side bridge listener
- `packettracer-mcp-bridge/tools/packettracer-meta.py` - shell-safe wrapper around Cisco Packet Tracer's `meta` packager

## Quick start

### 1. Build the PT-side ExApp package

From `packettracer-mcp-bridge/apps/`:

```bash
./build.sh
cd build/package
python3 ../../../tools/packettracer-meta.py pt-exapp.pta PT_APP_META.xml -i pt-exapp.py
```

PowerShell build equivalent:

```powershell
.\build.ps1
Set-Location build/package
python ..\..\..\tools\packettracer-meta.py pt-exapp.pta PT_APP_META.xml -i pt-exapp.py
```

This produces the Packet Tracer app package:

```text
packettracer-mcp-bridge/apps/build/package/pt-exapp.pta
```

### 2. Install and launch the ExApp in Packet Tracer

- import/install `pt-exapp.pta` into Packet Tracer
- launch the app from inside Packet Tracer
- watch the Packet Tracer-side app log for a line like:

```text
Local experimental host bridge bound at 127.0.0.1:3915X.
```

That line tells you the exact bridge port used by this launch.

### 3. Start the MCP server against that port

From `packettracer-mcp-bridge/mcp-server/`:

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
npm install
npm run build
$env:PACKET_TRACER_EXAPP_BRIDGE_COMMAND = "$PWD/dist/packettracer-exapp-bridge.js"
$env:PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON = '[]'
$env:PACKET_TRACER_EXAPP_BRIDGE_CWD = "$PWD"
$env:PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT = '39150'
npm start
```

Replace `39150` with the exact port shown by the Packet Tracer log.

### 4. Confirm readiness from the MCP side

Use `packettracer_status` first. It is the safest first check because it reports:

- host runtime state
- ExApp bridge readiness
- bridge discovery details

On Windows hosts, you can also run the focused smoke check from `packettracer-mcp-bridge/mcp-server/`:

```powershell
npm run smoke:windows
```

## Runtime prerequisites

This repo does not vendor Cisco Packet Tracer binaries. The main external prerequisites are:

- `/usr/lib/packettracer/packettracer.AppImage` or a running Packet Tracer AppImage mount
- a Packet Tracer Java framework JAR, provided via:
  - `PACKET_TRACER_JAVA_FRAMEWORK_JAR`, or
  - `packettracer-mcp-bridge/apps/lib/PacketTracerJavaFramework.jar`, or
- an active Packet Tracer mount under `/tmp/.mount_*/opt/pt/help/default/ipc/`

If you are setting this up outside Arch Linux, read the tested-platform note above first and expect to adapt the runtime path assumptions.

## Notes

- `packettracer-mcp-bridge/apps/PT_APP_META.xml` intentionally keeps its current `<ID>` / `<KEY>` values for compatibility until full `.pta` install/load validation is complete.
- `packettracer-mcp-bridge/mcp-server/` and `packettracer-mcp-bridge/apps/` can be built independently, but the repo is organized as one bridge workspace.
- `packettracer-mcp-bridge/apps/README.md` should stay focused on install/package/launch inside Packet Tracer.
- `packettracer-mcp-bridge/mcp-server/README.md` should stay focused on the MCP contract and server runtime.
