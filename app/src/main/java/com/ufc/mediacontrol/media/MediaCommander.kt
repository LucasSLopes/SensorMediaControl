package com.ufc.mediacontrol.media

import com.ufc.mediacontrol.service.MediaNotificationListener

object MediaCommander {

    fun skipNext(): Boolean {
        val controller = MediaNotificationListener.getController() ?: return false
        controller.transportControls.skipToNext()
        return true
    }

    fun skipPrevious(): Boolean {
        val controller = MediaNotificationListener.getController() ?: return false
        controller.transportControls.skipToPrevious()
        return true
    }
}

