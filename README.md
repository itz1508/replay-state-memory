# Replay State Memory — JetBrains Plugin

Backend-only JetBrains IDE plugin that bundles a Node 22 sidecar hosting `rsm-agent`
(MCP server over stdio). Version **0.2.0**.

## What this repo contains

- `src/main/kotlin/...` — production Kotlin sources
- `src/main/resources/META-INF/plugin.xml` — plugin descriptor
- `src/test/kotlin/...` — 54 JUnit tests
- `build.gradle.kts` + `gradle/libs.versions.toml` — Gradle build definition
- `scripts/` — build helpers (`prepare_node_runtimes.py`, `build_release_manifest.py`)
- `.github/workflows/build.yml` — CI: builds 4 native plugin ZIPs on every push
- `PLANS/`, `docs/`, `AGENTS.md`, `E2E_TRACE_VERIFICATION.md`, `FREEZE.md`,
  `RSM_JETBRAINS_0_2_0_DELIVERY.md` — design docs and readiness reports

## How to get the installable plugin

1. Go to the **Actions** tab in this repo.
2. Click the latest **build** workflow run.
3. Scroll to **Artifacts** at the bottom.
4. Download `rsm-jetbrains-native-variants` (a ZIP that contains the four
   OS/arch variants).
5. Inside, pick the one matching your machine:
   - Windows x64 → `rsm-jetbrains-0.2.0-windows-x64.zip`
   - macOS Intel → `rsm-jetbrains-0.2.0-macos-x64.zip`
   - macOS Apple Silicon → `rsm-jetbrains-0.2.0-macos-arm64.zip`
   - Linux x64 → `rsm-jetbrains-0.2.0-linux-x64.zip`

## How to install into IntelliJ IDEA

1. Open **IntelliJ IDEA 2026.1** or later.
2. **Settings → Plugins → gear ⚙ → Install Plugin from Disk...**
3. Select the plugin ZIP for your platform.
4. Restart the IDE.

You should see:

- The **RSM** icon on the **right rail** (View → Tool Windows → RSM).
- Five actions in **Find Action** (Ctrl/Cmd+Shift+A):
  - RSM: Show Console
  - RSM: Show Runtime Status
  - RSM: Restart Runtime
  - RSM: Register Source…
  - RSM: Replay Source…
- **Settings → Tools → RSM** — a settings page for the plugin.

## Frozen contracts

- Transport: **MCP over stdio only**. No HTTP/SSE/WebSocket.
- Execution model: **backend-only** (`applicationService`; safe in Split Mode / Gateway).
- MCP tools: exactly `rsm_register`, `rsm_replay`, `rsm_remove`.
- HostError kinds: exactly `PERMISSION_DENIED`, `RESOLVER_STORE_MISS`.
- Runtime states: `OFF`, `READY`, `ACTIVE`, `UNAVAILABLE`, `ERROR` (5-state, no sixth).
- Bundled Node: `v22.23.3`, one binary per target OS/arch.
- IntelliJ target: **2026.1** (`sinceBuild=261`), Gradle **9.8.0**, IPGP **2.19.0**, `jvmToolchain(17)`.

## Local build (optional)

If you'd rather build locally than use GitHub Actions:

```bash
# Requires JDK 17+ and ≥ 4 GiB RAM
./gradlew clean prepareNodeRuntimes buildPlugin
./gradlew buildPlugin-linux-x64 buildPlugin-macos-x64 buildPlugin-macos-arm64 buildPlugin-windows-x64
./gradlew test verifyPlugin
```

Output: `build/distributions/rsm-jetbrains-0.2.0-*.zip`.

## Testing

54 JUnit tests, all green. See `src/test/kotlin/rsm/jetbrains/*.kt`:

```
[  19 containers found      ]
[  54 tests found           ]
[  54 tests successful      ]
[   0 tests failed          ]
```

## Documentation

- **`PLANS/RSM_JETBRAINS_0_2_0_PLAN.md`** — approved 0.2.0 build plan, 13 evidence-anchored design claims
- **`RSM_JETBRAINS_0_2_0_DELIVERY.md`** — delivery report
- **`E2E_TRACE_VERIFICATION.md`** — the correctness criterion (correlation-id tracing, 8-hop path)
- **`FREEZE.md`** — build-parameter freeze
- **`AGENTS.md`** — subtree-scoped constraints (see `AGENTS-*.md` in `docs/`)

## License

See `LICENSE`.
