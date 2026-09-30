# RSM + JetBrains — External Integration Plan (End‑to‑End)

**Repository:** `rsm-jetbrains/` (this directory)
**Frozen deliverable state:** `PARTIALLY READY — FINAL VERIFICATION PENDING`
**Target terminal state:** `RSM + JetBrains Integration COMPLETE`
**Authoritative anchors:** see `FREEZE.md` and `RSM_JETBRAINS_READINESS.md`
**Scope:** everything the external Gradle runner and the developer workstation must do to move the deliverable from the current in‑sandbox state to `Integration COMPLETE`. Nothing here is theoretical — every step names its exact command, its exact expected output, and the exact way to classify a failure.

---

## 0. How to read this document

- **Every step has a `PASS` condition, a `Record` block, and a `Fail →` action.**
- **No task names are invented.** Wherever a Gradle task is referenced as `<actual-*-task>` the runner must first discover it (Step 5).
- **No artifact substitution.** The four ZIPs in `build/distributions/` are pre‑build evidence; they are **not** authoritative Gradle output until Step 6 regenerates them under the wrapper.
- **No RSM source modification.** `rsm/`, `rsm-host/`, `rsm-agent/`, `rsm-cli/`, `rsm-vscode/` are frozen. If an integration failure appears, classify it (§14) before proposing any change.
- **Preserve the freeze.** All values in §1 are input to the run, not outputs of the run.

---

## 1. Frozen inputs (do not modify)

| Input | Value | Source |
|---|---|---|
| Plugin id | `rsm.jetbrains` | `src/main/resources/META-INF/plugin.xml` |
| Plugin version | `0.1.0` | `build.gradle.kts` |
| IntelliJ Platform Gradle Plugin (IPGP) | `2.19.0` | `gradle/libs.versions.toml` |
| Gradle wrapper | `9.8.0` | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle wrapper JAR SHA‑256 | `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5` | Gradle 9.8.0 official `wrapperChecksum` |
| IntelliJ Platform target | `2026.1` | `build.gradle.kts` |
| `sinceBuild` | `261` | `plugin.xml` |
| Java toolchain (provisional) | `jvmToolchain(17)` | `build.gradle.kts` line 21 |
| Bundled Node | `v22.23.3` | `build/node-runtimes/manifest.json` |
| Targets | `windows-x64`, `macos-x64`, `macos-arm64`, `linux-x64` | `build.gradle.kts` `nativeVariants` |
| Transport | MCP over stdio | Frozen — no HTTP / SSE / WebSocket |
| Execution mode | Backend‑only (`applicationService`) | `plugin.xml` |
| Baseline tests | `132/132` upstream + `16/16` `rsm-jetbrains` | `RSM_JETBRAINS_READINESS.md` §30 |

The `jvmToolchain(17)` decision is provisional. Step 9 (Plugin Verifier) produces the evidence that resolves it into one of:

- **KEEP `jvmToolchain(17)`** — verifier passes.
- **CHANGE to `jvmToolchain(21)`** — verifier flags JVM incompatibility against the IntelliJ Platform 2026.1 JBR 21 baseline.
- **REMOVE** — let IPGP auto‑provision from the platform target.

Do not touch the toolchain declaration before Step 9. This is the single explicit exception to “no source changes” — and only with verifier evidence recorded in `RSM_JETBRAINS_READINESS.md` before mutation.

---

## 2. Runner environment prerequisites

The external Gradle runner (Steps 3–12) **must** provide all of:

1. Native filesystem (not blobfuse2 / FUSE / overlay — IPGP’s transforms fail on non‑native FS).
2. ≥ 4 GiB memory available to the Gradle daemon (IPGP 2.19.0 `InstrumentationAnalysisTransform` recommends `-Xmx4G`).
3. JDK 17+ on `PATH` and `JAVA_HOME`.
4. Network egress to:
   - `https://services.gradle.org`
   - `https://plugins.gradle.org`
   - `https://download.jetbrains.com`
   - `https://plugins.jetbrains.com`
   - `https://repo1.maven.org`
   - `https://nodejs.org`
5. The committed Gradle wrapper. **Never** run `gradle wrapper --gradle-version …`; use `./gradlew` verbatim.
6. `git`, `unzip`, `sha256sum` (or `shasum -a 256` on macOS), `curl`, `bash`.

Verification (must PASS before any Gradle command):

```bash
cd rsm-jetbrains
java -version                     # 17 or higher
uname -m                          # x86_64 or arm64
free -g || vm_stat                # confirm ≥ 4 GiB free
sha256sum gradle/wrapper/gradle-wrapper.jar
# expected: 238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5
```

**Record:** paste the four command outputs into `build/verification-logs/00-runner-env.log`.

**Fail →** if any check fails, stop. Repair the runner; do NOT edit the repository.

---

## 3. Verify wrapper (§24 Gates 1, 2, 15)

```bash
./gradlew --version
```

**Expected substring:**
```
Gradle 9.8.0
```
plus JVM 17+ and OS info.

**Record:** `build/verification-logs/01-gradlew-version.log`.

**PASS** when `Gradle 9.8.0` is on the first non‑blank line of the banner.

**Fail →** if the wrapper self‑bootstraps a different Gradle version, the runner tampered with `gradle-wrapper.properties`; restore from git and retry. If the JAR SHA‑256 differs from `238e777f…`, restore the wrapper from git. Do not regenerate it.

---

## 4. Resolve toolchain (§24 Gate 3)

```bash
./gradlew javaToolchains
```

**Expected:** at least one resolvable JDK entry printed. Note which toolchain Gradle would auto‑provision for language version `17` (the `jvmToolchain(17)` declaration in `build.gradle.kts`).

**Record:** `build/verification-logs/02-javaToolchains.log`.

