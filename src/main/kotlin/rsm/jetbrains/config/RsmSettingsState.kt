package rsm.jetbrains.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.concurrent.atomic.AtomicReference

/**
 * Application-level, non-roaming settings for the RSM plugin. Backed by the
 * IntelliJ Platform's PersistentStateComponent.
 */
@State(
    name = "RsmSettings",
    storages = [Storage(value = "rsm.xml", roamingType = RoamingType.DISABLED)]
)
class RsmSettingsState : PersistentStateComponent<RsmSettingsState.Data> {

    /** Mutable value object owned by the platform's serializer. */
    class Data {
        var autoStart: Boolean          = true
        var autoRestart: Boolean        = true
        var memoryTier: String          = "auto"
        var sourceStoreSpec: String     = ""
        var allowList: MutableList<String> = mutableListOf()
        var allowAll: Boolean           = false
        var autoFeed: Boolean           = true
        var traceRetentionDays: Int     = 30
        var logLevel: String            = "INFO"
        var folderMaxDepth: Int         = 8
        var folderMaxFiles: Int         = 500

        fun copy(): Data {
            val d = Data()
            d.autoStart          = autoStart
            d.autoRestart        = autoRestart
            d.memoryTier         = memoryTier
            d.sourceStoreSpec    = sourceStoreSpec
            d.allowList          = allowList.toMutableList()
            d.allowAll           = allowAll
            d.autoFeed           = autoFeed
            d.traceRetentionDays = traceRetentionDays
            d.logLevel           = logLevel
            d.folderMaxDepth     = folderMaxDepth
            d.folderMaxFiles     = folderMaxFiles
            return d
        }
    }

    private val dataRef = AtomicReference(Data())

    fun data(): Data = dataRef.get()
    fun replace(newData: Data) { dataRef.set(newData) }

    override fun getState(): Data? = dataRef.get()
    override fun loadState(state: Data) { dataRef.set(state) }
}
