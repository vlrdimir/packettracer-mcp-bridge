# Local framework JAR staging

Place a Packet Tracer Java Framework JAR here only for local experiments when you want a stable repo-local path.

Expected default filename:

- `PacketTracerJavaFramework.jar`

The JAR is intentionally not committed by this repo. `build.sh` prefers the explicit `PACKET_TRACER_JAVA_FRAMEWORK_JAR` override, then this directory, and only then falls back to best-effort auto-detection under Packet Tracer's ephemeral `/tmp/.mount_*` AppImage mount.
