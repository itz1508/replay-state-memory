# rsm-jetbrains — Final Readiness Report (Post-Handoff Verification)

## STATUS

**`PARTIALLY READY — FINAL VERIFICATION PENDING`**

## CLASSIFICATION

**`ENVIRONMENT BLOCKER`** — sandbox cgroup `memory.max = 1073741824` bytes (1 GiB). IPGP 2.19.0 recommends `-Xmx4G` per its own documentation (https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html). Every attempt to run Gradle tasks that evaluate `build.gradle.kts` and apply the IPGP 2.19.0 plugin classpath OOM-kills the daemon during `InstrumentationAnalysisTransform` on the plugin's ~40-jar transitive graph.

**No project defects demonstrated.** Zero unauthorized source changes.

## §30 Required Report

```
STATUS:                              PARTIALLY READY — FINAL VERIFICATION PENDING
CLASSIFICATION:                      ENVIRONMENT BLOCKER

WRAPPER:
  Gradle:                            9.8.0 (live-verified via ./gradlew --version)
  Wrapper JAR:                       PRESENT — 47,623 bytes
                                     SHA-256: 238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5
                                     Matches Gradle 9.8.0 official wrapperChecksum
  gradlew:                           PRESENT — 8,656 bytes, chmod +x
  gradlew.bat:                       PRESENT — 4,888 bytes

TOOLCHAIN:
  Gradle JVM:                        17.0.20.1 (Ubuntu OpenJDK)
  Kotlin JVM Toolchain:              jvmToolchain(17) — KEPT (unchanged per §7/§12)
  Decision:                          PENDING (env-blocked; A/B/C requires javaToolchains + verifyPlugin outputs)
  Evidence:                          Reference-only research declared INSUFFICIENT by §12:
                                       IPGP 2.19.0 module metadata → JVM 17 target
                                       IntelliJ Platform 2026.1 runtime → JBR 21
                                       IPGP changelog → target-derived toolchain default
                                     Concrete Gradle-authoritative evidence blocked by cgroup memory.

TASK DISCOVERY:
  Build task:                        PENDING (to be discovered on §9 runner)
  Native tasks:                      PENDING
  Test task:                         PENDING
  Plugin Verifier task:              PENDING

BUILD:
  Result:                            NOT EXECUTED — ENVIRONMENT BLOCKER

NATIVE VARIANTS (Gradle-regenerated):
  windows-x64:                       PENDING — existing artifact hash matches release-manifest.json
  macos-x64:                         PENDING — existing artifact hash matches release-manifest.json
  macos-arm64:                       PENDING — existing artifact hash matches release-manifest.json
  linux-x64:                         PENDING — existing artifact hash matches release-manifest.json

TESTS:
  Gradle:                            NOT EXECUTED — ENVIRONMENT BLOCKER
  Package baseline:                  132/132 PASS ✅  (re-verified this run)
                                       rsm         69/69
                                       rsm-host    15/15
                                       rsm-cli     18/18
                                       rsm-vscode  13/13
                                       rsm-agent   17/17

PLUGIN VERIFIER:
  Result:                            NOT EXECUTED — ENVIRONMENT BLOCKER
  Findings:                          NOT AVAILABLE

ARTIFACTS:
  Count:                             4 (unchanged)
  SHA-256:                           windows-x64  7eea99e76c994d849780221250ffc9c69e3a501f43f1d6dcb8c240507e0dba35
                                     macos-x64    469e82c15c85497fe622e8738bbb91ccbd6ea4e6d40a1fa14292810e3a540cd3
                                     macos-arm64  f4bf5f9635c069a6ec1487309f3cf2820d2c0089662262b5ae21cf9503e00075
                                     linux-x64    a997d4979fa526a659d8c6e516d3998e1d9f0468d01d8a5d1780b084829b49b4
  Manifest:                          All 4 SHA-256s match build/release-manifest.json exactly ✅
  One-target-per-ZIP:                Verified via unzip -l this run:
                                       linux-x64.zip     → node-runtimes/linux-x64/node       (124,827,920 B)
                                       macos-arm64.zip   → node-runtimes/macos-arm64/node     (112,925,600 B)
                                       macos-x64.zip     → node-runtimes/macos-x64/node       (115,419,824 B)
                                       windows-x64.zip   → node-runtimes/windows-x64/node.exe  (86,973,768 B)
                                     Exactly one node-runtimes/<target>/ subtree per ZIP. No cross-contamination.

NODE:
  Version:                           v22.23.3 (unchanged) ✅
  Integrity:                         4/4 archives match nodejs.org SHASUMS256.txt (per build/node-runtimes/manifest.json) ✅
  Smoke test:                        Bundled linux-x64/node → bundled rsm-agent --help ✅ (re-run this turn)
                                     Output: "rsm-agent 0.1.0 — MCP server exposing rsm-host to LLM agents."

SOURCE INTEGRITY:
  Result:                            PASS ✅
                                     Cumulative diff = exactly the §22-authorized 6 files:
                                       gradle/wrapper/gradle-wrapper.properties  (reconciliation)
                                       gradle/wrapper/gradle-wrapper.jar         (completion)
                                       gradlew                                    (completion)
                                       gradlew.bat                                (completion)
                                       FREEZE.md                                  (both phases)
                                       RSM_JETBRAINS_READINESS.md                 (both phases + this)
                                     Verified UNCHANGED (SHA-256 identity with Turn N-2):
                                       build.gradle.kts     e2e184096642d8363d2d6463a4908accfbc59e0ab2f9ea75951676116070e524
                                       lib/rsm-jetbrains-0.1.0.jar  8179308508334cbb2ce8e3a8a8346e170737093d899cd49fd38424f914bc4cc9
                                       All 9 Kotlin source files
                                       plugin.xml, gradle.properties, settings.gradle.kts, libs.versions.toml
                                       All 6 frozen-package spot-check files (rsm/api.ts, rsm.ts, types.ts,
                                                                                rsm-host/install.ts, rsm-cli/cli.js,
                                                                                rsm-agent/agent.js)
                                     Core file lines still 23/257/66.

REPRODUCIBILITY:
  Wrapper bootstrap:                 PASS ✅ (Live-proven this run: `./gradlew --version` from fresh
                                     Gradle-cache state downloaded gradle-9.8.0-bin.zip and reported Gradle 9.8.0)
  Full project build:                PENDING — ENVIRONMENT BLOCKER (requires §9 runner + Item 5)

OPEN ISSUES:
  1. §21 ENVIRONMENT BLOCKER — sandbox cgroup memory.max = 1 GiB. Cannot fit IPGP 2.19.0
     cold-cache InstrumentationAnalysisTransform on the plugin's ~40-jar transitive dependency graph.
     Not a project defect.

FINAL STATUS:                        PARTIALLY READY — FINAL VERIFICATION PENDING
```

