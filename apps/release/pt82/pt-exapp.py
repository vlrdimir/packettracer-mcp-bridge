#!/usr/bin/env python3

from __future__ import annotations

import os
import sys
from pathlib import Path

MAIN_CLASS = "packettracer.exapp.PacketTracerPtExApp"


def detect_mounted_framework_jar() -> Path | None:
    for candidate in sorted(
        Path("/tmp").glob(
            ".mount_*/opt/pt/help/default/ipc/pt-cep-java-framework-*.jar"
        )
    ):
        if candidate.is_file():
            return candidate

    return None


def resolve_app_jar(script_dir: Path) -> Path:
    configured = os.environ.get("PACKET_TRACER_PTEXAPP_JAR", "").strip()
    if configured:
        app_jar = Path(configured).expanduser().resolve()
        if app_jar.is_file():
            return app_jar
        raise FileNotFoundError(
            f"Configured PACKET_TRACER_PTEXAPP_JAR does not exist: {app_jar}"
        )

    candidates = [
        script_dir / "pt-exapp.jar",
        script_dir / "build" / "package" / "pt-exapp.jar",
    ]

    for candidate in candidates:
        if candidate.is_file():
            return candidate

    raise FileNotFoundError(
        "Java PT-side application JAR not found. Build first with ./build.sh or set PACKET_TRACER_PTEXAPP_JAR."
    )


def resolve_framework_jar(script_dir: Path) -> Path:
    configured = os.environ.get("PACKET_TRACER_JAVA_FRAMEWORK_JAR", "").strip()
    if configured:
        framework_jar = Path(configured).expanduser().resolve()
        if framework_jar.is_file():
            return framework_jar
        raise FileNotFoundError(
            f"Configured PACKET_TRACER_JAVA_FRAMEWORK_JAR does not exist: {framework_jar}"
        )

    default_framework = script_dir / "lib" / "PacketTracerJavaFramework.jar"
    if default_framework.is_file():
        return default_framework

    mounted_framework = detect_mounted_framework_jar()
    if mounted_framework is not None:
        return mounted_framework

    raise FileNotFoundError(
        "Packet Tracer Java Framework JAR not found. Set PACKET_TRACER_JAVA_FRAMEWORK_JAR, stage lib/PacketTracerJavaFramework.jar, or launch from a running Packet Tracer AppImage session."
    )


def main() -> int:
    script_dir = Path(__file__).resolve().parent
    app_jar = resolve_app_jar(script_dir)
    framework_jar = resolve_framework_jar(script_dir)
    classpath = os.pathsep.join((str(app_jar), str(framework_jar)))
    os.execvp(
        "java",
        ["java", "-cp", classpath, MAIN_CLASS, *sys.argv[1:]],
    )


if __name__ == "__main__":
    raise SystemExit(main())
