# RSM + JetBrains — End-to-End Trace Verification

**Scope.** This document replaces PASS/FAIL gate-thinking. The only meaningful correctness criterion for an integrated RSM ↔ JetBrains system is:

> **Every call originating in the IDE produces a fully-traced, end-to-end response — from the IDE process, through the bundled Node sidecar, through `rsm-agent`, over MCP/stdio, into `rsm-host`, into `rsm`, and back — with every hop visible in logs, with matched request/response identifiers, and with no missing segment on any hop.**

Anything else is either build hygiene (already covered by the frozen anchors in `FREEZE.md` and `RSM_JETBRAINS_READINESS.md`) or environmental noise. If a call cannot be traced end-to-end, the integration is not real — regardless of how many gates went green.

This document defines exactly what "fully traced" means, how to make each hop emit that trace, and how to reconstruct a single call's journey from the recorded evidence.

---

## 1. The call graph — every hop that must trace

```
┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│   IDE process    │  H1 ──▶ │  RsmRuntime      │  H2 ──▶ │ ProcessLauncher  │
│  (Kotlin action  │         │  Service         │         │  (spawns Node)   │
│   or dev API)    │  ◀── H8 │  (state machine) │  ◀── H7 │                  │
└──────────────────┘         └──────────────────┘         └────────┬─────────┘
                                                                   │
                                                            H3 spawn/stdio
                                                                   ▼
                                                          ┌──────────────────┐
                                                          │  Bundled Node    │
                                                          │  v22.23.3        │
                                                          │  process         │
                                                          └────────┬─────────┘
                                                                   │
                                                            H4 stdio pipes
                                                                   ▼
                                                          ┌──────────────────┐
                                                          │   rsm-agent      │
                                                          │  (MCP server)    │
                                                          └────────┬─────────┘
                                                                   │
                                                            H5 in-process call
                                                                   ▼
                                                          ┌──────────────────┐
                                                          │   rsm-host       │
                                                          │  (perms + store) │
                                                          └────────┬─────────┘
                                                                   │
                                                            H6 in-process call
                                                                   ▼
                                                          ┌──────────────────┐
                                                          │      rsm         │
                                                          │  (register/      │
                                                          │   replay/remove) │
                                                          └──────────────────┘
```

Eight hops. A fully-traced call emits exactly one line per hop, tagged with the same **correlation id**, in temporal order — request phase H1 → H6, response phase H6 → H1. Any missing hop is a diagnostic gap that must be closed before promoting the integration.

---

## 2. The correlation id

Every call gets a single **correlation id** (an opaque UUID or ULID string) that:

- is minted at H1 by the IDE-side caller (typically `RsmRuntimeService.<verb>`),
- is passed as the MCP JSON-RPC `id` field (or attached in `_meta` if `id` is already used by the transport layer),
- appears verbatim in every log line, every trace entry, and every error return along the eight hops.

If a hop cannot log the id it was given, that hop's tracing is incomplete — fix that hop before proceeding.

**Naming.** Use `rsm.callId` in every logger. Do not invent per-layer synonyms (`requestId`, `traceId`, `mcp.id`) — one name in one place. Where the MCP protocol requires a separate JSON-RPC `id`, `rsm.callId` is the source and the JSON-RPC id is either the same string or a wrapper that logs `rsm.callId` alongside it.

---

## 3. What every hop must record

For every call, each hop writes exactly two log entries (request phase, response phase). Missing either half means the hop did not trace the call.

