package com.ufc.mediacontrol.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        private var currentController: MediaController? = null

        fun getController(): MediaController? = currentController

        fun isPermissionGranted(context: Context): Boolean {
            val packageName = context.packageName
            val listeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return listeners?.contains(packageName) == true
        }
    }

    private lateinit var mediaSessionManager: MediaSessionManager

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list ->
            updateController(list)
        }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        val component = ComponentName(this, MediaNotificationListener::class.java)

        val current = mediaSessionManager.getActiveSessions(component)
        updateController(current)
        mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, component)
    }

    override fun onDestroy() {
        runCatching {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        }
        currentController = null
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        currentController = null
    }

    private fun updateController(list: List<MediaController>?) {
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        currentController = playing ?: list?.lastOrNull()
    }
}
