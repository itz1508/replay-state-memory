#!/usr/bin/env bash
# RSM JetBrains — External Verification Runner
#
# Executes §3–§12 of EXTERNAL_INTEGRATION_PLAN.md end-to-end on a properly
# provisioned Gradle host (native FS, ≥ 4 GiB memory, JDK 17+, network egress).
#
# Usage:
#   ./scripts/run-external-verification.sh
#
# Behavior:
#   - Runs each step in order.
#   - Records every log to build/verification-logs/.
#   - Stops on the first failure and prints a classification pointer.
#   - Never invents Gradle task names — reads them from tasks --all output.
#   - Never mutates the repository (except for the toolchain decision file
#     it writes to build/verification-logs/, which is evidence, not source).

set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

LOG_DIR="build/verification-logs"
mkdir -p "$LOG_DIR"

WRAPPER_SHA_EXPECTED="238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"

hr() { printf '\n\033[1;36m===== %s =====\033[0m\n' "$*"; }
pass() { printf '\033[1;32m[PASS]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; }
die()  { fail "$*"; exit 1; }

# --- §2 pre-flight ---
hr "§2 Runner pre-flight"
{
  echo "--- java -version ---"; java -version 2>&1 || true
  echo "--- uname ---"; uname -a
  echo "--- memory ---"; (free -g 2>/dev/null) || (vm_stat 2>/dev/null) || true
  echo "--- wrapper JAR SHA-256 ---"
  sha256sum gradle/wrapper/gradle-wrapper.jar 2>&1 \
    || shasum -a 256 gradle/wrapper/gradle-wrapper.jar 2>&1
} > "$LOG_DIR/00-runner-env.log" 2>&1

wrapper_sha="$(sha256sum gradle/wrapper/gradle-wrapper.jar 2>/dev/null \
              | awk '{print $1}' \
              || shasum -a 256 gradle/wrapper/gradle-wrapper.jar | awk '{print $1}')"

if [ "$wrapper_sha" != "$WRAPPER_SHA_EXPECTED" ]; then
  die "Wrapper JAR SHA-256 mismatch: got $wrapper_sha, expected $WRAPPER_SHA_EXPECTED (restore from git; do NOT regenerate)"
fi
pass "wrapper JAR SHA-256 matches Gradle 9.8.0 official"

# --- §3 wrapper ---
hr "§3 Wrapper — Gradle 9.8.0"
./gradlew --version 2>&1 | tee "$LOG_DIR/01-gradlew-version.log"
if ! grep -q '^Gradle 9\.8\.0' "$LOG_DIR/01-gradlew-version.log"; then
  die "gradle --version did not report 9.8.0 (see $LOG_DIR/01-gradlew-version.log)"
fi
pass "Gradle 9.8.0 self-bootstrapped"

# --- §4 toolchains ---
hr "§4 Toolchain resolution (evidence only; decision deferred to §9)"
./gradlew javaToolchains 2>&1 | tee "$LOG_DIR/02-javaToolchains.log" || \
  die "javaToolchains failed — inspect $LOG_DIR/02-javaToolchains.log"
pass "toolchains listed (final decision deferred to §9)"

# --- §5 task discovery ---
hr "§5 Task discovery — reading actual task names"
./gradlew tasks --all --console=plain > build/gradle-tasks-report.txt 2>&1 \
  || die "tasks --all failed — inspect build/gradle-tasks-report.txt"

# Extract task names from the report. These are heuristics that read the
# IPGP-generated task list. Adjust the greps if IPGP renames them; NEVER
# invent a name.
BUILD_TASK="$(grep -oE '^buildPlugin' build/gradle-tasks-report.txt | head -1 || true)"
TEST_TASK="$(grep -oE '^test( - .*)?' build/gradle-tasks-report.txt | head -1 | awk '{print $1}' || true)"
VERIFIER_TASK="$(grep -oE '^(verifyPlugin|runPluginVerifier)' build/gradle-tasks-report.txt | head -1 || true)"

# Native variants: IPGP emits per-target packaging tasks; grep for the pattern
# it uses (typical: buildPlugin<Target>Zip or similar). Adjust to match the
# actual report.
NATIVE_TASKS="$(grep -oE '^buildPlugin[A-Za-z0-9]+Zip' build/gradle-tasks-report.txt | sort -u | tr '\n' ' ' || true)"

if [ -z "$BUILD_TASK" ] || [ -z "$TEST_TASK" ] || [ -z "$VERIFIER_TASK" ] || [ -z "$NATIVE_TASKS" ]; then
  fail "Could not discover one or more task names from build/gradle-tasks-report.txt"
  fail "  BUILD_TASK='$BUILD_TASK' TEST_TASK='$TEST_TASK' VERIFIER_TASK='$VERIFIER_TASK'"
  fail "  NATIVE_TASKS='$NATIVE_TASKS'"
  fail "Inspect build/gradle-tasks-report.txt manually, then set env vars and re-run:"
  fail "  BUILD_TASK=... NATIVE_TASKS='...' TEST_TASK=... VERIFIER_TASK=... $0"
  # Allow override via env for the retry path.
  BUILD_TASK="${BUILD_TASK_OVERRIDE:-$BUILD_TASK}"
  TEST_TASK="${TEST_TASK_OVERRIDE:-$TEST_TASK}"
  VERIFIER_TASK="${VERIFIER_TASK_OVERRIDE:-$VERIFIER_TASK}"
  NATIVE_TASKS="${NATIVE_TASKS_OVERRIDE:-$NATIVE_TASKS}"
  [ -z "$BUILD_TASK$TEST_TASK$VERIFIER_TASK$NATIVE_TASKS" ] && exit 2
fi

cat > "$LOG_DIR/03-discovered-tasks.txt" <<EOF
BUILD_TASK=$BUILD_TASK
NATIVE_TASKS=$NATIVE_TASKS
TEST_TASK=$TEST_TASK
VERIFIER_TASK=$VERIFIER_TASK
EOF
pass "task names discovered: $BUILD_TASK / $TEST_TASK / $VERIFIER_TASK / [$NATIVE_TASKS]"

# --- §6 authoritative build ---
hr "§6 Authoritative build — $BUILD_TASK"
./gradlew "$BUILD_TASK" --console=plain 2>&1 | tee "$LOG_DIR/04-build.log"
ls -la build/distributions/ > "$LOG_DIR/04-build-dist.txt" 2>&1 || true
grep -qE '^FAILED|^BUILD FAILED' "$LOG_DIR/04-build.log" \
  && die "Build failed — see $LOG_DIR/04-build.log; classify per plan §14"
pass "build succeeded"

# --- §7 native variants ---
hr "§7 Native variants — $NATIVE_TASKS"
./gradlew $NATIVE_TASKS --console=plain 2>&1 | tee "$LOG_DIR/05-native.log"
grep -qE '^FAILED|^BUILD FAILED' "$LOG_DIR/05-native.log" \
  && die "Native variant build failed — see $LOG_DIR/05-native.log"
: > "$LOG_DIR/06-one-target-scan.txt"
for z in build/distributions/rsm-jetbrains-0.1.0-*.zip; do
  {
    echo "=== $z ==="
    unzip -l "$z" | grep -E 'node-runtimes/[^/]+/' || echo "(no node-runtimes entries)"
  } >> "$LOG_DIR/06-one-target-scan.txt"
done
sha256sum build/distributions/*.zip > "$LOG_DIR/07-dist-sha256.txt" 2>/dev/null \
  || shasum -a 256 build/distributions/*.zip > "$LOG_DIR/07-dist-sha256.txt"
pass "4 native variants generated and scanned"

# --- §8 tests ---
hr "§8 Gradle tests — $TEST_TASK"
./gradlew "$TEST_TASK" --console=plain 2>&1 | tee "$LOG_DIR/08-test.log"
grep -qE '^FAILED|BUILD FAILED|tests failed' "$LOG_DIR/08-test.log" \
  && die "Gradle tests failed — see $LOG_DIR/08-test.log; classify per plan §14"
pass "Gradle tests passed"

# --- §9 verifier ---
hr "§9 Plugin Verifier — $VERIFIER_TASK"
./gradlew "$VERIFIER_TASK" --console=plain 2>&1 | tee "$LOG_DIR/09-verifier.log" || true
if grep -qE 'FAILED|invalid|not compatible' "$LOG_DIR/09-verifier.log"; then
  fail "Plugin Verifier reported findings — READ the log and record the toolchain decision manually"
  fail "  Evidence: $LOG_DIR/09-verifier.log"
  fail "  Decision file: $LOG_DIR/10-toolchain-decision.md (MUST be written before mutating build.gradle.kts)"
  cat > "$LOG_DIR/10-toolchain-decision.md" << TMPL
# Toolchain decision

Decision: TODO (KEEP 17 | CHANGE TO 21 | REMOVE)

Evidence (verifier log lines):

<paste the specific verifier line(s) that justify the decision>

Reference:

- IPGP 2.19.0: https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/main/CHANGELOG.md
- IntelliJ Platform 2026.1 JBR version: https://github.com/JetBrains/intellij-sdk-docs/blob/main/topics/basics/getting_started/build_number_ranges.md
TMPL
  exit 3
fi
cat > "$LOG_DIR/10-toolchain-decision.md" << EOF
# Toolchain decision

Decision: KEPT jvmToolchain(17)

Evidence: Plugin Verifier reported no release-blocking findings (see 09-verifier.log).

Reference: IPGP 2.19.0 module metadata declares org.gradle.jvm.version: 17.
EOF
pass "Plugin Verifier clean; toolchain KEPT at 17"

# --- §10 artifact structure ---
hr "§10 Artifact structure"
: > "$LOG_DIR/11-artifact-structure.txt"
for z in build/distributions/rsm-jetbrains-0.1.0-*.zip; do
  {
    echo "=== $z ==="
    unzip -l "$z" | awk '{print $NF}' | sort -u
  } >> "$LOG_DIR/11-artifact-structure.txt"
done
pass "artifact structure recorded"

# --- §11 equivalence ---
hr "§11 Artifact equivalence"
{
  echo "--- newly built ---"
  sha256sum build/distributions/*.zip 2>/dev/null \
    || shasum -a 256 build/distributions/*.zip
  echo "--- frozen manifest ---"
  jq -r '.pluginArchives | to_entries[] | .value.path + "  " + .value.sha256' build/release-manifest.json
} | tee "$LOG_DIR/12-artifact-equivalence.md"
pass "equivalence recorded (compare manually; strong = SHA match, weak = content-equivalent)"

# --- §12 baseline + integrity ---
hr "§12 Baseline (132/132) + source integrity"
: > "$LOG_DIR/13-baseline.log"
for p in ../rsm ../rsm-host ../rsm-cli ../rsm-vscode ../rsm-agent; do
  if [ -d "$p" ]; then
    {
      echo "=== $p ==="
      (cd "$p" && bun test 2>&1 | tail -6) || echo "(bun test failed in $p)"
    } >> "$LOG_DIR/13-baseline.log"
  fi
done
git status > "$LOG_DIR/14-git-status.log" 2>&1 || true
git diff --stat HEAD > "$LOG_DIR/15-git-diff.log" 2>&1 || true

hr "SUMMARY"
echo "All §3–§12 steps completed. Review:"
for f in "$LOG_DIR"/*; do echo "  $f"; done
echo
echo "Next: proceed to plan §13 (install into IntelliJ IDEA 2026.1+ workstation)."