| Hop | Layer | Phase | Log line contract |
|---|---|---|---|
| H1 | IDE action → RsmRuntimeService | request | `[#rsm.jetbrains:INFO] call.begin rsm.callId=<id> verb=<register\|replay\|remove> args=<compact json>` |
| H1 | IDE action → RsmRuntimeService | response | `[#rsm.jetbrains:INFO] call.end rsm.callId=<id> outcome=<ok\|err> stateAfter=<OFF\|READY\|ACTIVE\|UNAVAILABLE\|ERROR> durationMs=<n>` |
| H2 | RsmRuntimeService → ProcessLauncher | request | `[#rsm.jetbrains:DEBUG] launch.request rsm.callId=<id> agentPath=<abs> nodePath=<abs>` |
| H2 | RsmRuntimeService → ProcessLauncher | response | `[#rsm.jetbrains:DEBUG] launch.ack rsm.callId=<id> pid=<n>` (only on first launch or restart) |
| H3 | ProcessLauncher → Node child | request | `[#rsm.jetbrains:DEBUG] spawn rsm.callId=<id> argv=<...> envKeysCount=<n>` (envKeysCount only — no values) |
| H3 | ProcessLauncher → Node child | response | `[#rsm.jetbrains:DEBUG] spawn.ready rsm.callId=<id> pid=<n>` |
| H4 | Node child ↔ rsm-agent (MCP stdio) | request | rsm-agent stderr: `mcp.request id=<jsonrpc-id> rsm.callId=<id> method=tools/call name=<tool>` |
| H4 | Node child ↔ rsm-agent (MCP stdio) | response | rsm-agent stderr: `mcp.response id=<jsonrpc-id> rsm.callId=<id> outcome=<ok\|err> durationMs=<n>` |
| H5 | rsm-agent → rsm-host | request | rsm-agent stderr: `host.enter rsm.callId=<id> tool=<name>` |
| H5 | rsm-agent → rsm-host | response | rsm-agent stderr: `host.leave rsm.callId=<id> outcome=<ok\|err> errKind=<PERMISSION_DENIED\|RESOLVER_STORE_MISS\|none>` |
| H6 | rsm-host → rsm | request | rsm-agent stderr: `rsm.enter rsm.callId=<id> op=<register\|replay\|remove> sourceId=<id-or-omitted>` |
| H6 | rsm-host → rsm | response | rsm-agent stderr: `rsm.leave rsm.callId=<id> outcome=<ok\|err> bytes=<n-if-replay>` |
| H7 | rsm-agent → Node child ↔ ProcessLauncher | response | (already covered by H4 response — same log line) |
| H8 | ProcessLauncher → RsmRuntimeService → IDE | response | (already covered by H1 response — same log line) |

H7 and H8 have no dedicated log — they are the same event as H4 response and H1 response respectively (nothing else happens on those wires). Six hops, twelve distinct log entries per call.

The rsm-agent log lines land on **rsm-agent's stderr** because MCP owns rsm-agent's stdout for protocol traffic. RsmRuntimeService pipes that stderr into `idea.log` under `#rsm.jetbrains` (see §5.2 for the plumbing).

---

## 4. What "fully traced" means — the two invariants

A call is fully traced when both invariants hold. Neither is a subjective pass/fail — they are structural properties of the log stream.

### Invariant A — every hop has both halves

For a given `rsm.callId`, `grep` across the collected logs must produce **exactly the 12 distinct log entries listed in §3**, in temporal order. Fewer means a hop is silent; more means a hop is chatty (log noise is not a defect, but audit the extras — they may be retries or unauthorized retransmissions).

### Invariant B — matched request/response identifiers on every layer boundary

At every layer boundary, request and response carry the same `rsm.callId`. Concretely:

- H1 request `rsm.callId` == H1 response `rsm.callId`.
- H4 request `rsm.callId` == H4 response `rsm.callId`.
- H4 request `rsm.callId` == H5 request `rsm.callId`.
- H5 request `rsm.callId` == H6 request `rsm.callId`.
- H4 request `id` (JSON-RPC) == H4 response `id` (JSON-RPC) — MCP protocol invariant.

If any boundary shows a mismatched id, the layer at that boundary dropped or replaced the correlation. That layer is where the diagnostic gap is.

Neither invariant needs a "PASS" or "FAIL" stamp. They are true or false statements about a text file. The verification is to run `grep` and read.

---

## 5. Making the trace real — the wiring each layer needs

Everything below is either already in the frozen source or must be added as a **narrowly-scoped, evidence-backed correction** (per plan §14/§20/§26) before the trace is reproducible. Do not add anything else at the same time.

### 5.1 IDE side — `RsmRuntimeService`

- Mint `rsm.callId` at the entry of every `register` / `replay` / `remove` method.
- Emit `call.begin` immediately after minting, `call.end` in a `finally` block after the response arrives (or an exception propagates).
- Pass `rsm.callId` down to `McpStdioClient.callTool(..., rsm.callId)`; the client must inject it into the MCP request's `_meta.rsm_call_id` field so the agent can echo it back.
- On state transitions, log a separate `state` line with the current `rsm.callId` if the transition was caused by a call, or without one if it was an idle transition (startup, external kill).

Frozen source status: `RsmLogger.kt` exists; the log-line format above may need to be tightened. Verify by reading the current lines and, if any of the twelve are missing, propose the smallest change that adds them.

### 5.2 IDE side — `ProcessLauncher`

