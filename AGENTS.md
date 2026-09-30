# AGENTS.md — `rsm-jetbrains`

Fourth and final RSM platform consumer. JetBrains IDE plugin bundling
a Node 22 sidecar that hosts `rsm-agent` (MCP stdio server) and speaks
to `rsm-host` → `rsm`. Consumer only.

Read `/work/AGENTS.md` first for workspace-wide rules. This file
refines them for this subtree; the closest `AGENTS.md` wins on conflict.

## Package facts

- Kotlin source under `src/main/kotlin/rsm/jetbrains/…`.
- Plugin id `rsm.jetbrains`, version `0.1.0`.
- Frozen test count: **16** JUnit 5 tests (Kotlin), executed via Gradle.
- Bundled Node: **v22.23.3**.
- Targets: `windows-x64`, `macos-x64`, `macos-arm64`, `linux-x64`.
- Packaging: IPGP `nativeVariants` — one ZIP per target.

## Frozen build inputs

Do NOT modify without explicit user authorization. All are recorded in
`FREEZE.md`:

| Input | Value |
|---|---|
| IntelliJ Platform Gradle Plugin (IPGP) | `2.19.0` |
| Gradle wrapper                          | `9.8.0` |
| Gradle wrapper JAR SHA-256              | `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5` (= Gradle 9.8.0 official `wrapperChecksum`) |
| IntelliJ Platform target                | `2026.1` |
| `sinceBuild`                            | `261` |
| Java toolchain (provisional)            | `jvmToolchain(17)` — final decision by Plugin Verifier |
| Bundled Node                            | `v22.23.3` |

The `jvmToolchain(17)` decision is provisional; the Plugin Verifier
either confirms it (`KEEP 17`), asks for `21`, or asks for removal.
Record the decision in `RSM_JETBRAINS_READINESS.md` before mutating
`build.gradle.kts`.

## Frozen runtime invariants

- **5-state vocabulary** in `RsmRuntimeService`: `OFF`, `READY`,
  `ACTIVE`, `UNAVAILABLE`, `ERROR`. Exactly these.
- **At-most-one automatic restart** per session; manual restart resets.
- **Shutdown ladder**: `SIGTERM` → 2 s grace → `SIGKILL`.
- **Backend-only**: `plugin.xml` declares only `<applicationService>` —
  no `<projectService>`, `<StatusBarWidget>`, `<ToolWindow>`, or
  `<PersistentStateComponent>`. This preserves Split Mode / Gateway
  correctness.
- **Bundled Node only**: `NodeRuntimeResolver` resolves to the ZIP's
  target binary. It MUST NOT honor `PATH`, `$NODE`, or any override.
- **MCP over stdio only**: no HTTP / SSE / WebSocket.
- **Cleared environment**: the child process starts with
  `ParentEnvironmentType.NONE` — no IDE env leaks.

## Verified commands (external runner required)

The Gradle-authoritative build requires an external runner (native FS,
≥ 4 GiB RAM, JDK 17+, network egress). From a properly provisioned
host:

```bash
cd rsm-jetbrains
./gradlew --version           # expect: Gradle 9.8.0
./gradlew javaToolchains
./gradlew tasks --all --console=plain > build/gradle-tasks-report.txt
# then read the report, and only then invoke the discovered task names:
./gradlew <actual-build-task>
./gradlew <actual-native-variant-tasks>
./gradlew <actual-test-task>
./gradlew <actual-plugin-verifier-task>
sha256sum build/distributions/*.zip
```

Do NOT invent task names. Discover them from `tasks --all`.

The scripted runner `scripts/run-external-verification.sh` automates
the sequence above. See `EXTERNAL_INTEGRATION_PLAN.md` and
`INTEGRATION_CHECKLIST.md` for details, and `E2E_TRACE_VERIFICATION.md`
for the canonical end-to-end-tracing correctness criterion (which
supersedes any PASS/FAIL gate matrix for integration correctness).

## Prohibited actions

- Rebuilding the wrapper JAR (`gradle wrapper --gradle-version …`).
  Use the committed wrapper verbatim.
- Substituting the four native ZIPs by hand to make a SHA match.
- Adding a fourth MCP tool call from the plugin — it invokes only the
  three frozen ones through `RsmRuntimeService`.
- Adding client-side extensions to `plugin.xml` — the plugin is
  backend-only.
- Adding an LLM SDK dependency, or a non-stdio transport.
- Modifying `rsm/`, `rsm-host/`, `rsm-agent/`, `rsm-cli/`, or
  `rsm-vscode/` in order to make the plugin build. Those trees are
  frozen relative to this work.
- Introducing `rsm-ide`, `rsm-jvm`, `IntegrationRegistry`, or
  `MarketplaceDiscovery`.
- Adding a Task Management panel, an orchestration engine, or any
  documentation system beyond the four MD files already present.

## Stop conditions

- Wrapper JAR SHA-256 differs from `238e777f…` — STOP; restore from
  git. Do NOT regenerate.
- Any of the four native ZIP SHA-256s differ from
  `build/release-manifest.json` without a documented reason — STOP and
  classify (`expected build variation` / `toolchain variation` /
  `frozen artifact drift` / `project defect` / `unauthorized change`).
- A layer under `src/main/kotlin/rsm/jetbrains/` needs a change that
  crosses a boundary into `rsm`, `rsm-host`, or `rsm-agent` — STOP,
  classify per `EXTERNAL_INTEGRATION_PLAN.md` §25, and get user
  authorization before proceeding.
- The Kotlin JUnit test count drops below 16 — STOP.

## Approval requirements

Explicit user authorization is required to change any of:

- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.properties` (Gradle version pin)
- `gradle/wrapper/gradle-wrapper.jar`
- `src/main/resources/META-INF/plugin.xml`
- The frozen native ZIPs under `build/distributions/`
- `build/release-manifest.json` or `build/node-runtimes/manifest.json`

The narrowly-scoped exception is the toolchain line in
`build.gradle.kts` when the Plugin Verifier's evidence justifies
changing it — record the verifier line in
`RSM_JETBRAINS_READINESS.md` **before** mutating.

## Format

Standard Markdown per [agents.md](https://agents.md/).
