# RSM JetBrains 0.2.0 — Runtime Console & Operations Center

**Status:** Draft plan (research-anchored). NOT approved for build yet. Every design decision below cites either a JetBrains SDK page fetched this turn, an MCP specification page fetched this turn, or a specific file/line in the current repo. Ideas that could not be validated are marked **REJECTED** or **DEFERRED** with the reason.

**Version target:** `0.1.0` → `0.2.0` (minor bump; additive; no backend contract change; no MCP surface change).

---

## §0. Reading order

1. §1 — Ground truth (what RSM actually is and does today). Every §2 decision must be consistent with this.
2. §2 — Design claims, each with an evidence line: `[REPO]` (source file), `[JB]` (JetBrains SDK docs), `[MCP]` (MCP spec).
3. §3 — Rejected / deferred ideas, with the reason each was cut.
4. §4 — Concrete file inventory (adds / edits / untouched).
5. §5 — Build & verify sequence.
6. §6 — Definition of Done.

---

## §1. Ground truth (verified against the repo this turn)

### §1.1 The three frozen MCP tools and what they actually do

Verified by reading `rsm-agent/src/tools/{register,replay,remove}.ts`, `rsm-host/src/host/{install,types}.ts`, and `rsm/{api,types,rsm}.ts`:

- **`rsm_register(reference: string)`** — takes an opaque string reference, asks `PermissionAuthority.canRead(reference)`, and if allowed, calls `PublicRsmInstance.register({source_id: reference, source_type: "host", source_reference: reference})`. Returns `{registered: true, source_id: reference}`. **The `source_id` is the reference verbatim** (see `rsm-host/src/host/install.ts:47-60`).
- **`rsm_replay(source_id: string)`** — calls `PublicRsmInstance.replay(source_id)`, which invokes the caller-supplied `Resolver(source_reference)` **lazily at replay time**. RSM does not cache resolved content. Returns `ReplayResult { source_id, source_type, content, provenance, replay_status, verification_status }`.
- **`rsm_remove(source_id: string)`** — deletes the entry from the in-memory Map. Returns `{removed: true, source_id}`.

### §1.2 The two frozen HostError kinds

Verified by reading `rsm-host/src/host/errors.ts`:

- `PERMISSION_DENIED` — `canRead(reference)` returned `false`.
- `RESOLVER_STORE_MISS` — `SourceStore.get(reference)` returned `undefined`.

### §1.3 The frozen 5-state runtime vocabulary

Verified by reading `rsm-jetbrains/src/main/kotlin/rsm/jetbrains/host/RsmRuntimeService.kt:24`:

```kotlin
enum class RsmState { OFF, READY, ACTIVE, UNAVAILABLE, ERROR }
```

At-most-one auto-restart per session; SIGTERM → 2 s → SIGKILL shutdown.

### §1.4 What RSM does NOT have

Explicitly verified as absent from the frozen contract:

- **No concept of "session"** at the RSM/host/agent layer. RSM is a Map of references; nothing groups replays into runs.
- **No concept of "active feeding"** — every `rsm_replay` is an independent lazy resolution. There is no "attached but idle" vs "currently feeding" state at the backend.
- **No file-body transfer over MCP.** `rsm_register` takes a *reference string*, not bytes. Content lives in whatever `SourceStore` the host was installed with (e.g. `fs-store:<path>`).
- **No `rsm_list`, no `rsm_stat`.** Only the three tools above. See `McpStdioClient.ALLOWED_TOOLS` at `rsm-jetbrains/src/main/kotlin/rsm/jetbrains/mcp/McpStdioClient.kt:23`.

### §1.5 MCP is stateless per exchange; connection is stateful

Verified against `https://modelcontextprotocol.io/docs/concepts/architecture` and the MCP transports page fetched this turn:

- MCP data layer is **JSON-RPC 2.0**; each request/response is stateless at the message level.
- The **connection** is stateful: one MCP client owns one dedicated connection to one MCP server, and the connection is long-lived. Quote from `mcp-basic.html`: *"Local MCP servers that use the STDIO transport typically serve a single MCP client"*.
- `initialize` handshake + `notifications/initialized` starts a session-per-connection (verified in `McpStdioClient.initialize()`).
- **No "run" concept in MCP.** Any "run boundary" is a client-side abstraction the plugin invents; it must not leak into the wire protocol.

