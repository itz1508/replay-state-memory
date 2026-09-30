#!/usr/bin/env python3
"""
prepareNodeRuntimes — download + SHA-256-verify Node 22.x for 4 targets.

Frozen behavior:
  - Query https://nodejs.org/download/release/index.json for latest v22.x.y at build start.
  - Download only from nodejs.org.
  - Fetch SHASUMS256.txt from nodejs.org for verification.
  - Verify SHA-256 for every archive.
  - Extract only the `node` (or `node.exe`) binary.
  - Write build/node-runtimes/manifest.json with per-target sha256+source+verification.
  - HARD STOP on any failure. No local-binary substitute.

Usage: prepare_node_runtimes.py <output_dir>
"""
import hashlib
import io
import json
import os
import shutil
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

TARGETS = [
    # (target-id, archive-name-template, node-binary-path-within-archive)
    ("windows-x64", "node-{v}-win-x64.zip",         "node-{v}-win-x64/node.exe"),
    ("macos-x64",   "node-{v}-darwin-x64.tar.gz",   "node-{v}-darwin-x64/bin/node"),
    ("macos-arm64", "node-{v}-darwin-arm64.tar.gz", "node-{v}-darwin-arm64/bin/node"),
    ("linux-x64",   "node-{v}-linux-x64.tar.xz",    "node-{v}-linux-x64/bin/node"),
]

INDEX_URL = "https://nodejs.org/download/release/index.json"


def http_get(url: str, binary: bool = False) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "rsm-jetbrains/0.1"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def resolve_latest_v22() -> str:
    data = json.loads(http_get(INDEX_URL))
    v22 = [e for e in data if e["version"].startswith("v22.")]
    if not v22:
        raise SystemExit("STOP: no Node 22.x release found on nodejs.org")
    v22.sort(key=lambda e: [int(x) for x in e["version"][1:].split(".")], reverse=True)
    return v22[0]["version"]


def load_shasums(version: str) -> dict[str, str]:
    text = http_get(f"https://nodejs.org/dist/{version}/SHASUMS256.txt").decode("utf-8")
    out = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        if len(parts) != 2:
            continue
        digest, name = parts
        out[name.lstrip("*")] = digest
    return out


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def extract_binary(archive_bytes: bytes, archive_name: str, member_path: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if archive_name.endswith(".zip"):
        with zipfile.ZipFile(io.BytesIO(archive_bytes)) as z:
            with z.open(member_path) as src, open(dest, "wb") as dst:
                shutil.copyfileobj(src, dst)
    elif archive_name.endswith(".tar.gz") or archive_name.endswith(".tar.xz"):
        mode = "r:gz" if archive_name.endswith(".tar.gz") else "r:xz"
        with tarfile.open(fileobj=io.BytesIO(archive_bytes), mode=mode) as t:
            m = t.getmember(member_path)
            f = t.extractfile(m)
            if f is None:
                raise SystemExit(f"STOP: cannot extract {member_path} from {archive_name}")
            with open(dest, "wb") as dst:
                shutil.copyfileobj(f, dst)
    else:
        raise SystemExit(f"STOP: unknown archive format for {archive_name}")
    # POSIX exec bit on non-Windows binaries
    if not dest.name.endswith(".exe"):
        os.chmod(dest, 0o755)


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: prepare_node_runtimes.py <output_dir>")
    out_root = Path(sys.argv[1]).resolve()
    out_root.mkdir(parents=True, exist_ok=True)

    print(f"[prepareNodeRuntimes] resolving latest Node 22.x from {INDEX_URL}")
    version = resolve_latest_v22()
    print(f"[prepareNodeRuntimes] Node version: {version}")

    print(f"[prepareNodeRuntimes] loading SHASUMS256.txt for {version}")
    shasums = load_shasums(version)

    manifest = {
        "nodeVersion": version,
        "nodeMajor": "22",
        "source":  f"https://nodejs.org/dist/{version}/",
        "shasumsSource": f"https://nodejs.org/dist/{version}/SHASUMS256.txt",
        "targets": {},
    }

    for target_id, archive_tmpl, member_tmpl in TARGETS:
        archive_name = archive_tmpl.format(v=version)
        member_path  = member_tmpl.format(v=version)
        expected     = shasums.get(archive_name)
        if not expected:
            raise SystemExit(f"STOP: no SHA-256 for {archive_name} in SHASUMS256.txt")

        print(f"[prepareNodeRuntimes] {target_id}: downloading {archive_name}")
        url = f"https://nodejs.org/dist/{version}/{archive_name}"
        blob = http_get(url, binary=True)
        actual = sha256(blob)
        if actual != expected:
            raise SystemExit(
                f"STOP: SHA-256 mismatch for {archive_name}\n"
                f"  expected: {expected}\n"
                f"  actual:   {actual}"
            )
        print(f"[prepareNodeRuntimes] {target_id}: SHA-256 OK ({actual})")

        binary_name = "node.exe" if target_id == "windows-x64" else "node"
        dest = out_root / target_id / binary_name
        extract_binary(blob, archive_name, member_path, dest)
        print(f"[prepareNodeRuntimes] {target_id}: extracted -> {dest} ({dest.stat().st_size} bytes)")

        manifest["targets"][target_id] = {
            "archive":            archive_name,
            "archiveUrl":         url,
            "archiveSha256":      actual,
            "expectedSha256":     expected,
            "verified":           True,
            "extractedBinary":    str(dest.relative_to(out_root)),
            "extractedBytes":     dest.stat().st_size,
        }

    manifest_path = out_root / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2))
    print(f"[prepareNodeRuntimes] manifest written: {manifest_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