## §24 Gate Matrix

| # | Gate | Status | Evidence |
|---|---|---|---|
| 1  | Wrapper contract complete | ✅ **PASS** | 4 files present; JAR SHA-256 matches Gradle 9.8.0 official checksum |
| 2  | Gradle 9.8.0 executes | ✅ **PASS** | `./gradlew --version` live-verified this run; log at `build/verification-logs/gradlew-version-item2.log` |
| 3  | Toolchain decision | ⏸ PENDING | Requires `./gradlew javaToolchains` + `verifyPlugin` on §9 runner |
| 4  | Task discovery | ⏸ PENDING | Requires `./gradlew tasks --all` on §9 runner |
| 5  | Authoritative build | ⏸ PENDING | Depends on Gate 4 |
| 6  | Native variants (Gradle-regen) | ⏸ PENDING | Depends on Gate 4 |
| 7  | Gradle tests | ⏸ PENDING | Depends on Gate 4 |
| 8  | Plugin Verifier | ⏸ PENDING | Depends on Gate 4 + 5 |
| 9  | Authoritative artifact structure | ⏸ PENDING | Depends on Gate 5/6 |
| 10 | Artifact equivalence | ⏸ PENDING | Existing 4 ZIPs match release-manifest.json exactly; Gradle-regenerated comparison pending |
| 11 | Node runtime | ✅ **PASS** | v22.23.3; 4/4 SHA-256 match `nodejs.org` SHASUMS256.txt |
| 12 | One-target-per-ZIP | ✅ **PASS** | `unzip -l` scan this run; exactly one target per ZIP |
| 13 | 132/132 baseline | ✅ **PASS** | Re-verified this run |
| 14 | Source integrity | ✅ **PASS** | Zero drift; cumulative diff = §22-authorized 6-file set |
| 15 | Wrapper reproducibility | ✅ **PASS** | `./gradlew` self-bootstraps Gradle 9.8.0 from clean state |

**Green: 7 of 15.  Pending (env-blocked, not failed): 8 of 15.  Failed: 0.**

## Frozen inputs preserved

IPGP `2.19.0` · Gradle wrapper `9.8.0` · IntelliJ Platform `2026.1` · `sinceBuild=261` · Node `v22.23.3` · `nativeVariants` · 4 targets (windows-x64, macos-x64, macos-arm64, linux-x64) · MCP over stdio · backend-only execution · `rsm`/`rsm-host`/`rsm-agent`/`rsm-cli`/`rsm-vscode` untouched · `jvmToolchain(17)` KEPT pending §9-runner authoritative evidence.

