package com.alkisstam.sidebar

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import java.util.concurrent.CopyOnWriteArraySet

internal object SidebarMediaAccess {
    private val observers = CopyOnWriteArraySet<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())

    fun component(context: Context) = ComponentName(context, SidebarMediaNotificationListener::class.java)

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return manager.isNotificationListenerAccessGranted(component(context))
        }
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component(context) }
    }

    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return
            } catch (_: android.content.ActivityNotFoundException) { }
        }
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun addObserver(observer: () -> Unit) { observers.add(observer) }
    fun removeObserver(observer: () -> Unit) { observers.remove(observer) }
    fun notifyChanged() { handler.post { observers.forEach { it() } } }
}

// Grants access to MediaSessionManager after the user opts in. Notification contents
// are never read or stored; the player subscribes directly to selected media sessions.
class SidebarMediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        SidebarMediaAccess.notifyChanged()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        SidebarMediaAccess.notifyChanged()
    }
}
