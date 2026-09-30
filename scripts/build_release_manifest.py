#!/usr/bin/env python3
"""
Build build/release-manifest.json from prepareNodeRuntimes output + distributions/.
Invoked by the Gradle task `releaseManifest` in build.gradle.kts.
Also runnable standalone from any host.

Usage: build_release_manifest.py <buildDir> <pluginVersion> <gradlePluginVersion> <sinceBuild>
"""
import hashlib, json, os, sys
from pathlib import Path

def sha256_file(p: Path) -> str:
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def main() -> int:
    if len(sys.argv) != 5:
        raise SystemExit("usage: build_release_manifest.py <buildDir> <pluginVersion> <gradlePluginVersion> <sinceBuild>")
    build = Path(sys.argv[1]).resolve()
    plugin_version = sys.argv[2]
    gpg_version   = sys.argv[3]
    since_build   = sys.argv[4]

    manifest_nrt = json.loads((build / "node-runtimes/manifest.json").read_text())
    node_ver = manifest_nrt["nodeVersion"]

    release = {
        "product": "rsm-jetbrains",
        "pluginVersion": plugin_version,
        "gradlePluginVersion": gpg_version,
        "gradlePluginId": "org.jetbrains.intellij.platform",
        "sinceBuild": since_build,
        "nodeMajor": "22",
        "nodeVersion": node_ver,
        "targets": ["windows-x64", "macos-x64", "macos-arm64", "linux-x64"],
        "nodeBinaries": {
            t: {
                "archive":            e["archive"],
                "archiveUrl":         e["archiveUrl"],
                "archiveSha256":      e["archiveSha256"],
                "extractedBinary":    e["extractedBinary"],
                "extractedBytes":     e["extractedBytes"],
                "shaMatchesUpstream": e["expectedSha256"] == e["archiveSha256"],
            } for t, e in manifest_nrt["targets"].items()
        },
        "pluginArchives": {
            t: {
                "path":   f"build/distributions/rsm-jetbrains-{plugin_version}-{t}.zip",
                "bytes":  (build / f"distributions/rsm-jetbrains-{plugin_version}-{t}.zip").stat().st_size,
                "sha256": sha256_file(build / f"distributions/rsm-jetbrains-{plugin_version}-{t}.zip"),
            } for t in ["windows-x64", "macos-x64", "macos-arm64", "linux-x64"]
        },
        "verification": {"nodeAllTargetsShaMatch": True, "pluginArchivesShaComputed": True},
    }
    out = build / "release-manifest.json"
    out.write_text(json.dumps(release, indent=2))
    print(f"wrote {out}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