## §31 External-runner command sequence

```bash
cd rsm-jetbrains
./gradlew --version                                                       # reproduce Gate 2
./gradlew javaToolchains                                                  # Gate 3 evidence
./gradlew tasks --all --console=plain > build/gradle-tasks-report.txt    # Gate 4 discovery
./gradlew <actual-build-task> --console=plain                             # Gate 5
./gradlew <actual-native-variant-tasks>                                   # Gate 6
./gradlew <actual-test-task> --console=plain                              # Gate 7
./gradlew <actual-plugin-verifier-task>                                   # Gate 8
sha256sum build/distributions/*.zip                                       # Gates 9/10
```

Runner requirements (§9): native filesystem, ≥ 4 GiB memory available to Gradle daemon, network egress, JDK 17+, committed repository state used as-is. Per §26.2: only narrowly-scoped toolchain / build-config / compatibility / verifier corrections authorized, each requiring concrete authoritative evidence documented before mutation.

## License

Apache-2.0 (`LICENSE`).

---

## Option 1 Handoff — Accepted

The user's disposition of the Final Verification & Promotion Plan is **Option 1**:

> *"The verification run is not failed as a project. It is complete for this environment and externally blocked."*

Interpretation, per the plan's §5 and §27 (external-runner requirement) and §25 Case C (`PARTIALLY READY — FINAL VERIFICATION PENDING` when the environment remains blocked):

- The agent's role for this build is **complete for this environment.**
- The frozen deliverable is `/work/rsm-jetbrains/` in its current, unchanged state.
- Gates 3–10 remain formally **PENDING** with classification **`ENVIRONMENT BLOCKER`** — not converted to failures.
- No further mutation of the repository is authorized in this environment.
- Handoff surface: the eight commands listed under "§31 External-runner command sequence" above.

### Handoff snapshot (evidence for the receiving runner)

| Anchor | Value |
|---|---|
| Wrapper JAR SHA-256 | `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5` (matches Gradle 9.8.0 official `wrapperChecksum`) |
| `windows-x64` ZIP SHA-256 | `7eea99e76c994d849780221250ffc9c69e3a501f43f1d6dcb8c240507e0dba35` |
| `macos-x64` ZIP SHA-256 | `469e82c15c85497fe622e8738bbb91ccbd6ea4e6d40a1fa14292810e3a540cd3` |
| `macos-arm64` ZIP SHA-256 | `f4bf5f9635c069a6ec1487309f3cf2820d2c0089662262b5ae21cf9503e00075` |
| `linux-x64` ZIP SHA-256 | `a997d4979fa526a659d8c6e516d3998e1d9f0468d01d8a5d1780b084829b49b4` |
| Bundled Node | `v22.23.3` (SHA-256 verified against `nodejs.org` SHASUMS256.txt, 4/4) |
| Frozen packages | 132/132 (rsm 69/69, rsm-host 15/15, rsm-cli 18/18, rsm-vscode 13/13, rsm-agent 17/17) |
| Core file line counts | `rsm/api.ts` **23** · `rsm/rsm.ts` **257** · `rsm/types.ts` **66** |
| `build.gradle.kts` SHA-256 | `e2e184096642d8363d2d6463a4908accfbc59e0ab2f9ea75951676116070e524` |
| `lib/rsm-jetbrains-0.1.0.jar` SHA-256 | `8179308508334cbb2ce8e3a8a8346e170737093d899cd49fd38424f914bc4cc9` |

**Nothing on the receiving runner may modify these anchors.** Any observed divergence is either an authorized §26.2 correction (documented before mutation) or a run-integrity failure.

---

## Completion & Integration Plan — Status

The 12-item Completion & Integration Plan (Verify → Promote → Integrate → Validate → Complete) is now overlaid on this run.

**Sandbox-verifiable items completed this turn:**

| # | Phase · Item | Status | Notes |
|---|---|---|---|
| 1 | VERIFY · Sandbox-verifiable pre-handoff anchors | ✅ done | All 12 anchor sub-checks green: wrapper JAR SHA-256 = Gradle 9.8.0 official; 4 ZIP SHA-256s match `release-manifest.json` exactly; Node v22.23.3 4/4 verified; 132/132 baseline; core lines 23/257/66; `build.gradle.kts` + lib jar SHA-256 unchanged; one-target-per-ZIP invariant intact; `./gradlew --version` self-bootstraps; bundled Node executes bundled rsm-agent `--help` |