**PASS** when the command exits 0 and reports at least one usable JDK. Do NOT edit the toolchain here — the decision is deferred to Step 9.

**Fail →** if the daemon disappears with an OOM error, §2.2 is not met — increase memory and retry. If no JDK is found and network is blocked, §2.4 is not met.

---

## 5. Discover actual Gradle tasks (§24 Gate 4)

```bash
mkdir -p build
./gradlew tasks --all --console=plain > build/gradle-tasks-report.txt
```

The report is now the authoritative source for the following names (search verbatim):

- **Build task** — the assembly task in the `intellij platform` group (typically `buildPlugin`, but confirm from the report).
- **Native variant tasks** — the four per‑target packaging tasks emitted by IPGP’s `nativeVariants` DSL.
- **Test task** — the JVM test task (typically `test`).
- **Plugin Verifier task** — the verifier entry point (typically `verifyPlugin` or `runPluginVerifier`; the report is definitive).

**Record:** copy the exact task names into `build/verification-logs/03-discovered-tasks.txt` using the block:

```
BUILD_TASK=<actual name>
NATIVE_TASKS=<space-separated list of the 4 tasks>
TEST_TASK=<actual name>
VERIFIER_TASK=<actual name>
```

**PASS** when all four names are recorded and each appears in `build/gradle-tasks-report.txt`.

**Fail →** if any name is missing from the report, IPGP was not applied — read `build.gradle.kts` and confirm the `id("org.jetbrains.intellij.platform")` line is present and Gradle exited 0. Never guess.

---

## 6. Authoritative build (§24 Gate 5)

```bash
./gradlew "$BUILD_TASK" --console=plain 2>&1 | tee build/verification-logs/04-build.log
```

**PASS** when:
1. Gradle exits 0.
2. `build/verification-logs/04-build.log` contains no `FAILED` lines.
3. A plugin archive appears in `build/distributions/` (or the equivalent directory IPGP writes to).

**Record:** the log file above, plus `ls -la build/distributions/` output in `build/verification-logs/04-build-dist.txt`.

**Fail →** if Gradle exits non‑zero, classify by the log:
- Compilation error → project defect. Report exact file:line; do NOT edit before naming the layer (§14).
- Dependency resolution error → runner network (§2.4) or transient upstream outage.
- `InstrumentationAnalysisTransform` OOM → runner memory (§2.2).
- `Toolchain` error → JDK not resolvable (§2.3, §4).

---

## 7. Authoritative native builds (§24 Gates 6, 9)

```bash
./gradlew $NATIVE_TASKS --console=plain 2>&1 | tee build/verification-logs/05-native.log
```

**PASS** when:
1. Gradle exits 0.
2. `build/distributions/` contains exactly four ZIPs named for the four targets.
3. Each ZIP contains exactly one target’s Node binary and no other target’s runtime.

**One‑target‑per‑ZIP scan (§24 Gate 12):**

```bash
for z in build/distributions/rsm-jetbrains-0.1.0-*.zip; do
  echo "=== $z ==="
  unzip -l "$z" | grep -E 'node-runtimes/[^/]+/'
done > build/verification-logs/06-one-target-scan.txt
```

Each ZIP must show exactly one `node-runtimes/<target>/` subtree; cross‑target contamination is a project defect.

**Record:** the two logs plus `sha256sum build/distributions/*.zip > build/verification-logs/07-dist-sha256.txt`.

**Fail →** if a ZIP contains multiple target subtrees, or is missing its target subtree, classify as **JetBrains plugin (packaging)** and STOP. Read `build.gradle.kts` `nativeVariants { … }` and the `prepare_node_runtimes.py` invocation.

---

## 8. Authoritative Gradle tests (§24 Gate 7)

```bash
./gradlew "$TEST_TASK" --console=plain 2>&1 | tee build/verification-logs/08-test.log
```

**PASS** when Gradle exits 0 and the log shows `0 failed` (or the equivalent JUnit banner). The prior 16/16 `junit-platform-console-standalone` evidence is supporting only — this step establishes the current authoritative Gradle test result.

**Record:** the log above plus `build/test-results/test/*.xml` (JUnit XML if present).

**Fail →** report the exact failing test class + method. Never disable a test. Never rewrite a test to make it pass. If the failure is a lifecycle assertion (`RsmRuntimeServiceTest`), the defect is in `RsmRuntimeService.kt`; if MCP (`McpStdioClientTest`), in `McpStdioClient.kt`. Classify by layer (§14) before editing.

---

## 9. Plugin Verifier (§24 Gate 8) — resolves the toolchain decision

```bash
./gradlew "$VERIFIER_TASK" --console=plain 2>&1 | tee build/verification-logs/09-verifier.log
```

**PASS** when Gradle exits 0 and the verifier reports zero release‑blocking findings against `sinceBuild=261+`. Compatibility notices (deprecation warnings) are acceptable if they are documented as not blocking release.

**Toolchain final resolution (§24 Gate 3, deferred from Step 4):**

| Verifier output | Decision | Action |
|---|---|---|
| Zero findings | **KEEP** `jvmToolchain(17)` | No source change. Record “KEPT” in `RSM_JETBRAINS_READINESS.md` §26. |
| “Class file version 65 not supported” or equivalent JVM mismatch | **CHANGE** to `jvmToolchain(21)` | Edit `build.gradle.kts` line 21 only. Re‑run Steps 6–9 in full. Record “CHANGED” with the verifier evidence line quoted. |
| IPGP recommends target‑derived default | **REMOVE** `jvmToolchain(17)` | Delete line 21. Re‑run Steps 6–9. Record “REMOVED” with reference. |

