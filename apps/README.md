# Packet Tracer PT-side ExApp

This directory contains the PT-side Java ExApp used by the ExApp-only workflow in `packettracer-mcp-bridge/`.

Its job is simple:

- launch inside Packet Tracer
- bootstrap the Java IPC/PTMP side
- open a localhost bridge listener for the host-side MCP bridge
- expose readiness plus runtime operations used by `packettracer-mcp-bridge/mcp-server/`

## What gets generated

This package generates a **`.pta` Packet Tracer ExApp package**.

If you previously called it `.pts`, note that the artifact produced here is **`pt-exapp.pta`**, not a `.pts` file.

## Files you will use

- `build.sh` — compiles the Java sources and prepares `build/package/`
- `build.ps1` — PowerShell build equivalent for preparing `build/package/` on Windows
- `pt-exapp.py` — executable launched by Packet Tracer from the packaged app
- `PT_APP_META.xml` — Packet Tracer app metadata
- `build/package/pt-exapp.jar` — built Java app JAR
- `build/package/pt-exapp.pta` — final installable ExApp package after packaging

PT 8.2-specific release artifacts also exist:

- `PT_APP_META_82.xml`
- `release/pt82/`

Those PT 8.2 files are release/compatibility assets for manual or alternate packaging. They are **not** the default `.pta` build input used by `build.sh` / `build.ps1`.

## Prerequisites

You need:

- Packet Tracer 9.0.0 installed
- a Java Development Kit (JDK) that provides `java`, `javac`, and `jar`
- the Packet Tracer Java framework JAR, provided by one of these paths:
  - `PACKET_TRACER_JAVA_FRAMEWORK_JAR=/absolute/path/to/PacketTracerJavaFramework.jar`
  - `packettracer-mcp-bridge/apps/lib/PacketTracerJavaFramework.jar`
  - an active Packet Tracer AppImage mount under `/tmp/.mount_*/opt/pt/help/default/ipc/` (Linux fallback)

`pt-exapp.py` resolves the framework JAR using that order and then launches the main class with `java -cp ... packettracer.exapp.PacketTracerPtExApp`.

For Windows builds, the simplest setup is usually `PACKET_TRACER_JAVA_FRAMEWORK_JAR` or a staged `apps/lib/PacketTracerJavaFramework.jar`; the AppImage mount fallback is Linux-specific.

`pt-exapp.py` also accepts `PACKET_TRACER_PTEXAPP_JAR` when you want to point the launcher at a specific `pt-exapp.jar` instead of relying on the default packaged locations.

## Build the ExApp

Run from `packettracer-mcp-bridge/apps/`:

```bash
./build.sh
```

Windows PowerShell equivalent:

```powershell
.\build.ps1
```

If the framework JAR is not already staged under `apps/lib/`, use the explicit env var form:

```bash
PACKET_TRACER_JAVA_FRAMEWORK_JAR=/absolute/path/to/PacketTracerJavaFramework.jar ./build.sh
```

Successful output leaves:

- compiled classes under `build/classes/`
- package-ready files under `build/package/`

## Build the `.pta` package

After `./build.sh`, package the ExApp from `packettracer-mcp-bridge/apps/build/package/`:

```bash
cd build/package
python3 ../../../tools/packettracer-meta.py pt-exapp.pta PT_APP_META.xml -i pt-exapp.py
```

PowerShell equivalent:

```powershell
Set-Location build/package
python ..\..\..\tools\packettracer-meta.py pt-exapp.pta PT_APP_META.xml -i pt-exapp.py
```

`packettracer-meta.py` can auto-discover Packet Tracer's `meta` / `meta.exe`, or you can override it explicitly with `--meta-path` / `PACKETTRACER_META`. On Linux you can also override the AppImage source with `--appimage` / `PACKETTRACER_APPIMAGE`.

That produces:

```text
build/package/pt-exapp.pta
```

## Install / upload into Packet Tracer