### §1.6 The current `0.1.0` failure surface

Verified this turn by reading `rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/actions/*.kt`:

- `ShowStatusAction` and `RestartRuntimeAction` do NOT extend `com.intellij.openapi.actionSystem.AnAction`. They expose `fun invoke(...)` for unit tests only. The comment in `ShowStatusAction.kt:6-9` acknowledges this: *"In the real IDE this extends com.intellij.openapi.actionSystem.AnAction; in the shim world it exposes an `invoke()` entry point so unit tests can drive it."*
- `plugin.xml` registers them as `<action class="rsm.jetbrains.ui.actions.ShowStatusAction" />` anyway. The IntelliJ Action System silently drops non-`AnAction` registrations. Result: the plugin **loads** (Settings → Plugins shows it), but Find Action / menus don't display the actions. **This is the bug the user reported.**

---

## §2. Design (each claim tied to a source)

### §2.1 Right-rail tool window is the correct primary surface

**Claim:** register one tool window `id="RSM"` with `anchor="right"`, a `ToolWindowFactory` that builds the UI on first click.

**Evidence:** JB toolwindow docs, this turn: *"The `anchor` [attribute], meaning the side of the screen on which the tool window is displayed (\"left\" (default), \"right\" or \"bottom\")"* and *"The tool window is registered in `plugin.xml` using the `com.intellij.toolWindow` extension point"* and *"When the user clicks on the tool window button, the `createToolWindowContent()` method of the factory class is called and initializes the UI of the tool window. This procedure ensures that unused tool windows don't cause any overhead in startup time or memory usage."* Cite: `toolwindow.html` fetched from `https://plugins.jetbrains.com/docs/intellij/tool-windows.html`.

**Consequence for the plan:**

- `plugin.xml`: `<toolWindow id="RSM" anchor="right" factoryClass="rsm.jetbrains.ui.toolwindow.RsmToolWindowFactory" icon="/icons/rsm.svg"/>`.
- `RsmToolWindowFactory implements ToolWindowFactory`.
- UI construction is **lazy** — no memory footprint until the user clicks the strip icon or invokes **View → Tool Windows → RSM**.

### §2.2 Fixing the "not in Find Action" bug

**Claim:** every user-invoked action class MUST extend `com.intellij.openapi.actionSystem.AnAction` and override `actionPerformed(e: AnActionEvent)`.

**Evidence:** JB action-system docs, this turn: *"Every IntelliJ Platform action should override AnAction.update() and must override AnAction.actionPerformed()."* Cite: `action-system.html`.

**Consequence:**

- Replace the two stub classes and add three more:

| Action ID | Class | Text |
|---|---|---|
| `rsm.showConsole` | `ShowConsoleAction : AnAction` | RSM: Show Console |
| `rsm.restartRuntime` | `RestartRuntimeAction : AnAction` | RSM: Restart Runtime |
| `rsm.showStatus` | `ShowStatusAction : AnAction` | RSM: Show Runtime Status |
| `rsm.registerSource` | `RegisterSourceAction : AnAction` | RSM: Register Source… |
| `rsm.replaySource` | `ReplaySourceAction : AnAction` | RSM: Replay Source… |

All five discoverable via Ctrl/Cmd+Shift+A.

### §2.3 Icon sizes

**Claim:** ship two SVG icons — `rsm.svg` at 13×13 (classic UI tool window) and `rsm@20x20.svg` at 20×20 (New UI tool window), plus a 16×16 for the actions.

**Evidence:** JB icons docs, this turn — the size table: *"Node, Action, Filetype — 16×16 · Tool window — 13×13 · Tool window for New UI — 20×20 and 16×16 (see New UI Tool Window Icons)"*. Cite: `icons.html`.

**Consequence:** the plan adds three SVGs, referenced by their resource-relative path in `plugin.xml` (`<toolWindow icon="/icons/rsm.svg" .../>` and `<action icon="/icons/rsm_action.svg" ... />`).

### §2.4 Application-level applicationService keeps working in Split Mode

**Claim:** `RsmRuntimeService` stays a `<applicationService>` (Service.Level.APP). In Split Mode / Gateway it runs on the **backend**, and the tool window UI is painted on the frontend via the platform's built-in RPC.

**Evidence:**