**Record:** the verifier log plus `build/verification-logs/10-toolchain-decision.md` (a two‑paragraph note stating: decision, evidence line, verifier link).

**Fail →** never mutate the toolchain without a verifier line to cite. If the verifier itself fails to run (network, cache), the fault is in §2.4, not the project.

---

## 10. Artifact structure verification (§24 Gate 9)

For each of the four ZIPs, verify:

```bash
for z in build/distributions/rsm-jetbrains-0.1.0-*.zip; do
  echo "=== $z ==="
  unzip -l "$z" | awk '{print $NF}' | sort | uniq
done > build/verification-logs/11-artifact-structure.txt
```

Every ZIP must contain:
- `rsm-jetbrains/lib/rsm-jetbrains-0.1.0.jar`
- `rsm-jetbrains/lib/instrumented-*.jar` (IPGP writes these — count only)
- `rsm-jetbrains/META-INF/plugin.xml`
- `rsm-jetbrains/node-runtimes/<target>/node[.exe]`
- `rsm-jetbrains/rsm-agent/dist/agent.js`
- `rsm-jetbrains/rsm-agent/node_modules/…`

**PASS** when every required path is present in every ZIP.

**Record:** the log above.

**Fail →** classify:
- Missing `plugin.xml` → **JetBrains plugin (descriptor)**.
- Missing `rsm-jetbrains-0.1.0.jar` → **JetBrains plugin (compilation)**.
- Missing `node[.exe]` → **JetBrains plugin (packaging)** — check `prepare_node_runtimes.py`.
- Missing `rsm-agent/dist/agent.js` → **rsm-agent (staging)** — check `build/agent-staging/`.

---

## 11. Artifact equivalence (§24 Gate 10)

Compare the newly generated ZIPs to the frozen anchors recorded in this repository:

**Frozen anchors (from `build/release-manifest.json`):**

| Target | Bytes | SHA‑256 |
|---|---|---|
| `windows-x64` | 42,734,584 | `7eea99e76c994d849780221250ffc9c69e3a501f43f1d6dcb8c240507e0dba35` |
| `macos-x64` | 48,492,636 | `469e82c15c85497fe622e8738bbb91ccbd6ea4e6d40a1fa14292810e3a540cd3` |
| `macos-arm64` | (see manifest) | `f4bf5f9635c069a6ec1487309f3cf2820d2c0089662262b5ae21cf9503e00075` |
| `linux-x64` | (see manifest) | `a997d4979fa526a659d8c6e516d3998e1d9f0468d01d8a5d1780b084829b49b4` |

```bash
sha256sum build/distributions/*.zip
diff <(sha256sum build/distributions/*.zip | awk '{print $1}' | sort) \
     <(jq -r '.pluginArchives | to_entries[] | .value.sha256' build/release-manifest.json | sort)
```

**PASS — strong outcome:** all four SHA‑256s match byte‑for‑byte.

**PASS — weak outcome (documented content‑equivalence):** SHA‑256s differ but the extracted content is equivalent:

```bash
mkdir -p /tmp/rsm-diff
for z in build/distributions/rsm-jetbrains-0.1.0-*.zip; do
  t=$(basename "$z" .zip)
  mkdir -p "/tmp/rsm-diff/$t"
  unzip -q "$z" -d "/tmp/rsm-diff/$t"
done
# Compare each pair against the pre-frozen ZIPs’ SHA-256s and content trees
```

**Fail →** if content differs materially, classify per §11 of `RSM_JETBRAINS_READINESS.md`:
- `expected build variation` (timestamps, jar directory order) → weak PASS.
- `toolchain variation` (JVM 17 vs 21 bytecode) → note in the toolchain decision (§9).
- `frozen artifact drift` (Node binary differs) → **critical** — the frozen manifest is out of sync with `nodejs.org`. Verify `SHASUMS256.txt` and update `release-manifest.json` in a separate change, not this run.
- `project defect` → STOP, classify by layer, do not overwrite the frozen artifacts.
- `unauthorized change` → git‑diff the tree; revert.

**Record:** `build/verification-logs/12-artifact-equivalence.md` with outcome (strong / weak / fail) and evidence.

---

## 12. Close the 15‑gate matrix (§24)

Aggregate the results:

| # | Gate | Established in |
|---|---|---|
| 1 | Wrapper contract | §2 pre‑flight + §3 |
| 2 | Gradle 9.8.0 execution | §3 |
| 3 | Toolchain decision | §9 |
| 4 | Task discovery | §5 |
| 5 | Authoritative build | §6 |
| 6 | Native variants | §7 |
| 7 | Gradle tests | §8 |
| 8 | Plugin Verifier | §9 |
| 9 | Authoritative artifact structure | §10 |
| 10 | Artifact equivalence | §11 |
| 11 | Node runtime | pre‑established, re‑confirmed in §10 |
| 12 | One‑target‑per‑ZIP | §7 |
| 13 | 132/132 baseline | pre‑established (upstream); confirm by running `bun test` in each of `rsm`, `rsm-host`, `rsm-cli`, `rsm-vscode`, `rsm-agent` |
| 14 | Source integrity | `git status && git diff --stat HEAD` — expected only the §22‑authorized six files, plus (conditionally) the toolchain decision from §9 |
| 15 | Wrapper reproducibility | §3 |

**PASS — required for promotion:** 15 / 15 PASS, 0 FAIL, 0 PENDING.

Update `RSM_JETBRAINS_READINESS.md`:
- Change status from `PARTIALLY READY — FINAL VERIFICATION PENDING` to `READY FOR INSTALLATION / INTEGRATION`.
- Append an `External Verification Evidence` section citing each log path.

**Fail →** any red → remain `PARTIALLY READY` and classify per §14.

---

## 13. Install the native variant (Local mode)