This repo only documents the artifact and launch flow. The actual item to import/install into Packet Tracer is the generated **`.pta`** package.

Suggested operator flow:

1. Build `pt-exapp.pta`
2. Open Packet Tracer
3. In the top menu, open `Extensions > IPC > Configure Apps...`
4. In the **Apps List** dialog, click `Add`
5. Select the built package at `build/package/pt-exapp.pta`
6. Confirm the app appears in the **Apps List**
7. Select the app entry in that list
8. Click `Launch`

The important point is: the packaged app that Packet Tracer should receive is **`pt-exapp.pta`**.

If you want the README flow to match the Packet Tracer UI literally, the operator path is:

```text
Extensions > IPC > Configure Apps... > Add > choose pt-exapp.pta > select app > Launch
```

## After launch: confirm the bridge port from Packet Tracer logs

When the ExApp starts successfully, it opens a localhost bridge listener on:

- host: `127.0.0.1`
- port range: `39150-39159`

The exact chosen port is written by the app logger. Look in the Packet Tracer-side app log/output for a line like:

```text
Extensions > IPC > Log...
```

Then look for a line like:

```text
Local experimental host bridge bound at 127.0.0.1:3915X.
```

That log line comes from the PT-side bridge startup code and is the easiest way to see which port this launch picked.

You may also see a readiness detail like:

```text
Started local experimental host bridge at 127.0.0.1:3915X.
```

So the practical Packet Tracer operator flow becomes:

1. `Extensions > IPC > Configure Apps...`
2. `Add`
3. choose `pt-exapp.pta`
4. select the installed app
5. `Launch`
6. `Extensions > IPC > Log...`
7. copy the logged `127.0.0.1:3915X` port for MCP-side setup

## Match that logged port in the MCP server shell

The host-side bridge executable can scan the full `39150-39159` range automatically, but the cleanest setup is to pin it to the exact port shown in the Packet Tracer log.

Example from `packettracer-mcp-bridge/mcp-server/` after build:

```bash
export PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT=39150
export PACKET_TRACER_EXAPP_BRIDGE_COMMAND="$PWD/dist/packettracer-exapp-bridge.js"
export PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON='[]'
export PACKET_TRACER_EXAPP_BRIDGE_CWD="$PWD"
```

PowerShell equivalent:

```powershell
$env:PACKET_TRACER_LOCAL_EXPERIMENTAL_BRIDGE_PORT = '39150'
$env:PACKET_TRACER_EXAPP_BRIDGE_COMMAND = "$PWD/dist/packettracer-exapp-bridge.js"
$env:PACKET_TRACER_EXAPP_BRIDGE_ARGS_JSON = '[]'
$env:PACKET_TRACER_EXAPP_BRIDGE_CWD = "$PWD"
```

Replace `39150` with the exact port shown by the Packet Tracer log.

That makes the MCP side prefer the specific ExApp listener instead of scanning the whole range first.

## Relationship to the MCP server

Once the PT-side app is installed and launched:

- Packet Tracer hosts the ExApp
- the ExApp opens the localhost bridge
- `packettracer-mcp-server` uses `packettracer-exapp-bridge` to handshake with that listener
- MCP calls like `packettracer_status` and `packettracer_execute` then go through the ExApp bridge

See the host-side contract and server run steps in:

- `packettracer-mcp-bridge/mcp-server/README.md`

## Notes

- The package metadata currently points Packet Tracer at `pt-exapp.py` via `PT_APP_META.xml`.
- The current metadata uses `<LOADING>ON_DEMAND</LOADING>`, so the app must be launched from Packet Tracer before the MCP side can talk to it.
- `PT_APP_META_82.xml` and `release/pt82/` are PT 8.2 release/compatibility assets. Use them only when you intentionally need a PT 8.2-specific manual packaging path; they are not part of the default `build/package/pt-exapp.pta` workflow.
- Keep serial-capable router examples aligned with the catalog-backed `HWIC-2T` module shape when docs or samples refer to serial links.
