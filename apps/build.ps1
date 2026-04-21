$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcDir = Join-Path $scriptDir "src"
$buildDir = Join-Path $scriptDir "build"
$classesDir = Join-Path $buildDir "classes"
$packageDir = Join-Path $buildDir "package"
$packageJar = Join-Path $packageDir "pt-exapp.jar"
$launcherScript = Join-Path $scriptDir "pt-exapp.py"
$packageLauncher = Join-Path $packageDir "pt-exapp.py"
$packageMeta = Join-Path $packageDir "PT_APP_META.xml"
$rootMeta = Join-Path $scriptDir "PT_APP_META.xml"
$defaultFrameworkJar = Join-Path $scriptDir "lib\PacketTracerJavaFramework.jar"
$mainClass = "packettracer.exapp.PacketTracerPtExApp"

$javaSources = Get-ChildItem -Path $srcDir -Recurse -Filter *.java -File | Select-Object -ExpandProperty FullName
if (-not $javaSources -or $javaSources.Count -eq 0) {
  throw "No Java sources found under $srcDir"
}

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
  throw "javac is required to build this ExApp package. Install a JDK that provides javac."
}

if (-not (Get-Command jar -ErrorAction SilentlyContinue)) {
  throw "jar is required to assemble the Java PT-side package artifact. Install a JDK that provides jar."
}

if (-not (Test-Path -LiteralPath $rootMeta -PathType Leaf)) {
  throw "Missing PT_APP_META.xml: $rootMeta"
}

if (-not (Test-Path -LiteralPath $launcherScript -PathType Leaf)) {
  throw "Missing launcher script: $launcherScript"
}

$frameworkJar = $env:PACKET_TRACER_JAVA_FRAMEWORK_JAR
$frameworkSource = ""
if ($frameworkJar) {
  if (-not (Test-Path -LiteralPath $frameworkJar -PathType Leaf)) {
    throw "Configured PACKET_TRACER_JAVA_FRAMEWORK_JAR does not exist: $frameworkJar"
  }
  $frameworkSource = "PACKET_TRACER_JAVA_FRAMEWORK_JAR"
} elseif (Test-Path -LiteralPath $defaultFrameworkJar -PathType Leaf) {
  $frameworkJar = $defaultFrameworkJar
  $frameworkSource = "lib/PacketTracerJavaFramework.jar"
} else {
  throw "Packet Tracer Java Framework JAR is required. Set PACKET_TRACER_JAVA_FRAMEWORK_JAR or stage apps/lib/PacketTracerJavaFramework.jar."
}

New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
if (Test-Path -LiteralPath $classesDir) {
  Remove-Item -LiteralPath $classesDir -Recurse -Force
}
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
New-Item -ItemType Directory -Path $packageDir -Force | Out-Null

foreach ($staleFile in @(
  "pt-exapp.py",
  "packettracer-pt-exapp.py",
  "packettracer-pt-exapp-java.py",
  "packettracer-pt-exapp-java.sh"
)) {
  $stalePath = Join-Path $packageDir $staleFile
  if (Test-Path -LiteralPath $stalePath) {
    Remove-Item -LiteralPath $stalePath -Force
  }
}

Write-Host "Compiling with Packet Tracer Java Framework JAR ($frameworkSource): $frameworkJar"
& javac -cp $frameworkJar -d $classesDir $javaSources
if (-not $?) {
  throw "javac compilation failed"
}

& jar --create --file $packageJar --main-class $mainClass -C $classesDir .
if (-not $?) {
  throw "jar packaging failed"
}

Copy-Item -LiteralPath $rootMeta -Destination $packageMeta -Force
Copy-Item -LiteralPath $launcherScript -Destination $packageLauncher -Force

Write-Host "Compiled classes written to $classesDir"
Write-Host "Packageable PT_APP_META copied to $packageMeta"
Write-Host "Packageable application JAR written to $packageJar"
Write-Host "Packageable launcher written to $packageLauncher"