Move to a developer workstation with **IntelliJ IDEA Community or Ultimate 2026.1+** matching the workstation OS/arch:

| Workstation | ZIP |
|---|---|
| Windows x64 | `rsm-jetbrains-0.1.0-windows-x64.zip` |
| macOS Intel | `rsm-jetbrains-0.1.0-macos-x64.zip` |
| macOS Apple Silicon | `rsm-jetbrains-0.1.0-macos-arm64.zip` |
| Linux x64 | `rsm-jetbrains-0.1.0-linux-x64.zip` |

**Procedure:**
1. `File → Settings` (or `IntelliJ IDEA → Preferences` on macOS) → `Plugins`.
2. Click the gear icon → `Install Plugin from Disk…`.
3. Select the matching ZIP → confirm.
4. Restart the IDE when prompted.

**PASS** when:
- After restart, `Settings → Plugins → Installed` shows **RSM 0.1.0** from `RSM Contributors`.
- The plugin toggle is **enabled**.
- `idea.log` contains no `class-loader` / `descriptor` errors mentioning `rsm.jetbrains`.
- The plugin directory contains `lib/rsm-jetbrains-0.1.0.jar`, `node-runtimes/<target>/node[.exe]`, `rsm-agent/`, `rsm-agent/node_modules/`.

**Record:** attach an `idea.log` excerpt (grep for `rsm.jetbrains`) to `build/integration-evidence/13-install.log` and the plugin directory `ls -la` output to `13-install-tree.txt`.

**Fail →** if the plugin fails to load, capture the exception from `idea.log` and classify:
- `ClassNotFoundException: rsm.jetbrains.*` → **JetBrains plugin (compilation)** — Step 6 output is broken.
- `PluginException: descriptor…` → **JetBrains plugin (descriptor)** — `plugin.xml` invalid or `sinceBuild` mismatched.
- Missing native binary at runtime → **JetBrains plugin (packaging)** — Step 7 output is broken.

Reference: <https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk>

---

## 14. Verify plugin loading (Local mode)

With the IDE fully started:

1. `Help → Diagnostic Tools → Debug Log Settings…`.
2. Add category `#rsm.jetbrains` at level `INFO` (or `DEBUG` for deeper tracing). Apply.
3. Restart the IDE.
4. Open `Help → Show Log in Explorer/Finder/Files` and open `idea.log`.

**Expected on startup:**
```
[#rsm.jetbrains:INFO] RsmRuntimeService initialized (state=OFF)
```

**PASS** when the line appears with no accompanying `ERROR` / `WARN` from category `#rsm.jetbrains`.

**Record:** `build/integration-evidence/14-plugin-loading.log` — the last 50 lines of `idea.log` around startup.

**Fail →** classify:
- No `RsmRuntimeService initialized` line → **JetBrains plugin (application service registration)** — check `plugin.xml` `<applicationService>`.
- Exception on service construction → **JetBrains plugin (Kotlin)** — inspect `RsmRuntimeService.kt`.

---

## 15. Verify bundled Node (Local mode)

Do NOT use system Node. The plugin must resolve to its bundled runtime.

1. Locate the plugin dir under the IDE config: e.g. `~/.config/JetBrains/IntelliJIdea2026.1/plugins/rsm-jetbrains-0.1.0/` on Linux, or the equivalent on macOS / Windows.
2. Verify the target binary is present at `node-runtimes/<target>/node[.exe]`.
3. Run it directly to confirm identity:
   ```bash
   <plugin-dir>/node-runtimes/<target>/node --version
   # expected: v22.23.3
   <plugin-dir>/node-runtimes/<target>/node -p "process.arch + '/' + process.platform"
   # expected: match target — e.g. x64/linux, arm64/darwin
   ```

**PASS** when both commands print the expected values.

**Record:** `build/integration-evidence/15-bundled-node.log`.

**Fail →** classify:
- Wrong Node version → **JetBrains plugin (packaging)** — `prepare_node_runtimes.py` shipped the wrong archive.
- Wrong architecture → **JetBrains plugin (packaging)** — wrong ZIP installed, or wrong target extracted.
- Missing binary → **JetBrains plugin (packaging)** — ZIP is defective (Step 10 should have caught this).

The plugin **MUST NOT** honor `PATH`, `NODE`, or `RSM_NODE_OVERRIDE` — the security invariant is `JetBrains → bundled Node`, period. If any override is honored, that is a `NodeRuntimeResolver.kt` defect.

---

## 16. Verify rsm-agent startup (Local mode)

Configure the source store (required by `rsm-agent`):

- **Env:** `RSM_AGENT_SOURCE_STORE=map-inline:hello=world` before launching the IDE.
- **Or JVM sysprop:** append `-Drsm.agent.source_store=map-inline:hello=world` to the IDE VM options.

Then:
1. Open any project (or the welcome screen).
2. `Cmd/Ctrl+Shift+A` → search `RSM: Show Runtime Status`.
3. Invoke it. Expected balloon: `RSM runtime state OFF` (first invocation) or `RSM runtime state READY` if auto‑started.
4. Watch `idea.log` for:
   ```
   [#rsm.jetbrains:INFO] RSM sidecar READY (pid=<N>)
   ```

**Verify child process from the workstation shell:**
```bash
pgrep -a node | grep rsm-agent
# expected: one line matching:
#   <PID> <plugin-dir>/node-runtimes/<target>/node <plugin-dir>/rsm-agent/dist/agent.js …
```

**Verify argv contract** — inspect the discovered process line. Only these argv shapes are allowed:
- `agent.js --source-store <spec>`
- `agent.js --source-store <spec> --allow <permission>`
- `agent.js --source-store <spec> --allow-all`
- `agent.js --source-store <spec> --verbose`

