#!/usr/bin/env bash
#
# rsm-jetbrains 0.2.0 — external Gradle build script.
#
# Runs on any host with:
#   - JDK 17+
#   - Network access to plugins.gradle.org / download.jetbrains.com / nodejs.org
#   - >= 4 GiB free memory for the Gradle daemon (IPGP 2.19.0 requires this)
#
# Produces:
#   - build/distributions/rsm-jetbrains-0.2.0.zip                  (universal plugin)
#   - build/distributions/rsm-jetbrains-0.2.0-linux-x64.zip        (native variant)
#   - build/distributions/rsm-jetbrains-0.2.0-macos-x64.zip        (native variant)
#   - build/distributions/rsm-jetbrains-0.2.0-macos-arm64.zip      (native variant)
#   - build/distributions/rsm-jetbrains-0.2.0-windows-x64.zip      (native variant)
#
# All four native ZIPs are the deliverables users install from Settings → Plugins →
# gear → Install Plugin from Disk.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$HERE"

echo "▶ Verifying wrapper version"
./gradlew --version | grep -E "^Gradle" | grep -q "9.8.0" \
  || { echo "ERROR: expected Gradle 9.8.0"; exit 2; }

echo "▶ Cleaning previous build outputs"
./gradlew clean

echo "▶ Discovering actual Gradle task names for this build (no invented names)"
./gradlew tasks --all --console=plain > build/gradle-tasks-report.txt || true

echo "▶ Preparing bundled Node 22 runtimes (SHA-256 verified against nodejs.org)"
./gradlew prepareNodeRuntimes

echo "▶ Building the universal plugin JAR"
./gradlew buildPlugin

echo "▶ Building the four native variants"
./gradlew \
  buildPlugin-linux-x64 \
  buildPlugin-macos-x64 \
  buildPlugin-macos-arm64 \
  buildPlugin-windows-x64

echo "▶ Running the Kotlin/JUnit test suite (target: >= 54 tests green)"
./gradlew test

echo "▶ Running Plugin Verifier against IntelliJ Platform 2026.1+"
./gradlew verifyPlugin

echo "▶ SHA-256 of the four release artifacts"
sha256sum build/distributions/rsm-jetbrains-0.2.0-*.zip

echo "▶ DONE. Four native ZIPs are ready in build/distributions/"
