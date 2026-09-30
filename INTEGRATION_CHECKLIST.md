# RSM JetBrains — Integration Checklist (Developer Workstation)

Companion to `EXTERNAL_INTEGRATION_PLAN.md` §13–§26. Print this page and check each box on a real IntelliJ IDEA 2026.1+ workstation after the Gradle runner has produced 15/15 PASS on §3–§12.

**Workstation must have:**
- IntelliJ IDEA Community or Ultimate **2026.1+** matching the OS/arch of one of the four ZIPs.
- Shell access (Terminal / PowerShell) for `pgrep`, `ls`, `sha256sum`.
- The corresponding ZIP from `build/distributions/`.

Evidence directory: create `build/integration-evidence/` in the repo before starting; save every log there with the numbering below.

---

## §13 · Install

- [ ] IDE version confirmed **2026.1+** (`Help → About`).
- [ ] Correct ZIP selected for the workstation OS/arch.
- [ ] `Settings → Plugins → gear → Install Plugin from Disk…` → picked ZIP → restart accepted.
- [ ] After restart, `Settings → Plugins → Installed` shows **RSM 0.1.0** by **RSM Contributors**, enabled.
- [ ] Plugin directory contains: `lib/rsm-jetbrains-0.1.0.jar`, `node-runtimes/<target>/node[.exe]`, `rsm-agent/dist/agent.js`, `rsm-agent/node_modules/`.
- [ ] `idea.log` has zero errors mentioning `rsm.jetbrains` on startup.
- Save: `13-install.log` (idea.log grep) + `13-install-tree.txt` (`ls -la` of plugin dir).

## §14 · Plugin loading

- [ ] `Help → Diagnostic Tools → Debug Log Settings…` → added `#rsm.jetbrains` at `INFO`.
- [ ] Restart IDE.
- [ ] `idea.log` contains `[#rsm.jetbrains:INFO] RsmRuntimeService initialized (state=OFF)`.
- Save: `14-plugin-loading.log` (last 50 lines around startup).

## §15 · Bundled Node

- [ ] Located plugin dir on filesystem.
- [ ] Ran `<plugin-dir>/node-runtimes/<target>/node --version` → prints `v22.23.3`.
- [ ] Ran `<plugin-dir>/node-runtimes/<target>/node -p "process.arch+'/'+process.platform"` → matches target.
- [ ] Confirmed the plugin does NOT honor `PATH` / `NODE` / `RSM_NODE_OVERRIDE` — only bundled runtime is used.
- Save: `15-bundled-node.log`.

## §16 · rsm-agent startup

- [ ] Configured source store: `RSM_AGENT_SOURCE_STORE=map-inline:hello=world` (env) OR `-Drsm.agent.source_store=map-inline:hello=world` (VM options).
- [ ] `Cmd/Ctrl+Shift+A` → `RSM: Show Runtime Status` → invoked.
- [ ] Balloon appeared with `RSM runtime state OFF` or `READY`.
- [ ] `idea.log` shows `RSM sidecar READY (pid=<N>)`.
- [ ] `pgrep -a node | grep rsm-agent` — exactly one match; argv is `<bundled-node> <plugin>/rsm-agent/dist/agent.js --source-store <spec>` (only `--source-store`, `--allow`, `--allow-all`, `--verbose` allowed).
- Save: `16-agent-startup.log`.

## §17 · MCP / stdio

- [ ] Child process has NO listening sockets (Linux: `ls -la /proc/<pid>/fd/` shows no `socket:`; macOS: `lsof -p <pid>` shows no `IPv4`/`IPv6`/`unix` listeners).
- [ ] Child environment cleared: `cat /proc/<pid>/environ | tr '\0' '\n'` (Linux) shows minimal env — no `HOME`/`USER` leaked from IDE.
- [ ] No HTTP / SSE / WebSocket transport observed anywhere.
- Save: `17-mcp-stdio.log`.

## §18 · rsm_register

- [ ] Invoked `register({ source_id: "greeting.hello" })` via dev action or Kotlin scratch calling `ApplicationManager.getApplication().getService(RsmRuntimeService::class.java).register(...)`.
- [ ] Returned success payload.
- [ ] `idea.log` shows state transition `READY → ACTIVE`.
- Save: `18-register.log`.

## §19 · rsm_replay

- [ ] `replay("greeting.hello")` returned the raw output body of the registered resource.
- [ ] State remained `ACTIVE`.
- Save: `19-replay.log`.