No other flags are permitted. Inventing a new flag is a `ProcessLauncher.kt` defect.

**Record:** `build/integration-evidence/16-agent-startup.log` — the `idea.log` READY line and the `pgrep` output.

**Fail →** classify:
- `state=UNAVAILABLE` with `RsmRuntimeUnavailable: bundled Node not found` → **JetBrains plugin (resolver)**.
- `state=ERROR` with an exec error → **rsm-agent** if the JS module fails to load; **JetBrains plugin (launcher)** if `spawn` itself fails.
- Argv contains an unauthorized flag → **JetBrains plugin (launcher)**.

---

## 17. Verify MCP / stdio (Local mode)

Confirm the process is speaking MCP over stdio only (not HTTP / SSE / WebSocket):

**On Linux/macOS:**
```bash
# The child’s file descriptors
ls -la /proc/<child-pid>/fd/    # Linux
# or
lsof -p <child-pid>              # macOS
```

**Expected:** the process has only stdin/stdout/stderr FDs plus its own JS module files. **No** listening sockets, **no** established TCP connections, **no** UNIX sockets other than internal.

**Verify cleared environment (ProcessLauncher invariant `ParentEnvironmentType.NONE`):**
```bash
cat /proc/<child-pid>/environ | tr '\0' '\n'
```

**Expected:** a minimal environment — the source‑store spec, `PATH` (if set by the launcher), and nothing from the IDE parent process. No `HOME`, no `USER`, no IDE‑specific env leakage. This is the security invariant.

**Verify MCP handshake completed:** already implied by the `READY` line in Step 16 — `RsmRuntimeService.startFor()` transitions to `READY` only after `McpStdioClient.initialize()` and `tools/list` both succeed.

**Record:** `build/integration-evidence/17-mcp-stdio.log`.

**Fail →** classify:
- Listening socket present → **JetBrains plugin (transport)** or **rsm-agent (transport)** — one of them opened a non‑stdio transport. This is a prohibited action; STOP.
- Parent env leaked → **JetBrains plugin (launcher)** — `ProcessLauncher` did not clear env.
- No READY line → **rsm-agent (MCP)** or **rsm-host** — the handshake never completed. Inspect `idea.log` for the exact failure.

---

## 18. Verify `rsm_register` end‑to‑end (Local mode)

Two options — use whichever fits your workflow:

**Option A — dev mode action (`RsmDevExecuteAction`, if present):** invoke via `Find Action`, pass `{ tool: "rsm_register", args: { source_id: "greeting.hello" } }`.

**Option B — Kotlin scratch file:**
```kotlin
// scratch.kts
import com.intellij.openapi.application.ApplicationManager
import rsm.jetbrains.host.RsmRuntimeService

val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
val result = svc.register(mapOf("source_id" to "greeting.hello"))
println(result)
```
Run via `Run in Kotlin Console…`.

**Expected:**
- Return value contains `{ ok: true, source_id: "greeting.hello", … }` (or the equivalent MCP tool response payload).
- `idea.log` shows state transition `READY → ACTIVE`.

**Verify path:** the request went `JetBrains → RsmRuntimeService → Node → rsm-agent → MCP → rsm-host → rsm → register`. No other path is permitted.

**Record:** `build/integration-evidence/18-register.log`.

**Fail →** classify:
- `HostError: PERMISSION_DENIED` → expected only if you passed a permission‑restricting flag; retry with the permissive default.
- `HostError: RESOLVER_STORE_MISS` → the source id is not in the store — configure `RSM_AGENT_SOURCE_STORE` to include it.
- Any other error kind → **rsm-host** — but only two `HostError` kinds are authorized (`PERMISSION_DENIED`, `RESOLVER_STORE_MISS`). A third kind is a `rsm-host` defect.

---

## 19. Verify `rsm_replay` end‑to‑end (Local mode)

Immediately after Step 18 (state should be `ACTIVE`):

```kotlin
val out = svc.replay("greeting.hello")
println(out)
```

**Expected:** the raw output body of the registered resource. State remains `ACTIVE`.

**Record:** `build/integration-evidence/19-replay.log`.

**Fail →** classify:
- Empty / null output → **rsm** (registration succeeded but replay produced nothing). Reproduce with `rsm-cli replay greeting.hello` outside the IDE to isolate.
- Different content than expected → **rsm** or **rsm-agent (serialization)**.

---

## 20. Verify `rsm_remove` end‑to‑end (Local mode)

```kotlin
val gone = svc.remove("greeting.hello")
println(gone)
```

**Expected:** success ack. State remains `ACTIVE` (per frozen spec: `remove` does not transition state).

Then verify removal:
```kotlin
svc.replay("greeting.hello")     // should error / miss
```

Expected: `HostError: RESOLVER_STORE_MISS` (one of the two authorized error kinds).

**Full lifecycle established:** `REGISTER → REPLAY → REMOVE → REPLAY‑MISS`.

**Record:** `build/integration-evidence/20-remove.log`.

**Fail →** if `remove` transitions state incorrectly, that is a **JetBrains plugin (RsmRuntimeService state machine)** defect. If `replay` after `remove` returns the resource, that is a **rsm** or **rsm-host** defect.

---

## 21. Verify error handling (Local mode)

Exercise each failure mode and confirm the frozen 5‑state vocabulary is honored:

