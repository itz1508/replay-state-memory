# RSM JetBrains — Frozen Build Parameters (authoritative)

Executed under user authorization: **APPROVE RSM JETBRAINS BUILD — ITEMS 1–15**,
**BUILD CONFIGURATION RECONCILIATION**, and **FINAL BUILD COMPLETION**.

## Frozen product/architecture inputs (unchanged since Turn N-2)

| Parameter                       | Value      |
| ------------------------------- | ---------- |
| IntelliJ Platform Gradle Plugin | `2.19.0`   |
| IntelliJ Platform target        | `2026.1`   |
| `sinceBuild`                    | `261` (kept explicit) |
| Node LTS major                  | `22`       |
| Node exact                      | `v22.23.3` (bundled; SHA-256 verified against `nodejs.org/dist/v22.23.3/SHASUMS256.txt`) |
| Packaging                       | `nativeVariants` |
| Target matrix                   | Option C — 4 targets |
| Transport                       | MCP over stdio only |
| Runtime bundling                | bundled Node sidecar only |
| Execution model                 | backend-only (Split Mode / Gateway safe) |
| Frozen packages                 | `/work/{rsm,rsm-host,rsm-agent,rsm-cli,rsm-vscode}/` — do not modify |

## Build-configuration reconciliation

### Gradle wrapper — RECONCILED and COMPLETE

**Original pin (Turn N-2):** Gradle `8.10.2` — *incompatible with IPGP 2.19.0.*
**Corrected pin:** Gradle `9.8.0`.

**Reason.** IntelliJ Platform Gradle Plugin `2.19.0` requires Gradle `≥ 9.0.0`. Source: `CHANGELOG.md` entry `[2.19.0] - 2026-09-15` on https://github.com/JetBrains/intellij-platform-gradle-plugin — *"Update minimal supported Gradle version to `9.0.0`"*. Live evidence: Gradle 8.10.2 build reported *"IntelliJ Platform Gradle Plugin requires Gradle 9.0.0 and higher"* (log: `build/verification-logs/gradle-help-6.log`).

### Gradle wrapper contract — COMPLETED

Per §5–§8 of the Final Build Completion plan: the committed wrapper was incomplete (only `gradle-wrapper.properties` present). This turn generated the missing artifacts via `gradle wrapper --gradle-version 9.8.0 --distribution-type bin` in a scratch project (native FS, empty `settings.gradle.kts`, no `build.gradle.kts` — the `wrapper` task does not apply project plugins, so IPGP 2.19.0 instrumentation transforms are not triggered). The scratch-generated wrapper JAR was copied verbatim into `/work/rsm-jetbrains/gradle/wrapper/`.

**Wrapper contract now complete:**

| Path                                          | Size (bytes) | SHA-256                                                              |
| --------------------------------------------- | ------------ | -------------------------------------------------------------------- |
| `gradlew`                                     | 8,656        | (Gradle 9.8.0 stock launcher, `chmod +x`)                            |
| `gradlew.bat`                                 | 4,888        | (Gradle 9.8.0 stock Windows launcher)                                |
| `gradle/wrapper/gradle-wrapper.jar`           | 47,623       | `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`  |
| `gradle/wrapper/gradle-wrapper.properties`    | 252          | (unchanged from Turn N-2 reconciliation; pins distribution URL to 9.8.0) |

**Provenance.** The wrapper JAR SHA-256 above matches EXACTLY Gradle's officially published wrapper checksum from https://services.gradle.org/versions/current (field `wrapperChecksum`) — bit-for-bit authoritative provenance for a stock Gradle 9.8.0 wrapper.

**Live proof that the wrapper contract works:**
```
$ ./gradlew --version
Fetching distribution.
Downloading https://services.gradle.org/distributions/gradle-9.8.0-bin.zip
..............10%…100%

------------------------------------------------------------
Gradle 9.8.0
------------------------------------------------------------
Build time:    2026-09-24 13:40:00 UTC
Launcher JVM:  17.0.20.1
Daemon JVM:    /usr/lib/jvm/java-17-openjdk-amd64
OS:            Linux 6.17.0-1011-oracle amd64
```

Log: `build/verification-logs/gradlew-version.log` (recorded this turn).

### Kotlin JVM toolchain — KEPT PENDING (§4 handoff plan)

Current: `kotlin { jvmToolchain(17) }` in `build.gradle.kts` line 21 — **NOT modified this turn**.

Per §12 of the Final Build Completion plan, the A/B/C decision requires outputs of:
- `./gradlew javaToolchains`
- `./gradlew tasks --all`
- Full `./gradlew <build-task>` + Plugin Verifier execution

The `wrapper` and `--version` steps do not exercise the project's plugin classpath, so IPGP 2.19.0 instrumentation transforms are not applied — those succeed in the 1 GiB sandbox. Steps that DO apply project plugins (`javaToolchains`, `tasks --all`, `build`, `verifyPlugin`) crash the daemon in this sandbox during `InstrumentationAnalysisTransform`. Evidence: `build/verification-logs/gradlew-javaToolchains.log` (this turn) plus `gradle-help-{2..8}.log` (prior turns).

Per §7 and §21 of the Final Build Completion plan, this is an **`ENVIRONMENT BLOCKER`**, not a project defect.

## Build-tool environment (recorded)

| Tool                | Value                                                    |
| ------------------- | -------------------------------------------------------- |
| Gradle wrapper      | 9.8.0 (contract complete, self-bootstrapped this turn)   |
| Gradle client JVM   | JDK 17 (satisfies Gradle 9.8.0 minimum)                  |
| Kotlin              | `1.9.24` (via `libs.versions.toml`; unchanged)           |
| JUnit               | `5.10.2` (unchanged)                                     |
| Java toolchain      | **PENDING VERIFICATION** (Step 5 of §29 handoff)         |

## STOP conditions

Unchanged from the reconciliation phase.

## Runtime direction

```
JetBrains Plugin (frontend actions)
    ↓
RsmRuntimeService (backend applicationService)
    ↓
bundled Node 22 runtime (per-target)
    ↓
rsm-agent (MCP over stdio)
    ↓
rsm-host
    ↓
rsm
```