**External-runner-dependent items (abandoned as ENVIRONMENT BLOCKER, not failed):**

| # | Phase · Item | Blocker | Required actor |
|---|---|---|---|
| 2  | VERIFY · Task discovery + toolchain evidence | 1 GiB cgroup vs IPGP 2.19.0 InstrumentationAnalysisTransform | §5 external runner (≥ 4 GiB memory) |
| 3  | VERIFY · Authoritative Gradle build + native variants | Depends on Item 2 | §5 external runner |
| 4  | VERIFY · Gradle tests + Plugin Verifier + toolchain final resolution | Depends on Item 3 | §5 external runner |
| 5  | PROMOTE · Artifact equivalence + status flip | Depends on Items 2–4 | §5 external runner |
| 6  | INTEGRATE · Install into IntelliJ IDEA 2026.1+ | No IDE, no display server | Developer workstation with IDE 2026.1+ |
| 7  | INTEGRATE · Actions visible + RsmRuntimeService lazy start | Depends on Item 6 | Same workstation |
| 8  | VALIDATE · Node sidecar startup + MCP handshake in IDE | Depends on Item 7 | Same workstation |
| 9  | VALIDATE · MCP tool round-trip (register/replay/remove) | Depends on Item 8 | Same workstation |
| 10 | VALIDATE · Lifecycle (startup/shutdown/failure/restart) | Depends on Item 8 | Same workstation |
| 11 | VALIDATE · Backend-only across Local + Split + Gateway | No `remote-dev-server.sh` in sandbox, no multi-host | Workstation + Gateway host |
| 12 | COMPLETE · Integration compatibility decision + final freeze | Aggregates Items 2–11 | All above actors |

Terminal state **`RSM + JetBrains Integration COMPLETE`** cannot be recorded from this sandbox; per plan §14 Case C the correct status remains `PARTIALLY READY — FINAL VERIFICATION PENDING`.

Sandbox capability probe evidence this turn:

- `/sys/fs/cgroup/memory.max` = 1073741824 (1 GiB) — insufficient for IPGP 2.19.0 InstrumentationAnalysisTransform (recommended `-Xmx4G` per JetBrains docs).
- No IntelliJ IDEA installation detected (no `idea.sh`, no `idea.bat`, no `IntelliJ IDEA*` directory).
- `DISPLAY` empty, `WAYLAND_DISPLAY` empty, `xdpyinfo` not on PATH — headless.
- No `remote-dev-server.sh` for Split-Mode / Gateway validation.

No project defect demonstrated. No unauthorized mutation. All 12 items resolved (1 done, 11 abandoned with explicit ENVIRONMENT BLOCKER reasons naming the required external actor).

---

---

## End-to-End Trace Verification (canonical criterion)

Prior turns produced PASS/FAIL gate matrices for build hygiene, artifact structure, and per-operation smoke tests. Those documents remain in the repository as pre-integration hygiene, but they do NOT constitute integration correctness.

The **only meaningful correctness criterion for RSM ↔ JetBrains integration** is that every call originating in the IDE produces a fully-traced, end-to-end response — with a single correlation id (`rsm.callId`) visible on every hop from IDE → RsmRuntimeService → ProcessLauncher → bundled Node → rsm-agent → MCP/stdio → rsm-host → rsm — and matching request/response identifiers at every layer boundary. This is a structural property of the log stream, verifiable with `grep`, not a subjective pass/fail.

The canonical document is:

- **`E2E_TRACE_VERIFICATION.md`** — defines the eight-hop call graph, the correlation-id contract, the per-hop log-line format, the two invariants (every hop has both halves; matched ids at every boundary), the wiring each layer needs (RsmRuntimeService, ProcessLauncher, rsm-agent, rsm-host, rsm), the evidence-collection recipe for Local / Split Mode / Gateway, the six diagnostic patterns for incomplete traces, and the terminal-state criterion.

The three earlier documents remain in the repository as supporting material — build hygiene (`EXTERNAL_INTEGRATION_PLAN.md` §1–§12), automated runner (`scripts/run-external-verification.sh`), workstation checklist (`INTEGRATION_CHECKLIST.md`) — but their per-step PASS/FAIL semantics are superseded by the trace invariants. Read `E2E_TRACE_VERIFICATION.md` first; use the others only to reach the point where a live IDE can be invoked.

Integration is complete when a single `register / replay / remove` sequence, invoked from the IDE, produces a trace file containing all twelve distinct log-line prefixes for at least one `rsm.callId`, with matched ids at every layer boundary, on the correct host for the deployment mode (workstation for Local, backend for Split Mode, remote backend for Gateway). Not before, not otherwise.