| Test | How | Expected state | Expected balloon |
|---|---|---|---|
| Missing bundled Node | Rename `node-runtimes/<target>/node[.exe]` → restart IDE → invoke Show Status | `ERROR` | `RSM runtime unavailable: bundled Node binary not present` |
| rsm-agent crash | Kill child Node process externally with `kill <pid>` | `UNAVAILABLE` (before auto‑restart), then `READY` (after 1st auto‑restart), then `ERROR` (after 2nd kill — cap reached) | Appropriate transition balloons |
| MCP disconnect | Send `SIGSTOP` to child, wait 10s, `SIGCONT` | Depends on timeout — verify no crash, either recovers or transitions to `UNAVAILABLE` | — |
| Source store miss | Invoke `rsm_replay` on non‑existent id | State remains `ACTIVE`, balloon warning shown | Balloon references miss |
| Permission denied | Invoke `rsm_register` with `--allow` restricting the source | State remains `ACTIVE`, error propagated | Balloon references permission |

**PASS** when all five rows behave as specified. The 5‑state enum must be exactly `{ OFF, READY, ACTIVE, UNAVAILABLE, ERROR }` — no other states may appear.

**Record:** `build/integration-evidence/21-error-handling.log`.

**Fail →** if an unknown state name appears → **JetBrains plugin (state machine)**. If a failure silently succeeds → same layer. Do NOT edit `rsm` for a plugin‑local state bug.

---

## 22. Verify restart / shutdown (Local mode)

**Auto‑restart cap (per session):**
1. From `READY` / `ACTIVE`, `kill <child-pid>` externally.
2. Invoke `rsm_register` or `Show Status`.
3. **Expected:** state → `READY` (auto‑restart fired once).
4. `kill <child-pid>` again.
5. Invoke a tool.
6. **Expected:** state → `ERROR` permanently. **No second auto‑restart in the same session.**

**Manual restart resets the budget:**
7. `Cmd/Ctrl+Shift+A` → `RSM: Restart Runtime`.
8. **Expected:** state → `READY`. Budget now reset.
9. `kill <child-pid>` again.
10. Invoke a tool.
11. **Expected:** state → `READY` (auto‑restart fires again because the budget was reset).

**Clean shutdown:** close the IDE. On the shell, verify no orphaned child:
```bash
pgrep -a node | grep rsm-agent
# expected: no output (empty)
```

The shutdown path is `RsmRuntimeService.stop()` → `SIGTERM` → 2‑second grace period → `SIGKILL` if still alive.

**Record:** `build/integration-evidence/22-restart-shutdown.log`.

**Fail →** classify:
- Second auto‑restart occurs → **JetBrains plugin (state machine)** — the `at‑most‑one` cap is broken.
- Manual restart does not reset budget → same layer.
- Orphaned child after IDE close → **JetBrains plugin (shutdown)** — `stop()` is not wired to app‑close hook.

---

## 23. Verify Split Mode (backend + JetBrains Client)

**Environment:** two hosts — a remote backend running IntelliJ IDEA 2026.1+ (`Backend`) and a local `JetBrains Client`. Backend must have Steps 13–14 already completed (plugin installed and loading).

**Procedure:**
1. On `Backend`: `<idea>/bin/remote-dev-server.sh run /path/to/project`.
2. Copy the printed join link.
3. On `Client`: JetBrains Client → `Connect via Join Link…` → paste.
4. Once connected, `Cmd/Ctrl+Shift+A` (from the Client UI) → `RSM: Show Runtime Status`.

**PASS** when:
- The balloon appears in the Client.
- The `pgrep -a node | grep rsm-agent` runs successfully **on the Backend host** and shows the child.
- The same check on the `Client` host shows **nothing** — the Client never spawns Node.
- `idea.log` on the Backend shows the normal READY transition.
- Steps 18–20 (`rsm_register` / `rsm_replay` / `rsm_remove`) succeed when invoked from the Client UI, with all execution on the Backend.

**Record:** `build/integration-evidence/23-split-mode.log` — join transcript, backend `pgrep`, client `pgrep` (empty), and one register/replay/remove round‑trip.

**Fail →** classify:
- Node spawns on Client → **JetBrains plugin (backend-only invariant broken)** — `plugin.xml` must declare only `<applicationService>`, no client‑side extensions.
- Balloon never appears on Client → routing issue; check the Backend `idea.log`.

Reference: <https://plugins.jetbrains.com/docs/intellij/remote-development-overview.html>

---

## 24. Verify Gateway / Remote Backend

**Environment:** a remote backend host (SSH‑reachable) and a local workstation with **JetBrains Gateway** installed.

**Procedure:**
1. Install the plugin on the remote backend per Steps 13–14 (using the ZIP that matches the **backend’s** OS/arch — NOT the workstation’s).
2. Launch Gateway on the workstation.
3. Connect to the remote backend via SSH.
4. Open a project.
5. From Gateway’s IDE UI, invoke `RSM: Show Runtime Status`.

**PASS** when:
- The plugin executes on the backend (verify `pgrep -a node` on the backend, empty on the workstation).
- Bundled Node executes on the backend (verify with `<plugin-dir>/node-runtimes/<backend-target>/node --version` on the backend).
- MCP remains stdio (verify per §17 on the backend).
- Steps 18–20 round‑trip succeeds.
- Any credentials referenced by the source store remain server‑side; the SSH channel carries only the Gateway protocol, not RSM traffic.

**Record:** `build/integration-evidence/24-gateway.log`.

**Fail →** classify same as §23. Additionally: if the plugin fails to install on the backend because of an OS/arch mismatch, the installer used the wrong ZIP — reinstall with the backend’s matching ZIP.

Reference: <https://www.jetbrains.com/help/idea/remote-development-a.html>

---

## 25. Compatibility decision (§14 Case A / B)

**Case A — all steps 3–24 PASS:**

