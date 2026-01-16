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
        super.onDestroy()
    }

    private fun updateController(list: List<MediaController>?) {
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        currentController = playing ?: list?.lastOrNull()
    }
}
