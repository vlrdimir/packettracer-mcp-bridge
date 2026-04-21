#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SRC_DIR="$SCRIPT_DIR/src"
BUILD_DIR="$SCRIPT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
PACKAGE_DIR="$BUILD_DIR/package"
PACKAGE_JAR="$PACKAGE_DIR/pt-exapp.jar"
LAUNCHER_SCRIPT="$SCRIPT_DIR/pt-exapp.py"
PACKAGE_LAUNCHER="$PACKAGE_DIR/pt-exapp.py"
PACKAGE_META="$PACKAGE_DIR/PT_APP_META.xml"
ROOT_META="$SCRIPT_DIR/PT_APP_META.xml"
DEFAULT_FRAMEWORK_JAR="$SCRIPT_DIR/lib/PacketTracerJavaFramework.jar"
FRAMEWORK_JAR="${PACKET_TRACER_JAVA_FRAMEWORK_JAR:-}"
FRAMEWORK_SOURCE=""
MAIN_CLASS="packettracer.exapp.PacketTracerPtExApp"

detect_mounted_framework_jar() {
  for candidate in /tmp/.mount_*/opt/pt/help/default/ipc/pt-cep-java-framework-*.jar; do
    if [ -f "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

JAVA_SOURCES=$(find "$SRC_DIR" -type f -name '*.java')

if [ -z "$JAVA_SOURCES" ]; then
  printf '%s\n' "No Java sources found under $SRC_DIR" >&2
  exit 1
fi

mkdir -p "$CLASSES_DIR"
mkdir -p "$PACKAGE_DIR"
rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"
rm -f "$PACKAGE_DIR/pt-exapp.py"
rm -f "$PACKAGE_DIR/packettracer-pt-exapp.py"
rm -f "$PACKAGE_DIR/packettracer-pt-exapp-java.py"
rm -f "$PACKAGE_DIR/packettracer-pt-exapp-java.sh"

if ! command -v javac >/dev/null 2>&1; then
  printf '%s\n' "javac is required to build this skeleton. Install a JDK or provide a toolchain that includes javac." >&2
  exit 1
fi

if ! command -v jar >/dev/null 2>&1; then
  printf '%s\n' "jar is required to assemble the Java PT-side package artifact. Install a JDK or provide a toolchain that includes jar." >&2
  exit 1
fi

if [ ! -f "$ROOT_META" ]; then
  printf '%s\n' "Missing PT_APP_META.xml: $ROOT_META" >&2
  exit 1
fi

if [ ! -f "$LAUNCHER_SCRIPT" ]; then
  printf '%s\n' "Missing launcher script: $LAUNCHER_SCRIPT" >&2
  exit 1
fi

if [ -n "$FRAMEWORK_JAR" ]; then
  if [ ! -f "$FRAMEWORK_JAR" ]; then
    printf '%s\n' "Configured PACKET_TRACER_JAVA_FRAMEWORK_JAR does not exist: $FRAMEWORK_JAR" >&2
    exit 1
  fi

  FRAMEWORK_SOURCE="PACKET_TRACER_JAVA_FRAMEWORK_JAR"
elif [ -f "$DEFAULT_FRAMEWORK_JAR" ]; then
  FRAMEWORK_JAR="$DEFAULT_FRAMEWORK_JAR"
  FRAMEWORK_SOURCE="lib/PacketTracerJavaFramework.jar"
else
  DETECTED_FRAMEWORK_JAR=$(detect_mounted_framework_jar || true)

  if [ -n "$DETECTED_FRAMEWORK_JAR" ]; then
    FRAMEWORK_JAR="$DETECTED_FRAMEWORK_JAR"
    FRAMEWORK_SOURCE="auto-detected Packet Tracer mount"
  fi
fi

if [ -z "$FRAMEWORK_JAR" ]; then
  printf '%s\n' "Packet Tracer Java Framework JAR is required for this bootstrap stage. Set PACKET_TRACER_JAVA_FRAMEWORK_JAR, stage lib/PacketTracerJavaFramework.jar, or keep Packet Tracer running so the mounted JAR can be auto-detected." >&2
  exit 1
fi

printf '%s\n' "Compiling with Packet Tracer Java Framework JAR ($FRAMEWORK_SOURCE): $FRAMEWORK_JAR"
javac -cp "$FRAMEWORK_JAR" -d "$CLASSES_DIR" $JAVA_SOURCES

jar --create --file "$PACKAGE_JAR" --main-class "$MAIN_CLASS" -C "$CLASSES_DIR" .
cp "$ROOT_META" "$PACKAGE_META"
cp "$LAUNCHER_SCRIPT" "$PACKAGE_LAUNCHER"
chmod 755 "$PACKAGE_LAUNCHER"

printf '%s\n' "Compiled classes written to $CLASSES_DIR"
printf '%s\n' "Packageable PT_APP_META copied to $PACKAGE_META"
printf '%s\n' "Packageable application JAR written to $PACKAGE_JAR"
printf '%s\n' "Packageable launcher written to $PACKAGE_LAUNCHER"