## §20 · rsm_remove

- [ ] `remove("greeting.hello")` returned success.
- [ ] State remained `ACTIVE` (remove does not change state per spec).
- [ ] Follow-up `replay("greeting.hello")` returned `HostError: RESOLVER_STORE_MISS`.
- Save: `20-remove.log`.

## §21 · Error handling — 5-state vocabulary

Only `OFF`, `READY`, `ACTIVE`, `UNAVAILABLE`, `ERROR` may appear. If any other state name shows up, that is a plugin defect.

- [ ] **Missing bundled Node:** renamed `node[.exe]`, restarted IDE, invoked Show Status → state `ERROR`, balloon `RSM runtime unavailable: bundled Node binary not present`.
- [ ] **Child killed once:** `kill <pid>`, invoked tool → state `READY` (auto-restart fired). Second `kill <pid>`, invoked tool → state `ERROR` (cap enforced).
- [ ] **Manual restart:** `RSM: Restart Runtime` action → state `READY`, budget reset. Third `kill <pid>`, invoked tool → state `READY` again.
- [ ] **Source-store miss:** invoked `replay("does.not.exist")` → state stays `ACTIVE`, balloon warning shown, `idea.log` records the miss.
- [ ] **Permission denied:** invoked `register(...)` with a restrictive `--allow` → error propagated, state stays `ACTIVE`.
- Save: `21-error-handling.log`.

## §22 · Restart / shutdown

- [ ] Auto-restart cap: exactly one auto-restart per session (verified in §21 row 2).
- [ ] Manual restart resets the budget (verified in §21 row 3).
- [ ] Closed IDE from `File → Exit` → `pgrep -a node | grep rsm-agent` on the workstation → empty (no orphaned child).
- [ ] Shutdown latency ≤ ~2s in the normal path (SIGTERM); SIGKILL fallback only if child ignores SIGTERM.
- Save: `22-restart-shutdown.log`.

## §23 · Split Mode

Only run if a second host is available.

- [ ] Backend host: IntelliJ IDEA 2026.1+ with plugin installed per §13–§14 (using the backend's OS/arch ZIP).
- [ ] `<idea>/bin/remote-dev-server.sh run /path/to/project` on backend → prints join link.
- [ ] JetBrains Client on local host → `Connect via Join Link` → join.
- [ ] From Client, `Cmd/Ctrl+Shift+A` → `RSM: Show Runtime Status` → balloon appears in Client UI.
- [ ] On Backend host: `pgrep -a node | grep rsm-agent` → matches. On Client host: same command → empty.
- [ ] One full `register / replay / remove` round-trip from Client succeeds, with all execution on Backend.
- Save: `23-split-mode.log`.

## §24 · Gateway / Remote Backend

Only run if a remote SSH backend + JetBrains Gateway are available.

- [ ] Plugin installed on remote backend using the backend OS/arch ZIP (not the workstation's).
- [ ] JetBrains Gateway on workstation → SSH connect to backend → open project.
- [ ] From Gateway IDE, invoked `RSM: Show Runtime Status` → balloon appears.
- [ ] On backend: `pgrep -a node | grep rsm-agent` → matches. On workstation: same command → empty.
- [ ] Bundled Node on backend: `<plugin>/node-runtimes/<backend-target>/node --version` → `v22.23.3`.
- [ ] One full register / replay / remove round-trip via Gateway succeeds.
- [ ] Credentials referenced by source store remained server-side (nothing sensitive on the SSH channel beyond Gateway framing).
- Save: `24-gateway.log`.

## §26 · Final acceptance

- [ ] All boxes above checked (or explicitly `N/A — no second host available` for §23 / §24 with a written note).
- [ ] `RSM_JETBRAINS_READINESS.md` updated:
  - Status changed from `PARTIALLY READY — FINAL VERIFICATION PENDING` to `RSM + JetBrains Integration COMPLETE`.
  - `Final State` section appended per plan Appendix.
  - Cross-package total: **148/148**.
- [ ] Evidence directory `build/integration-evidence/` contains files `13-install.log` through `24-gateway.log`.

## Anything failed?

STOP. Read `EXTERNAL_INTEGRATION_PLAN.md` §25 Case B and classify the layer using the matrix there. Do **not** edit `rsm/`, `rsm-host/`, `rsm-agent/`, `rsm-cli/`, or `rsm-vscode/` before classification and explicit authorization. Write the classification and proposed correction into `build/integration-evidence/25-classification.md`.