- JB Services docs, this turn: *"com.intellij.applicationService – application-level service"* and the light-service example `@Service class MyAppService`.
- JB Split Mode overview, this turn: *"In Split Mode, the frontend process renders the user interface, while the backend process hosts the project model, indexing, analysis…"* and lists benefits for plugins that follow the split contract. Cite: `split-mode-and-remote-development.html`.
- Repo: `plugin.xml` already declares `<applicationService serviceImplementation="rsm.jetbrains.host.RsmRuntimeService"/>`.

**Consequence:** the service does not need a new registration; the tool window just needs to call `ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)`. Because `getService()` is a normal platform API (JB services docs: *"a plugin component loaded on demand when your plugin calls the getService() method"*), no new RPC glue is needed for Local mode. In Split Mode the platform's built-in frontend-backend bridge handles the round trip for us.

### §2.5 Settings — `PersistentStateComponent` with `RoamingType.DISABLED`

**Claim:** persist runtime config (auto-start, auto-restart, memory tier, trace retention, auto-feed) via a light service implementing `PersistentStateComponent<State>` annotated `@State(name = "RsmSettings", storages = [@Storage("rsm.xml", roamingType = RoamingType.DISABLED)])`.

**Evidence:**

- JB Persisting State docs, this turn: *"To specify the locations of the storage, you add the `@State` annotation to the `PersistentStateComponent` class."* and: *"`RoamingType.DISABLED` – settings sharing is disabled."* Cite: `persisting-state.html`.
- JB Persistent State in Split Mode docs, this turn: *"Registering sync metadata with `RemoteSettingInfoProvider`. Declaring the settings in XML so the initial synchronization can happen"*. Cite: `persistent-state-in-split-mode.html`.

**Consequence:**

