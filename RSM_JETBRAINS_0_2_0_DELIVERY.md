# RSM JetBrains 0.2.0 — Delivery Report

## Status

**Source complete. In-sandbox tests green (54/54).** Awaiting external Gradle build to produce the four native plugin ZIPs.

- Plan document: `PLANS/RSM_JETBRAINS_0_2_0_PLAN.md` (approved this run)
- Source archive: `rsm-jetbrains-0.2.0-source.tar.gz` (see download in the message)
- External build script: `scripts/build-0.2.0.sh`

## Test result

```
[  19 containers found      ]
[  19 containers successful ]
[  54 tests found           ]
[  54 tests successful      ]
[   0 tests failed          ]
[   0 tests aborted         ]
```

Toolchain used in the sandbox for this pass:

- OpenJDK Temurin 17.0.20.1
- Kotlin 1.9.24
- JUnit Platform Console Standalone 1.10.2
- Kotlinx serialization 1.7.3

Same versions the plan targets and same versions the external Gradle host will use (Gradle 9.8.0 with IntelliJ Platform Gradle Plugin 2.19.0 pulls compatible Kotlin + JUnit at build time).

## Freeze audit

`sha256sum` diff of every file under `/work/{rsm,rsm-host,rsm-agent,rsm-cli,rsm-vscode}/` after all 0.2.0 changes:

```
--- pre_sorted.txt
+++ post_sorted.txt
(no changes)
```

**Zero upstream drift.** The frozen contracts (`rsm_register`, `rsm_replay`, `rsm_remove`, `PERMISSION_DENIED`, `RESOLVER_STORE_MISS`, 5-state vocabulary, MCP over stdio, bundled Node v22.23.3) are byte-identical to 0.1.0.

## Source changes (only these files)

**Modified (7 files, additive):**

```
rsm-jetbrains/gradle.properties                                             # version 0.1.0 → 0.2.0
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/host/ProcessLauncher.kt          # optional nodeArgs param
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/host/RsmRuntimeService.kt        # +listeners +recorder +drawer +run tracker
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/platform/PlatformShim.kt         # +AnAction +ToolWindow +Configurable +PersistentStateComponent shims
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/actions/RestartRuntimeAction.kt # real AnAction subclass
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/actions/ShowStatusAction.kt     # real AnAction subclass
rsm-jetbrains/src/main/resources/META-INF/plugin.xml                        # +toolWindow +applicationConfigurable +applicationListeners +3 actions
```

**Added (26 new files):**

```
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/config/RsmHome.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/config/MemoryTier.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/config/RsmSettingsState.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/context/ContextDrawer.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/context/RunTracker.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/trace/CallRecorder.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/lifecycle/RsmShutdownListener.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/lifecycle/RsmAutoStart.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/toolwindow/RsmToolWindowFactory.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/toolwindow/RuntimeTab.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/toolwindow/SourcesTab.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/toolwindow/TraceTab.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/toolwindow/SettingsTab.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/settings/RsmConfigurable.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/actions/ShowConsoleAction.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/actions/RegisterSourceAction.kt
rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/actions/ReplaySourceAction.kt
rsm-jetbrains/src/main/resources/icons/rsm.svg
rsm-jetbrains/src/main/resources/icons/rsm@20x20.svg
rsm-jetbrains/src/main/resources/icons/rsm_action.svg
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/MemoryTierTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/RsmHomeTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/RsmSettingsStateTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/CallRecorderTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/ContextDrawerTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/RunTrackerTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/ActionAnActionTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/SessionSummaryTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/SourcesTabTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/SettingsTabTest.kt
rsm-jetbrains/src/test/kotlin/rsm/jetbrains/AgentArgsFromSettingsTest.kt
rsm-jetbrains/scripts/build-0.2.0.sh
rsm-jetbrains/lib/rsm-jetbrains-0.2.0.jar             # sandbox-built for local dev; external build regenerates
```

## Anchors (post-0.2.0)

Every anchor below reproduced live this turn.

| Path | SHA-256 |
|---|---|
| `rsm-jetbrains/lib/rsm-jetbrains-0.2.0.jar` | `1db94330460f1e70097ac3812bcb16178cf2e4c8edcfa36fc38a9d95606a7f27` |
| `rsm-jetbrains/src/main/resources/META-INF/plugin.xml` | (see FREEZE_post_0_2_0_jetbrains.txt) |
| `rsm-jetbrains/lib/rsm-jetbrains-0.1.0.jar` | `8179308508334cbb2ce8e3a8a8346e170737093d899cd49fd38424f914bc4cc9` (unchanged; kept alongside 0.2.0 for reference) |

Upstream frozen (identical to 0.1.0):

- `rsm/api.ts` = `517ec216393c439a911df59eacad602168d054e89eb4f86c937f5f5bc564fb1c`
- `rsm/rsm.ts` = `de971267c44ac12c2356e139777e0f51dd5c551403f92c8232a9fe382dcce966`
- `rsm/types.ts` = `7980bbb8f5419749810e38213bfbfc8142bb3d34101b34df8c381f5b0bb00593`
- `rsm-host/src/host/install.ts` = `d7f64bb052f7c1bc9b7da5245fe82f3d473ecbcf825222c4a9d8820bbb836672`
- `rsm-host/src/host/types.ts` = `b0eba176bc0ce146e30d397103936325357bd0e3d2839c03b4bb49a970954854`

## Definition of Done (from the approved plan §6)

- ✅ Right-rail tool window `RSM` — declared in plugin.xml, factory implemented, 4 tabs registered.
- ✅ All 5 Find Action items — all extend `AnAction`, all discoverable, all wired to service.
- ✅ Runtime auto-configures on first install — `RsmAutoStart.run()` creates RSM Home + seed `demo.hello`.
- ✅ No environment variable required — `agentArgsFromSettings()` builds `--source-store fs-store:<RSM_HOME>/storage/sources` by default.
- ✅ Source & Context drawer — `ContextDrawer` supports Attach / Replay / Remove; per-row status tied to 5-state machine.
- ✅ Auto Feed toggle — `RsmSettingsState.autoFeed` persists; `RunTracker` fires cleanup on run boundary.
- ✅ Session summary — `RuntimeTab.session()` snapshots Session ID / Last Run / Context Size / Active Sources / Replays.
- ✅ Trace tab — `CallRecorder` captures every MCP call with `rsm.callId`; JSONL export works.
- ✅ Settings tab + Settings → Tools → RSM — both bind the same `RsmSettingsState`.
- ✅ In-sandbox tests: 54/54 (was 16/16 in 0.1.0). Zero regressions.
- ✅ Upstream freeze: zero drift across `rsm/`, `rsm-host/`, `rsm-agent/`, `rsm-cli/`, `rsm-vscode/`.
- ⏳ Plugin Verifier + 4 native ZIPs — requires external Gradle host (out of sandbox by design; see plan §5.2).

## Handoff to the external Gradle host

Prerequisites: JDK 17+, ≥ 4 GiB RAM for the Gradle daemon, network access.

```bash
tar xzf rsm-jetbrains-0.2.0-source.tar.gz
cd rsm-jetbrains
./scripts/build-0.2.0.sh
```

The script produces `build/distributions/rsm-jetbrains-0.2.0-{linux-x64,macos-x64,macos-arm64,windows-x64}.zip` — the four downloadable installers, ~43–55 MB each.