- The launcher already pipes the child's stdout to `McpStdioClient` (protocol channel) and its stderr to a logger. Confirm that logger is `#rsm.jetbrains` and that lines are forwarded verbatim so rsm-agent's H4/H5/H6 lines appear in `idea.log`.
- If today it swallows stderr or logs at TRACE only, raise it to INFO (or route it through the `#rsm.jetbrains` category unconditionally). This is the single most common source of missing hops.

### 5.3 Node side — `rsm-agent`

- Read `_meta.rsm_call_id` on every incoming MCP request. If absent, mint a local id and log `rsm.callId=<generated>` with a `source=local` tag — that call is not correlated end-to-end and the invariant catches it.
- Emit H4, H5, H6 request+response lines on stderr with the exact `rsm.callId=<id>` token. Do NOT emit them on stdout — stdout is MCP protocol territory.
- Include `durationMs` on H4 response and `bytes` on H6 response when the op is `replay`, so the recorded evidence lets you compare cost per hop.

### 5.4 rsm-host

- Accept an optional `rsm.callId` from the calling layer (rsm-agent passes it in via the same permission-authority call context).
- Log H5 entry and exit with the id. On any `HostError`, include `errKind` — the only two authorized kinds are `PERMISSION_DENIED` and `RESOLVER_STORE_MISS`. A third kind appearing in a log line is itself the defect.

### 5.5 rsm

- Accept an optional `rsm.callId` in the register/replay/remove entry points.
- Log H6 entry and exit. rsm knows the source id, so `sourceId` appears in the entry line for register / replay / remove.

---

## 6. Evidence collection — one recipe per environment

Every verification, from the smallest smoke to the full Split Mode round-trip, follows the same three-step recipe:

1. **Prepare.** Fresh IDE start with `#rsm.jetbrains` enabled at INFO (§14 of the main plan). Rotate `idea.log`. `pgrep -a node` should be empty.
2. **Invoke.** Trigger exactly one call (or one small sequence — REGISTER, REPLAY, REMOVE). Note the wall-clock time.
3. **Collect and reconstruct.** Copy `idea.log` and any rsm-agent stderr capture; grep for the `rsm.callId` values; reconstruct the 12-line trace per call.

### 6.1 Local mode

```bash
# Prepare
tail -F "$IDEA_LOG" &            # or open Help → Show Log
TAIL_PID=$!

# Invoke — from within the IDE, run a Kotlin scratch:
#   val svc = ApplicationManager.getApplication().getService(RsmRuntimeService::class.java)
#   println(svc.register(mapOf("source_id" to "greeting.hello")))
#   println(svc.replay("greeting.hello"))
#   println(svc.remove("greeting.hello"))

# Collect
kill $TAIL_PID
# Extract every call's trace:
grep -E 'rsm\.callId=' "$IDEA_LOG" | awk -F'rsm.callId=' '{print $2}' \
  | awk '{print $1}' | sort -u > /tmp/callids.txt

for id in $(cat /tmp/callids.txt); do
  echo "=== $id ==="
  grep "rsm.callId=$id" "$IDEA_LOG"
  echo
done > "build/integration-evidence/trace-local.log"
```

Then read `build/integration-evidence/trace-local.log`. For each id, count distinct log line prefixes:

```
call.begin, call.end,
launch.request, launch.ack,           (only on first call in the session)
spawn, spawn.ready,                   (only on first call in the session)
mcp.request, mcp.response,
host.enter, host.leave,
rsm.enter, rsm.leave
```

Subsequent calls skip launch/spawn (the child is already running). Their expected line count is 8, not 12 — a real invariant check tolerates this.

### 6.2 Split Mode

- Prepare on **backend host**: `#rsm.jetbrains` at INFO, `idea.log` located, `pgrep -a node` empty.
- On **client host**: separately confirm `pgrep -a node` is empty and stays empty — the client must never spawn Node.
- Invoke from the client UI as in §6.1.
- Collect the backend `idea.log`. All 12 lines per call must appear there. The client host contributes zero rsm-agent lines — that is the backend-only invariant expressed as a trace invariant.

### 6.3 Gateway / Remote Backend

- Same as Split Mode. All 12 lines land on the remote backend's `idea.log`. Zero lines on the workstation.
- Additionally, the SSH channel carries only Gateway frames — verify by tcpdump'ing the SSH port if the environment permits and confirming no plaintext MCP frames.

---

## 7. Reading a trace — the six diagnostic patterns