1. Freeze the integration boundary: no changes to `rsm/`, `rsm-host/`, `rsm-agent/`, `rsm-cli/`, `rsm-vscode/`, `rsm-jetbrains/` without new explicit authorization.
2. Append per‑phase evidence to `RSM_JETBRAINS_READINESS.md`.
3. Move the readiness doc status from `PARTIALLY READY — FINAL VERIFICATION PENDING` to `RSM + JetBrains Integration COMPLETE`.
4. Record cross‑package total: **148/148** (132 upstream + 16 rsm-jetbrains).

**Case B — anything fails:**

1. STOP. Do not immediately mutate RSM.
2. Classify the failing layer using the matrix below.
3. Propose the smallest correction with a reference source (IPGP docs, IntelliJ SDK docs, MCP spec).
4. Get user approval before mutating.

**Layer classification matrix:**

| Symptom | Layer | Owning file(s) |
|---|---|---|
| Gradle task discovery empty / build fails at descriptor stage | JetBrains plugin (build config) | `build.gradle.kts`, `plugin.xml` |
| Compilation error in Kotlin | JetBrains plugin (Kotlin) | `src/main/kotlin/rsm/jetbrains/*.kt` |
| ZIP missing native binary | JetBrains plugin (packaging) | `scripts/prepare_node_runtimes.py`, `build.gradle.kts` `nativeVariants` |
| Bundled Node wrong version / arch | JetBrains plugin (packaging) | same as above |
| Plugin fails to load in IDE | JetBrains plugin (descriptor) | `plugin.xml` |
| `RsmRuntimeService` state machine wrong | JetBrains plugin (state) | `RsmRuntimeService.kt` |
| `ProcessLauncher` env leak / bad argv | JetBrains plugin (launcher) | `ProcessLauncher.kt` |
| MCP handshake fails | rsm-agent OR JetBrains plugin (MCP client) | `McpStdioClient.kt` first; then `rsm-agent` if client is correct |
| `HostError` third kind observed | rsm-host | `rsm-host/src/*` |
| Register succeeds, replay empty | rsm | `rsm/src/rsm.ts` |
| Non‑stdio transport observed | Prohibited action | Any layer that opened it — full audit |
| Backend‑only invariant broken | JetBrains plugin (backend-only) | `plugin.xml` — must be only `<applicationService>` |

**Record:** the classification and the proposed correction in `build/integration-evidence/25-classification.md`.

---

## 26. Final integration acceptance

Terminal state `RSM + JetBrains Integration COMPLETE` requires **all** rows below to be `PASS`, cross‑signed by log paths:

- [ ] 15/15 §24 verification gates (Steps 3–12)
- [ ] Plugin installs (Step 13)
- [ ] Plugin loads (Step 14)
- [ ] Bundled Node starts (Step 15)
- [ ] rsm-agent starts (Step 16)
- [ ] MCP initializes (Step 17)
- [ ] `rsm_register` end‑to‑end (Step 18)
- [ ] `rsm_replay` end‑to‑end (Step 19)
- [ ] `rsm_remove` end‑to‑end (Step 20)
- [ ] Error handling (Step 21)
- [ ] Shutdown + restart cap (Step 22)
- [ ] Split Mode (Step 23)
- [ ] Gateway / Remote Backend (Step 24)

When every row is checked, update `RSM_JETBRAINS_READINESS.md` with the final section:

```markdown
## Final State (yyyy-mm-dd)
RSM + JetBrains Integration COMPLETE
- Runner: <hostname / OS / arch>
- Toolchain resolution: <KEPT 17 | CHANGED 21 | REMOVED>
- Integration workstation: <OS / arch / IntelliJ build>
- Split-Mode host pair: <backend / client>
- Gateway backend: <hostname / arch>
- Evidence directory: build/verification-logs/ + build/integration-evidence/
- Cross-package tests: 148/148
```

---

## 27. Explicitly out of scope

The following are **not** part of this plan and must not be built while executing it. If a failure appears to require any of them, STOP and re‑classify (§25 Case B) — the failure is elsewhere.

- Task Management panel or database
- Any orchestration engine, scheduler, or workflow runner
- New RSM public API surface
- New `rsm-host` public API
- New `rsm-agent` MCP tool beyond the three frozen (`rsm_register`, `rsm_replay`, `rsm_remove`)
- `IntegrationRegistry` / `IntegrationManager` shared abstraction
- Marketplace discovery
- Any additional transport (HTTP, SSE, WebSocket)
- LLM SDK integration (`@anthropic-ai`, `openai`, `langchain`)
- `rsm-ide` or `rsm-jvm` package
- UI: `StatusBarWidget`, `ToolWindow`, `PersistentStateComponent`

---

## 28. Single execution rule

This plan is one continuous execution. Phases are checkpoints, not separate plans:

```
VERIFY (§3–§12) → PROMOTE (§12 status flip) → INSTALL (§13) →
CONNECT (§14–§17) → OPERATE (§18–§22) → VALIDATE (§23–§24) → ACCEPT (§26)
```

No downstream phase begins until its preceding phase has PASS evidence recorded in a log path listed above. If the runner (§3–§12) or the workstation (§13–§24) is not available, the state stays at `PARTIALLY READY — FINAL VERIFICATION PENDING` — do not redesign around the environment.

The immediate next actor is the external verification runner (§2). Once §12 yields 15/15 PASS, the same execution continues directly into §13 on a developer workstation.

---

## Appendix A — command sequence (copy‑paste)

For the **external Gradle runner**, from a clean clone / worktree:

