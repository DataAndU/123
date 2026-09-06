package com.minijarvis.app.system

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.minijarvis.app.core.MiniJarvisApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Optional module: with the user's explicit Notification Access grant, this
 * observes *only* currently-playing media session metadata (track title +
 * artist) system-wide and logs it to the local, encrypted music history
 * table. It never reads notification text/content — [onNotificationPosted]
 * is intentionally left a no-op.
 */
class MusicListenerService : NotificationListenerService() {

    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachTo(controllers ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MusicListenerService::class.java)
        manager.addOnActiveSessionsChangedListener(sessionsListener, component)
        attachTo(manager.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Intentionally ignored: this service only reads media-session metadata, never notification content.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Intentionally ignored.
    }

    private fun attachTo(controllers: List<MediaController>) {
        val current = controllers.toSet()
        callbacks.keys.filterNot { it in current }.forEach { it.unregisterCallback(callbacks.getValue(it)) }
        callbacks.keys.retainAll(current)

        controllers.forEach { controller ->
            if (callbacks.containsKey(controller)) return@forEach
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    logMetadata(controller.packageName, metadata)
                }
            }
            controller.registerCallback(callback)
            callbacks[controller] = callback
            logMetadata(controller.packageName, controller.metadata)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun logMetadata(packageName: String, metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val app = application as? MiniJarvisApp ?: return
        // Use a plain background-safe scope: this service has no Activity lifecycle to tie to.
        GlobalScope.launch {
            app.container.musicHistoryRepository.logPlay(title = title, artist = artist, sourceApp = packageName)
        }
    }
}
