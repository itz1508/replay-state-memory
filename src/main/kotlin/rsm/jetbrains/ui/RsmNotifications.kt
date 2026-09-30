package rsm.jetbrains.ui

import rsm.jetbrains.platform.NotificationsBus

/**
 * Thin facade over com.intellij.notification.Notifications.Bus.
 * All notifications post to the "rsm.notifications" group declared in plugin.xml.
 */
object RsmNotifications {
    private const val GROUP = "rsm.notifications"
    fun info(title: String, content: String)    = NotificationsBus.notify(GROUP, title, content, "INFO")
    fun warning(title: String, content: String) = NotificationsBus.notify(GROUP, title, content, "WARNING")
    fun error(title: String, content: String)   = NotificationsBus.notify(GROUP, title, content, "ERROR")
}