```bash
cd rsm-jetbrains

# Env pre-flight (§2)
java -version
sha256sum gradle/wrapper/gradle-wrapper.jar

# §3 — wrapper
./gradlew --version 2>&1 | tee build/verification-logs/01-gradlew-version.log

# §4 — toolchains
./gradlew javaToolchains 2>&1 | tee build/verification-logs/02-javaToolchains.log

# §5 — task discovery
mkdir -p build/verification-logs
./gradlew tasks --all --console=plain > build/gradle-tasks-report.txt
# READ the report, then export the four names:
export BUILD_TASK=<from report>
export NATIVE_TASKS=<space-separated 4 tasks from report>
export TEST_TASK=<from report>
export VERIFIER_TASK=<from report>
{ echo "BUILD_TASK=$BUILD_TASK"; echo "NATIVE_TASKS=$NATIVE_TASKS";
  echo "TEST_TASK=$TEST_TASK"; echo "VERIFIER_TASK=$VERIFIER_TASK"; \
} > build/verification-logs/03-discovered-tasks.txt

# §6 — build
./gradlew "$BUILD_TASK" --console=plain 2>&1 | tee build/verification-logs/04-build.log

# §7 — native variants
./gradlew $NATIVE_TASKS --console=plain 2>&1 | tee build/verification-logs/05-native.log
for z in build/distributions/rsm-jetbrains-0.1.0-*.zip; do
  echo "=== $z ==="; unzip -l "$z" | grep -E 'node-runtimes/[^/]+/';
done > build/verification-logs/06-one-target-scan.txt
sha256sum build/distributions/*.zip > build/verification-logs/07-dist-sha256.txt

# §8 — tests
./gradlew "$TEST_TASK" --console=plain 2>&1 | tee build/verification-logs/08-test.log

# §9 — verifier
./gradlew "$VERIFIER_TASK" --console=plain 2>&1 | tee build/verification-logs/09-verifier.log
# Record toolchain decision manually in 10-toolchain-decision.md

# §10 — artifact structure
for z in build/distributions/rsm-jetbrains-0.1.0-*.zip; do
  echo "=== $z ==="; unzip -l "$z" | awk '{print $NF}' | sort | uniq
done > build/verification-logs/11-artifact-structure.txt

# §11 — equivalence
sha256sum build/distributions/*.zip
diff <(sha256sum build/distributions/*.zip | awk '{print $1}' | sort) \
     <(jq -r '.pluginArchives | to_entries[] | .value.sha256' build/release-manifest.json | sort) \
  | tee build/verification-logs/12-artifact-equivalence.md

# §12 — 132/132 baseline confirmation
for p in ../rsm ../rsm-host ../rsm-cli ../rsm-vscode ../rsm-agent; do
  (cd "$p" && bun test 2>&1 | tail -5)
done | tee build/verification-logs/13-baseline.log

# §12 — source integrity
git status | tee build/verification-logs/14-git-status.log
git diff --stat HEAD | tee build/verification-logs/15-git-diff.log
```

For the **developer workstation** (Steps 13–24): follow the procedures inline — those require GUI interaction and cannot be scripted end‑to‑end.

---

## Appendix B — evidence tree layout

At the end of a successful run, the repository will contain:

```
rsm-jetbrains/
├── build/
│   ├── gradle-tasks-report.txt                  (from §5)
│   ├── verification-logs/
│   │   ├── 00-runner-env.log
│   │   ├── 01-gradlew-version.log
│   │   ├── 02-javaToolchains.log
│   │   ├── 03-discovered-tasks.txt
│   │   ├── 04-build.log
│   │   ├── 04-build-dist.txt
│   │   ├── 05-native.log
│   │   ├── 06-one-target-scan.txt
│   │   ├── 07-dist-sha256.txt
│   │   ├── 08-test.log
│   │   ├── 09-verifier.log
│   │   ├── 10-toolchain-decision.md
│   │   ├── 11-artifact-structure.txt
│   │   ├── 12-artifact-equivalence.md
│   │   ├── 13-baseline.log
│   │   ├── 14-git-status.log
│   │   └── 15-git-diff.log
│   └── integration-evidence/
│       ├── 13-install.log
│       ├── 13-install-tree.txt
│       ├── 14-plugin-loading.log
│       ├── 15-bundled-node.log
│       ├── 16-agent-startup.log
│       ├── 17-mcp-stdio.log
│       ├── 18-register.log
│       ├── 19-replay.log
│       ├── 20-remove.log
│       ├── 21-error-handling.log
│       ├── 22-restart-shutdown.log
│       ├── 23-split-mode.log
│       ├── 24-gateway.log
│       └── 25-classification.md          (only if any step failed)
└── RSM_JETBRAINS_READINESS.md            (updated with final state)
```

---

## Appendix C — anchors to reproduce

Values the external actors must reproduce (or explain the discrepancy per §11):

- Wrapper JAR SHA-256: `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`
- `windows-x64` ZIP SHA-256: `7eea99e76c994d849780221250ffc9c69e3a501f43f1d6dcb8c240507e0dba35`
- `macos-x64` ZIP SHA-256: `469e82c15c85497fe622e8738bbb91ccbd6ea4e6d40a1fa14292810e3a540cd3`
- `macos-arm64` ZIP SHA-256: `f4bf5f9635c069a6ec1487309f3cf2820d2c0089662262b5ae21cf9503e00075`
- `linux-x64` ZIP SHA-256: `a997d4979fa526a659d8c6e516d3998e1d9f0468d01d8a5d1780b084829b49b4`
- `build.gradle.kts` SHA-256: `e2e184096642d8363d2d6463a4908accfbc59e0ab2f9ea75951676116070e524`
- `lib/rsm-jetbrains-0.1.0.jar` SHA-256: `8179308508334cbb2ce8e3a8a8346e170737093d899cd49fd38424f914bc4cc9`
- Bundled Node: `v22.23.3`, all 4 archives verified against `nodejs.org/dist/v22.23.3/SHASUMS256.txt`
- Frozen tests: **132/132** upstream + **16/16** rsm-jetbrains

---

*End of plan. Every phase is executable; no phase requires the sandbox agent as an actor.*
