package rsm.jetbrains.logging

import rsm.jetbrains.platform.Logger

/**
 * Thin facade over com.intellij.openapi.diagnostic.Logger.
 * Category is fixed to "#rsm.jetbrains" per plugin convention.
 */
object RsmLogger {
    private val log = Logger.getInstance("#rsm.jetbrains")
    fun get(): Logger = log
}