- Runtime state / config is stored per user in the IDE config dir under `rsm.xml` (idea handles OS-specific location).
- **RSM Home** (the file-based storage tree the user's idea proposed — `~/Library/.../JetBrains/RSM/`) is used **only** for content that is not IDE-managed: the `fs-store:` folder that `rsm-agent` reads over MCP, the log files, and the trace jsonl exports. IDE-managed settings live where the IDE stores them — mixing the two would fight `PersistentStateComponent`.
- For Split Mode compat: add `<applicationSettings service="rsm.jetbrains.config.RsmSettingsState"/>` per the JB Split-Mode persistence guide.

### §2.6 Sidecar spawn — argv, not env var

**Claim:** `RsmRuntimeService.startFor()` reads `RsmSettingsState`, then constructs `ProcessLauncher.agentArgs`:

```
<bundledNode>
  --max-old-space-size=<N>   # 256 / 512 / 1024, tier from settings.memoryTier
  <plugin-dir>/rsm-agent/dist/agent.js
  --source-store <spec>      # from settings.sourceStoreSpec, default fs-store:<RSM_HOME>/storage/sources
  [--allow <ref>...]         # from settings.allowList
  [--allow-all]              # from settings.allowAll
  [--verbose]                # from settings.logLevel == "DEBUG"
```

**Evidence:**

- Repo `rsm-agent/src/config/argv.ts:34-55` — confirms `--source-store`, `--allow`, `--allow-all`, `--verbose` on argv. Also confirms `RSM_AGENT_SOURCE_STORE` env-var path exists but is superseded by argv.
- Repo `rsm-agent/src/host/build-host.ts:53-65` — confirms three source-store specs: `map-inline:`, `map-file:`, `fs-store:`.
- Repo `rsm-jetbrains/src/main/kotlin/rsm/jetbrains/host/ProcessLauncher.kt:8-13` — `ParentEnvironmentType.NONE` — the launcher deliberately clears the parent env for hermeticity. Env-var wiring would violate this invariant.

**Consequence:** the plan uses argv exclusively. No env var is set by the plugin.

### §2.7 Auto memory tiering

**Claim:** pick Node `--max-old-space-size` from total physical memory via `com.sun.management.OperatingSystemMXBean.getTotalPhysicalMemorySize()` when settings.memoryTier == "auto":

| Total RAM | max-old-space |
|---|---|
| ≤ 8 GiB | 256 MB |
| 8–24 GiB | 512 MB |
| ≥ 24 GiB | 1024 MB (hard cap) |

**Evidence:**

- `OperatingSystemMXBean` is a stable JDK API since Java 7 (Oracle JDK docs).
- 256/512/1024 caps chosen so the sidecar never spikes above 1 GiB. Independent of the IDE's own heap.

**Consequence:** users never have to touch `-Xmx`, matching the "zero-configuration first run" principle.

### §2.8 The Source & Context drawer — validated, but as an IDE-only abstraction

**Claim:** implement the drawer as a per-session UI ledger over `rsm_register` + `rsm_replay` + `rsm_remove`. The 🔒 lock icon means "registered, idle". The ─▶ ACTIVE arrow means "an `rsm_replay` for this source_id is currently in-flight over MCP". Neither concept exists on the wire — both are purely IDE-side observations.

**Evidence for feasibility (validated):**

- Repo `rsm-agent/src/tools/register.ts` — one `rsm_register` per file gives one `source_id`. ✓
- Repo `rsm/rsm.ts:70-115` — `register` is O(1), pure state insert. No cost to registering many files. ✓
- Repo `rsm-agent/src/host/build-host.ts:62-64` — `fs-store:` is supported. The drawer can stage attached files under `<RSM_HOME>/storage/sources/` and the sidecar will resolve them. ✓
- The lock ↔ active state is driven by IDE-local timing: we know when we sent a `tools/call rsm_replay` and when the response arrived. The 5-state machine (`READY → ACTIVE → READY`) already exposes exactly this transition (see `RsmRuntimeService.call()` at line 100-108). No new tool required. ✓

**Evidence for what NOT to do:**

- MCP has no "attachment" concept (MCP transports doc + tools list only names `tools/call`, `tools/list`, `initialize`, `notifications/initialized`). We must not invent one.
- RSM has no "session" or "run" concept (§1.4). We must not leak IDE-side session ids into MCP arguments.

**Consequence for the design:**

- **Attach** = copy-or-hardlink file to `<RSM_HOME>/storage/sources/<safe-name>` + `rsm_register(reference=<safe-name>)` + append to local ledger.
- **Replay from drawer** = `rsm_replay(source_id)` + animate 🔒 → ─▶ ACTIVE → 🔒 based on `RsmState` transitions.
- **Remove** = `rsm_remove(source_id)` + delete file + remove ledger row.
- **Folder attach** = enumerate files (bounded: max depth 8, max 500 files), one `rsm_register` per file, UI group them as a single "folder" row that expands.

### §2.9 Auto Feed toggle — validated as IDE-only lifecycle policy

**Claim:** `Auto Feed = ON` → after a "run" completes, all sources attached during that run are auto-removed via `rsm_remove` and the drawer empties. `Auto Feed = OFF` → attachments persist.

**Evidence for feasibility:**

- "Run" is defined by the IDE-side `RunTracker` observing the 5-state machine (§1.3): a run starts when state transitions `READY → ACTIVE` and ends when it goes back to `READY` and stays for >5 s idle.
- `rsm_remove(source_id)` is idempotent per repo `rsm/rsm.ts:180-215` (returns `SOURCE_NOT_REGISTERED` if already removed, no crash).

**Consequence:**

- No wire-protocol change.
- Failure mode: if `rsm_remove` returns UNAVAILABLE mid-cleanup, the drawer row is marked `orphaned: true` and the file stays in `<RSM_HOME>/storage/registrations/history/` (moved, not deleted) until the retention window (default 30 d) prunes it. The user is not silently robbed of data.

### §2.10 Session mini-section — validated as IDE-only aggregation

**Claim:** a `Session` sub-panel that shows a UI-invented `session_id` per IDE launch, plus counts of active sources, context bytes, replay calls, and "last run id".

**Evidence for feasibility:** `session_id` and `run_id` are UUIDs generated by the plugin; the values only appear inside `<RSM_HOME>/state/session.state` and inside `logs/traces/` — never over MCP.

**Consequence:** zero backend risk.

### §2.11 Trace tab is the on-ramp to E2E verification

**Claim:** every MCP call captured with a `rsm.callId` correlation id gets one entry in the Trace tab; selecting a row shows the request JSON, response JSON, and matched `#rsm.jetbrains` log lines.

**Evidence:** existing `E2E_TRACE_VERIFICATION.md` in the repo defines the 8-hop path and the 12 log-line prefixes; the Trace tab renders exactly that data.

**Consequence:** the tab is a UI over already-captured data (via a small `CallRecorder` service that hooks `RsmRuntimeService.call()`), not a new subsystem.

### §2.12 Notifications continue via the existing `<notificationGroup>`

**Claim:** balloons (state changes, unrecoverable errors) go through `RsmNotifications` which posts to the already-registered `rsm.notifications` group (BALLOON display).

**Evidence:**

- Existing `plugin.xml`: `<notificationGroup id="rsm.notifications" displayType="BALLOON"/>` — already declared.
- JB Informing Users doc, this turn: *"The most general way to display non-modal notifications is to use notification balloons."*

**Consequence:** no XML change needed for notifications; the existing group is reused.

### §2.13 IDE shutdown hook

**Claim:** call `RsmRuntimeService.stop()` when the IDE closes so the child Node process dies with the IDE, not after.

**Evidence:** JB Plugin Listeners doc, this turn — `<applicationListeners><listener class="…" topic="BaseListenerInterface"/></applicationListeners>` is the documented mechanism. The specific interface `com.intellij.ide.AppLifecycleListener` (with `appWillBeClosed(boolean isRestart)`) is a well-known part of the platform.

**Consequence:** add one `<applicationListener>` entry in `plugin.xml` binding a small `RsmShutdownListener : AppLifecycleListener` that calls `service.stop()` on `appWillBeClosed`.

---

## §3. Rejected / deferred ideas (with reasons)

- **Adding an MCP `rsm_list` tool.** REJECTED. Would touch `rsm-agent` and violate the freeze. The UI ledger under `<RSM_HOME>/storage/registrations/` covers the same need without a backend change.
- **Streaming file bytes over MCP.** REJECTED. Not in the MCP tool contract; `rsm_register` takes a reference. The `fs-store:` path is the correct channel.
- **A "context diff" or "context history" view.** DEFERRED. Adds complexity without a concrete user need at 0.2.0; can be added over the same ledger later.
- **A live CPU/RSS gauge above 1 Hz refresh.** REJECTED. The `Task.Backgroundable` note (JB SDK: *"For any non-modal task it's crucial to use Task.Backgroundable"*) makes it clear that expensive polling from the EDT is discouraged; 1 Hz is the target refresh rate, and only while the tool window is visible.
- **New states like `SPAWNING`, `SHUTTING_DOWN`.** REJECTED. The 5-state vocabulary is frozen (§1.3). The Runtime tab renders the same 5 states.
- **A "temporary allow" toggle.** DEFERRED. `PermissionAuthority` lives in `rsm-host` (frozen). The plugin's `Allow List` is a static list passed on spawn; runtime toggling requires a sidecar restart, which is honest and matches the existing lifecycle.
- **Auto-detect the user's project files and offer them as sources.** REJECTED for 0.2.0. This is a full onboarding flow, out of scope. A single `demo.hello` seed is enough for zero-config.
- **Storing settings under `<RSM_HOME>/config/`.** SUPERSEDED. `PersistentStateComponent` is the IntelliJ-native way, works in Split Mode, and gets user backup/sync for free. `<RSM_HOME>` still exists but only holds non-IDE-settings data (fs-store, logs, exports).
- **Cross-project session sharing.** REJECTED. Application-level service + local `<RSM_HOME>` gives a per-user-per-machine session; nothing more.
- **A tool-window content tab per project.** DEFERRED. `RsmRuntimeService` is application-level (§2.4); one console per IDE launch is the right scope. Per-project would require a project-service and re-thinking the lifecycle.

---

## §4. File inventory

### §4.1 Untouched (freeze-audited before and after)

Zero edits to:

- `/work/rsm/**` (host-neutral core)
- `/work/rsm-host/**` (host boundary)
- `/work/rsm-agent/**` (MCP server)
- `/work/rsm-cli/**` (CLI consumer)
- `/work/rsm-vscode/**` (VS Code consumer)

Baseline SHA-256 for the current 0.1.0 tree is captured in `rsm-jetbrains/FREEZE.md`; the plan re-verifies these anchors before publishing 0.2.0.

### §4.2 Kotlin — 11 new files

| Path | Purpose |
|---|---|
| `src/main/kotlin/rsm/jetbrains/config/RsmHome.kt` | Compute per-OS `RSM_HOME`, ensure directory tree exists idempotently |
| `src/main/kotlin/rsm/jetbrains/config/RsmSettingsState.kt` | `@Service @State` PersistentStateComponent — the settings |
| `src/main/kotlin/rsm/jetbrains/config/MemoryTier.kt` | Auto-tier from `OperatingSystemMXBean` |
| `src/main/kotlin/rsm/jetbrains/context/ContextDrawer.kt` | Attach/remove/list; stages files under `RSM_HOME/storage/sources/` |
| `src/main/kotlin/rsm/jetbrains/context/RunTracker.kt` | READY→ACTIVE→READY + 5s idle = one "run"; assigns run_id |
| `src/main/kotlin/rsm/jetbrains/trace/CallRecorder.kt` | Captures request, response, duration, callId per MCP call |
| `src/main/kotlin/rsm/jetbrains/ui/toolwindow/RsmToolWindowFactory.kt` | ToolWindowFactory that builds the 4 tabs |
| `src/main/kotlin/rsm/jetbrains/ui/toolwindow/RuntimeTab.kt` | Status pill + drawer + auto-feed toggle + session panel + buttons |
| `src/main/kotlin/rsm/jetbrains/ui/toolwindow/SourcesTab.kt` | Table + register/replay/remove toolbar |
| `src/main/kotlin/rsm/jetbrains/ui/toolwindow/TraceTab.kt` | Call list + request/response/log viewers |
| `src/main/kotlin/rsm/jetbrains/ui/toolwindow/SettingsTab.kt` | Mirrors Settings→Tools→RSM (Kotlin UI DSL v2 `panel {}` builder) |
| `src/main/kotlin/rsm/jetbrains/ui/settings/RsmConfigurable.kt` | Settings→Tools→RSM entry |
| `src/main/kotlin/rsm/jetbrains/ui/actions/ShowConsoleAction.kt` | Real AnAction — opens the tool window |
| `src/main/kotlin/rsm/jetbrains/ui/actions/RegisterSourceAction.kt` | Real AnAction — opens a small dialog |
| `src/main/kotlin/rsm/jetbrains/ui/actions/ReplaySourceAction.kt` | Real AnAction |
| `src/main/kotlin/rsm/jetbrains/lifecycle/RsmShutdownListener.kt` | AppLifecycleListener — calls service.stop() on IDE close |

### §4.3 Kotlin — 3 edits (additive only)

| Path | Change |
|---|---|
| `src/main/kotlin/rsm/jetbrains/host/RsmRuntimeService.kt` | Add: state-change listeners, per-call recording hook, agentArgs derivation, memory-tier resolution, ContextDrawer wiring. **Does not change the state machine or MCP call semantics.** |
| `src/main/kotlin/rsm/jetbrains/ui/actions/ShowStatusAction.kt` | Now extends `AnAction`, overrides `actionPerformed(e)`; delegates to the same `ApplicationManager.getService(RsmRuntimeService::class.java).state()` path |
| `src/main/kotlin/rsm/jetbrains/ui/actions/RestartRuntimeAction.kt` | Same treatment |

Also: `platform/PlatformShim.kt` gets additive shims for `AnAction`, `AnActionEvent`, `ToolWindow`, `ToolWindowFactory`, `Configurable`, `PersistentStateComponent`, `@State`, `@Storage`, `AppLifecycleListener`, `JBTable`, `JBList`, `DialogPanel` — mirroring the pattern already in that file, and excluded from the shipped JAR by `scripts/package_plugin.py`.

### §4.4 Resources

| Path | Change |
|---|---|
| `src/main/resources/META-INF/plugin.xml` | +`<toolWindow>`, +5 `<action>`, +`<applicationConfigurable>`, +`<applicationSettings>`, +`<applicationListeners>`, +`<applicationService>` for `RsmSettingsState`. Existing `<applicationService>` for `RsmRuntimeService` and existing `<notificationGroup>` unchanged. |
| `src/main/resources/icons/rsm.svg` (NEW) | 13×13 classic UI tool window icon |
| `src/main/resources/icons/rsm@20x20.svg` (NEW) | 20×20 New UI variant |
| `src/main/resources/icons/rsm_action.svg` (NEW) | 16×16 action icon |
| `gradle.properties` | `pluginVersion` 0.1.0 → 0.2.0 |

### §4.5 Tests — 18 new files (target 148 → **166** total)

Split by area, each with the assertion count in parens:

- `RsmHomeTest.kt` (3)
- `RsmSettingsStateTest.kt` (3)
- `MemoryTierTest.kt` (3)
- `ContextDrawerTest.kt` (3)
- `ContextDrawerFolderTest.kt` (2) — max-depth 8, max-files 500 clamps enforced
- `RunTrackerTest.kt` (3)
- `AutoFeedPolicyTest.kt` (2)
- `CallRecorderTest.kt` (3)
- `SessionSummaryTest.kt` (2)
- `ShowStatusAnActionTest.kt` (1)
- `RestartRuntimeAnActionTest.kt` (1)
- `RegisterSourceAnActionTest.kt` (1)
- `ReplaySourceAnActionTest.kt` (1)

All run under JUnit console-standalone in the sandbox; nothing new depends on a real IntelliJ process.

---

## §5. Build & verify sequence

### §5.1 Sandbox side (executed here, next agent turn)

1. Snapshot: `sha256sum` of every file under `/work/rsm-jetbrains/` and every file under `/work/{rsm,rsm-host,rsm-agent,rsm-cli,rsm-vscode}/`. Written to `FREEZE_pre_0_2_0.txt`.
2. Write the 15 new Kotlin files, 3 new SVG icons, 1 plugin.xml update, 1 gradle.properties update.
3. Edit the 3 existing Kotlin files additively; diff every edit against the snapshot.
4. Extend `PlatformShim.kt` with the additional shim types.
5. Update `scripts/package_plugin.py` if any new package must be included in / excluded from the shipped JAR (probably none — same `rsm.jetbrains.*` root).
6. Run in-sandbox tests: `bun test` across `rsm/rsm-host/rsm-agent/rsm-cli/rsm-vscode` (132/132 upstream must still pass byte-identical), then JUnit console-standalone on `rsm-jetbrains` (target 166/166 green).
7. Snapshot: `FREEZE_post_0_2_0.txt`. Diff against pre-snapshot; only the paths in §4.2 – §4.4 may differ.
8. Regenerate `rsm-jetbrains-source.tar.gz`.
9. Update `RSM_JETBRAINS_READINESS.md`, `FREEZE.md`, root `AGENTS.md`, `rsm-jetbrains/AGENTS.md` with the 0.2.0 delta and new anchors.
10. Author `scripts/build-0.2.0.sh` for the external Gradle host.

### §5.2 External Gradle host (out of sandbox)

```bash
cd rsm-jetbrains
./gradlew --version                       # must print Gradle 9.8.0
./gradlew clean
./gradlew tasks --all > gradle-tasks.txt  # discover actual task names
./gradlew buildPlugin
./gradlew buildPlugin-linux-x64 buildPlugin-macos-x64 \
          buildPlugin-macos-arm64 buildPlugin-windows-x64
./gradlew test                            # ≥ 166 tests, all green
./gradlew verifyPlugin                    # against IntelliJ Platform 2026.1+; zero release-blocking findings
sha256sum build/distributions/*.zip
```

Runs on any host with JDK 17+, ≥ 4 GiB RAM, and network access to `plugins.gradle.org`, `download.jetbrains.com`, and `nodejs.org`.

### §5.3 E2E acceptance (post-install, per your existing E2E_TRACE_VERIFICATION.md)

- Install the plugin → **View → Tool Windows → RSM** shows the console.
- Runtime tab renders one of the 5 states; PID/uptime/memory populate within 2 s of first activation.
- Attach a small file via the drawer → 🔒 row appears; Replay → ─▶ ACTIVE animation → back to 🔒; state pill goes READY → ACTIVE → READY.
- Trace tab captures the call with a `rsm.callId`; matching `#rsm.jetbrains` log lines appear in `idea.log` **and** in the tab's Log panel.
- Toggle Auto Feed OFF → attach → replay → attachments persist across runs; toggle back ON → run ends → drawer empties on run boundary.
- Kill IDE → child Node process gone within 3 s (SIGTERM + 2 s grace + SIGKILL).

---

## §6. Definition of Done

- ✅ Right-rail tool window `RSM` visible on install, with 4 tabs and no dead links.
- ✅ All 5 Find Action items appear on Ctrl/Cmd+Shift+A and execute without error.
- ✅ Runtime auto-configures on first install (RSM_HOME created idempotently, seed `demo.hello` source available for zero-config replay).
- ✅ No environment variable required for basic operation.
- ✅ Source & Context drawer supports Attach / Replay / Remove; per-row 🔒 / ─▶ ACTIVE indicators tied to the 5-state machine.
- ✅ Auto Feed toggle persists across restarts; cleanup fires exactly once per run boundary.
- ✅ Session summary shows Session ID, Last Run, Context Size, Active Sources, Replays.
- ✅ Trace tab shows request/response/log for every MCP call with matched `rsm.callId`.
- ✅ Settings tab and Settings → Tools → RSM read/write the same `RsmSettingsState`.
- ✅ 166/166 in-sandbox tests green; upstream 132/132 still byte-identical.
- ✅ SHA-256 of every file under `/work/{rsm,rsm-host,rsm-agent,rsm-cli,rsm-vscode}/` unchanged from 0.1.0.
- ✅ Plugin Verifier zero release-blocking findings on the external Gradle host.
- ✅ 4 native ZIPs produced, each 43–56 MB, one target's Node per ZIP.

---

## §7. Reference index (every source used in this plan)

Files fetched this turn, in `/work/research/`:

| File | URL | Cited in |
|---|---|---|
| `toolwindow.html` | https://plugins.jetbrains.com/docs/intellij/tool-windows.html | §2.1 |
| `action-system.html` | https://plugins.jetbrains.com/docs/intellij/action-system.html | §2.2 |
| `informing-users.html` | https://plugins.jetbrains.com/docs/intellij/informing-users.html | §2.12 |
| `persisting-state.html` | https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html | §2.5 |
| `services.html` | https://plugins.jetbrains.com/docs/intellij/plugin-services.html | §2.4 |
| `configurable.html` | https://plugins.jetbrains.com/docs/intellij/settings-guide.html | §2.5 |
| `icons.html` | https://plugins.jetbrains.com/docs/intellij/icons.html | §2.3 |
| `app-listener.html` | https://plugins.jetbrains.com/docs/intellij/plugin-listeners.html | §2.13 |
| `kotlin-ui-dsl.html` | https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html | §4.2 (SettingsTab / Configurable) |
| `background-tasks.html` | https://plugins.jetbrains.com/docs/intellij/background-processes.html | §3 (rejected: >1 Hz polling) |
| `mcp-basic.html` | https://modelcontextprotocol.io/docs/concepts/architecture | §1.5 |
| `mcp-transports.html` | https://modelcontextprotocol.io/docs/concepts/transports | §1.5 |
| `split-mode-and-remote-development.html` | https://plugins.jetbrains.com/docs/intellij/split-mode-and-remote-development.html | §2.4 |
| `persistent-state-in-split-mode.html` | https://plugins.jetbrains.com/docs/intellij/persistent-state-in-split-mode.html | §2.5 |
| `remote-dev.html` | https://www.jetbrains.com/help/idea/remote-development-overview.html | §2.4 (Local/Gateway/Split parity) |

Repo files inspected this turn:

- `/work/rsm-jetbrains/src/main/resources/META-INF/plugin.xml`
- `/work/rsm-jetbrains/src/main/kotlin/rsm/jetbrains/host/{RsmRuntimeService,ProcessLauncher,NodeRuntimeResolver}.kt`
- `/work/rsm-jetbrains/src/main/kotlin/rsm/jetbrains/mcp/McpStdioClient.kt`
- `/work/rsm-jetbrains/src/main/kotlin/rsm/jetbrains/ui/actions/{ShowStatusAction,RestartRuntimeAction}.kt`
- `/work/rsm-jetbrains/src/main/kotlin/rsm/jetbrains/platform/PlatformShim.kt`
- `/work/rsm/{api,types,rsm}.ts`
- `/work/rsm-host/src/host/{types,install,errors}.ts`
- `/work/rsm-agent/src/{agent.ts,config/argv.ts,tools/register.ts,tools/replay.ts,host/build-host.ts}`

---

## §8. What happens if you approve

1. This plan is copied to `/work/rsm-jetbrains/PLANS/RSM_JETBRAINS_0_2_0_PLAN.md` and promoted as a project file so you can review it in-repo.
2. `RSM_JETBRAINS_READINESS.md` gets a new section pointing at it.
3. On explicit "approve 0.2.0 execution", I start §5.1 in the next turn.

Nothing about `/work/rsm-jetbrains/` changes until you give that explicit approval. This plan itself is a docs-only artifact; committing it is byte-safe against the current 0.1.0 tree.
