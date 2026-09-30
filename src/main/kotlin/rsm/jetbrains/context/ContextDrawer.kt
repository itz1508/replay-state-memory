package rsm.jetbrains.context

import rsm.jetbrains.config.RsmHome
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One entry in the drawer. The `sourceId` returned by `rsm_register` is the
 * reference verbatim (see rsm-host/src/host/install.ts:47). We keep the display
 * name separate so the UI never has to parse the reference.
 */
data class DrawerEntry(
    val sourceId: String,
    val displayName: String,
    val reference: String,
    val stagedPath: File,
    val sizeBytes: Long,
    val kind: Kind,
    val attachedAt: Instant,
    val runIds: MutableList<String> = mutableListOf(),
    var lastReplayStatus: ReplayStatus = ReplayStatus.NEVER,
    var lastReplayAt: Instant? = null,
    var orphaned: Boolean = false,
) {
    enum class Kind { FILE, FOLDER_MEMBER }
    enum class ReplayStatus { NEVER, OK, UNAVAILABLE }
}

/**
 * Per-IDE-launch UI ledger of attached sources. NOT a backend concept — the
 * sidecar knows only the flat set of source_ids via rsm_register/rsm_remove.
 * See PLANS/RSM_JETBRAINS_0_2_0_PLAN.md §2.8 for the design rationale.
 */
class ContextDrawer {
    private val entries = CopyOnWriteArrayList<DrawerEntry>()
    private val listeners = CopyOnWriteArrayList<(List<DrawerEntry>) -> Unit>()

    // ---- Attachment lifecycle ------------------------------------------------

    /**
     * Stage a file into the fs-store root and return the reference (== filename)
     * that should be passed to `rsm_register`. Does NOT call rsm-agent; the caller
     * (RsmRuntimeService) does that so it can capture the trace record.
     *
     * @param file           Existing file the user picked.
     * @param preferHardlink If true, try `Files.createLink` before falling back to copy.
     */
    fun stageFile(file: File, preferHardlink: Boolean = true): DrawerEntry {
        require(file.isFile) { "not a regular file: $file" }
        val root: Path = RsmHome.sourcesDir().toPath()
        Files.createDirectories(root)

        val safeName = disambiguate(root, sanitize(file.name))
        val target: Path = root.resolve(safeName)

        if (preferHardlink) {
            try {
                Files.createLink(target, file.toPath())
            } catch (_: Throwable) {
                Files.copy(file.toPath(), target, StandardCopyOption.COPY_ATTRIBUTES)
            }
        } else {
            Files.copy(file.toPath(), target, StandardCopyOption.COPY_ATTRIBUTES)
        }

        val entry = DrawerEntry(
            sourceId = safeName,          // rsm-host makes source_id == reference
            displayName = file.name,
            reference = safeName,
            stagedPath = target.toFile(),
            sizeBytes = Files.size(target),
            kind = DrawerEntry.Kind.FILE,
            attachedAt = Instant.now(),
        )
        entries.add(entry)
        fireListeners()
        return entry
    }

    /**
     * Stage every regular file inside `folder` (recursive) as an independent entry.
     * Enforces max depth and max file count per plan §3 risk register.
     * Returns the list of staged entries (may be shorter than requested when clamped).
     */
    fun stageFolder(folder: File, maxDepth: Int, maxFiles: Int): List<DrawerEntry> {
        require(folder.isDirectory) { "not a directory: $folder" }
        val staged = mutableListOf<DrawerEntry>()
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(folder to 0)
        outer@ while (stack.isNotEmpty()) {
            val (dir, depth) = stack.removeFirst()
            if (depth > maxDepth) continue
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child to depth + 1)
                } else if (child.isFile) {
                    if (staged.size >= maxFiles) break@outer
                    val e = stageFile(child)
                    val updated = e.copy(kind = DrawerEntry.Kind.FOLDER_MEMBER)
                    val idx = entries.indexOfFirst { it.sourceId == e.sourceId }
                    if (idx >= 0) entries[idx] = updated
                    staged.add(updated)
                }
            }
        }
        fireListeners()
        return staged
    }

    /**
     * Called after `rsm_remove` succeeds. Moves the file to
     * `storage/registrations/history/` (never a hard delete) so "Replay Last Run"
     * can re-attach it if Auto Feed cleaned it up.
     */
    fun detach(sourceId: String, runId: String? = null): DrawerEntry? {
        val idx = entries.indexOfFirst { it.sourceId == sourceId }
        if (idx < 0) return null
        val e = entries[idx]
        val historyDir = RsmHome.registrationsDir().resolve("history")
        Files.createDirectories(historyDir.toPath())
        val moved = historyDir.resolve(e.stagedPath.name).toPath()
        try {
            Files.move(e.stagedPath.toPath(), moved, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Throwable) {
            // If move fails, leave the file in place and flag orphan; UI can offer purge.
            entries[idx] = e.copy(orphaned = true)
            fireListeners()
            return entries[idx]
        }
        entries.removeAt(idx)
        val copy = e.copy(stagedPath = moved.toFile(), orphaned = false)
        if (runId != null) copy.runIds.add(runId)
        fireListeners()
        return copy
    }

    // ---- State updates -------------------------------------------------------

    /** Mark that a replay has just completed for this source_id. */
    fun onReplayResult(sourceId: String, ok: Boolean) {
        val idx = entries.indexOfFirst { it.sourceId == sourceId }
        if (idx < 0) return
        val e = entries[idx]
        entries[idx] = e.copy().apply {
            lastReplayStatus = if (ok) DrawerEntry.ReplayStatus.OK
                               else DrawerEntry.ReplayStatus.UNAVAILABLE
            lastReplayAt = Instant.now()
        }
        fireListeners()
    }

    /** Tag every current entry with a run id. Called by RunTracker when a run starts. */
    fun tagRun(runId: String) {
        for (e in entries) e.runIds.add(runId)
    }

    // ---- Introspection -------------------------------------------------------

    fun list(): List<DrawerEntry> = entries.toList()
    fun contextBytes(): Long = entries.sumOf { it.sizeBytes }
    fun size(): Int = entries.size

    // ---- Listeners -----------------------------------------------------------

    fun addListener(l: (List<DrawerEntry>) -> Unit) { listeners.add(l) }
    fun removeListener(l: (List<DrawerEntry>) -> Unit) { listeners.remove(l) }
    private fun fireListeners() {
        val snapshot = list()
        for (l in listeners) try { l(snapshot) } catch (_: Throwable) { }
    }

    // ---- Internals -----------------------------------------------------------

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "attachment" }

    private fun disambiguate(root: Path, name: String): String {
        val base = name
        var n = 0
        var candidate = base
        while (Files.exists(root.resolve(candidate), LinkOption.NOFOLLOW_LINKS)) {
            n += 1
            candidate = if (base.contains(".")) {
                val i = base.lastIndexOf(".")
                base.substring(0, i) + "-$n" + base.substring(i)
            } else "$base-$n"
        }
        return candidate
    }
}
