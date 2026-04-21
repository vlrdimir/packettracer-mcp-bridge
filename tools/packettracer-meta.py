#!/usr/bin/env python3

from __future__ import annotations

import argparse
import os
import signal
import subprocess
import time
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path
from typing import IO, cast

DEFAULT_APPIMAGE_PATH = Path("/usr/lib/packettracer/packettracer.AppImage")


@dataclass(frozen=True)
class MetaResolution:
    meta_path: Path
    source: str


@dataclass(frozen=True)
class CliArgs:
    appimage: str
    meta_path: str


def parse_args() -> tuple[CliArgs, list[str]]:
    parser = argparse.ArgumentParser(
        description=(
            "Shell-safe wrapper for Cisco Packet Tracer's meta packager. "
            "It resolves the transient AppImage mount automatically and then "
            "forwards all remaining arguments to meta unchanged."
        )
    )
    _ = parser.add_argument(
        "--appimage",
        default=os.environ.get("PACKETTRACER_APPIMAGE", str(DEFAULT_APPIMAGE_PATH)),
        help="Path to the Packet Tracer AppImage used when a temporary mount is needed.",
    )
    _ = parser.add_argument(
        "--meta-path",
        default=os.environ.get("PACKETTRACER_META", ""),
        help="Optional direct path to the meta binary. Overrides auto-discovery.",
    )
    namespace, remainder = parser.parse_known_args()
    if not remainder:
        parser.error("pass the normal meta arguments after the wrapper options")

    appimage = cast(str, namespace.appimage)
    meta_path = cast(str, namespace.meta_path)
    return (
        CliArgs(appimage=appimage, meta_path=meta_path),
        remainder,
    )


def running_mount_candidates() -> Iterator[Path]:
    proc_root = Path("/proc")
    for proc_entry in proc_root.iterdir():
        if not proc_entry.name.isdigit():
            continue

        exe_link = proc_entry / "exe"
        try:
            resolved = exe_link.resolve(strict=True)
        except OSError:
            continue

        resolved_str = str(resolved)
        if "/tmp/.mount_packet" not in resolved_str:
            continue
        if "/opt/pt/bin/" not in resolved_str:
            continue

        parts = resolved.parts
        try:
            opt_index = parts.index("opt")
        except ValueError:
            continue

        yield Path(*parts[:opt_index])


def resolve_meta_from_running_mount() -> MetaResolution | None:
    seen: set[Path] = set()
    for mount_root in running_mount_candidates():
        if mount_root in seen:
            continue
        seen.add(mount_root)

        meta_path = mount_root / "opt/pt/bin/meta"
        if meta_path.is_file() and os.access(meta_path, os.X_OK):
            return MetaResolution(
                meta_path=meta_path,
                source=f"running Packet Tracer mount at {mount_root}",
            )
    return None


def resolve_explicit_meta(meta_path_raw: str) -> MetaResolution | None:
    if not meta_path_raw:
        return None

    meta_path = Path(meta_path_raw).expanduser().resolve()
    if not meta_path.is_file() or not os.access(meta_path, os.X_OK):
        raise FileNotFoundError(f"meta binary not executable: {meta_path}")

    return MetaResolution(
        meta_path=meta_path, source="explicit --meta-path / PACKETTRACER_META"
    )


def run_meta(meta_path: Path, forwarded_args: list[str]) -> int:
    process = subprocess.run([str(meta_path), *forwarded_args], check=False)
    return int(process.returncode)


def wait_for_mount_point(stdout: IO[str]) -> Path:
    deadline = time.monotonic() + 15.0
    while time.monotonic() < deadline:
        line = stdout.readline()
        if not line:
            time.sleep(0.05)
            continue

        mount_root = Path(line.strip())
        meta_path = mount_root / "opt/pt/bin/meta"
        if meta_path.is_file() and os.access(meta_path, os.X_OK):
            return mount_root

    raise RuntimeError("timed out waiting for AppImage mount path")


def terminate_mount(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return

    process.send_signal(signal.SIGTERM)
    try:
        _ = process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        _ = process.kill()
        _ = process.wait(timeout=5)


def run_with_temporary_mount(appimage_path: Path, forwarded_args: list[str]) -> int:
    if not appimage_path.is_file() or not os.access(appimage_path, os.X_OK):
        raise FileNotFoundError(
            f"Packet Tracer AppImage not executable: {appimage_path}"
        )

    mount_process = subprocess.Popen(
        [str(appimage_path), "--appimage-mount"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    try:
        if mount_process.stdout is None:
            raise RuntimeError("failed to capture AppImage mount output")

        mount_root = wait_for_mount_point(mount_process.stdout)
        meta_path = mount_root / "opt/pt/bin/meta"
        return run_meta(meta_path, forwarded_args)
    finally:
        terminate_mount(mount_process)


def main() -> int:
    namespace, forwarded_args = parse_args()

    explicit_meta = resolve_explicit_meta(namespace.meta_path)
    if explicit_meta is not None:
        return run_meta(explicit_meta.meta_path, forwarded_args)

    running_meta = resolve_meta_from_running_mount()
    if running_meta is not None:
        return run_meta(running_meta.meta_path, forwarded_args)

    appimage_path = Path(namespace.appimage).expanduser().resolve()
    return run_with_temporary_mount(appimage_path, forwarded_args)


if __name__ == "__main__":
    raise SystemExit(main())