When a trace is incomplete, the missing entries name the layer that dropped the correlation. There are exactly six shapes.

| Missing entries | Diagnosis |
|---|---|
| Only `call.begin`; no `call.end` | IDE-side exception before return; check `idea.log` for a stack trace at the same timestamp. |
| `call.begin`, no `launch.request`, no `spawn` | Sidecar was already running (normal on 2nd+ call). Not a defect. |
| `call.begin`, `launch.request`, but no `spawn.ready` | ProcessLauncher spawned Node but the child never signaled ready → **rsm-agent boot failure**. Check stderr for a JS load error. |
| `spawn.ready` but no `mcp.request` | MCP `initialize` handshake never completed → **McpStdioClient.initialize** hung or errored. |
| `mcp.request` but no `host.enter` | rsm-agent received the tool call but did not enter `rsm-host` → **rsm-agent tool dispatch** dropped it. Compare the tool name against the frozen allow-list `{rsm_register, rsm_replay, rsm_remove}`. |
| `host.enter` but no `rsm.enter` | rsm-host rejected the call before entering rsm → **PermissionAuthority denied** (expected in the permission-denied test) or `SourceStore` miss (expected in the miss test). Verify `host.leave` reports the correct `errKind`. |
| `rsm.enter` but no `rsm.leave` | rsm hung inside the operation → **rsm** defect. Reproduce with `rsm-cli` directly to isolate. |
| Complete trace, but response `outcome=err` and `errKind` outside the authorized two | **rsm-host** emitted an unauthorized error kind — this is the defect regardless of what triggered it. |

None of these is a PASS or a FAIL — each is a specific diagnostic pointer. The recorded trace tells you which file to open next.

---

## 8. Correlation across restarts

The `at-most-one-restart-per-session` invariant complicates trace correlation because a call may span a child-process restart. Handle it this way:

- The `rsm.callId` is minted **before** the restart decision. If auto-restart fires, the retry uses the same `rsm.callId`.
- H2 emits `launch.request` again (with the same `rsm.callId`); H3 emits `spawn` again with a **new `pid`**. Both lines are correctly correlated to the caller.
- H4 request is emitted a second time with the same `rsm.callId` — expect duplicates in the trace stream. Count them; if there are more than two `mcp.request` lines for one `rsm.callId`, the restart cap is broken. That is a plugin state-machine defect.

The trace itself becomes the evidence for the restart-cap invariant — no separate test is needed. This is why the trace verification subsumes error-handling and lifecycle tests.

---

## 9. What this replaces from the earlier documents

- The **§24 15-gate matrix** in `EXTERNAL_INTEGRATION_PLAN.md` — replaced by the two invariants in §4 above. Gates are structural (build succeeded, artifact matches SHA-256) and remain useful as *build* checks, but they say nothing about whether calls trace. Keep them as build hygiene; do not treat them as integration correctness.
- The **§18–§22 individual operation verifications** in `EXTERNAL_INTEGRATION_PLAN.md` — replaced by the single recipe in §6. One traced call proves every hop; N operations proves N applications of the same invariant.
- The **§23–§24 backend-only checks** — replaced by §6.2 and §6.3 (trace lines land on the backend host, zero on the client).
- The **§21 error-handling matrix** — subsumed by §7's diagnostic-pattern table. Every failure mode maps to a missing-line pattern.
- The **§22 restart/shutdown proof** — subsumed by §8. The trace records the cap.

The rest of `EXTERNAL_INTEGRATION_PLAN.md` (build wrapper, task discovery, native variants, artifact integrity) is unchanged — those are pre-integration hygiene, not integration correctness.

---

## 10. What "done" looks like

Two things:

1. **Every hop wired.** For a single local `register/replay/remove` sequence, the trace file produced by §6.1 contains all 12 distinct line prefixes for at least one `rsm.callId`, and every layer boundary shows matched ids per Invariant B.
2. **Every deployment mode produces the same trace on the correct host.** Local → workstation's `idea.log`. Split Mode → backend's `idea.log`, empty client. Gateway → remote backend's `idea.log`, empty workstation.

That is the whole criterion. It is small, physical, and testable by reading a text file. It is invariant across runs, across workstations, and across IntelliJ versions — a correct integration produces the trace; a broken one leaves a gap that names its layer.

Update `RSM_JETBRAINS_READINESS.md`'s status to `RSM + JetBrains Integration COMPLETE` only after §10.1 and §10.2 above are both true, with the trace files attached in `build/integration-evidence/`. Nothing else is required.
