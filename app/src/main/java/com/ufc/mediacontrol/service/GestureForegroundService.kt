package com.ufc.mediacontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ufc.mediacontrol.R
import com.ufc.mediacontrol.media.MediaCommander
import com.ufc.mediacontrol.sensor.GestureEngine
import kotlin.math.roundToInt

class GestureForegroundService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "media_gesture_channel"
        private const val CHANNEL_NAME = "Media Gesture Control"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.ufc.mediacontrol.action.START"
        const val ACTION_STOP = "com.ufc.mediacontrol.action.STOP"

        const val BROADCAST_SENSOR_UPDATE = "com.ufc.mediacontrol.SENSOR_UPDATE"
        const val EXTRA_ROLL_DEG = "roll_deg"
        const val EXTRA_SIDE = "side"

        private const val WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L
        private const val ROLL_ALPHA = 0.6f
    }

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var gestureEngine = GestureEngine()
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var smoothedRoll: Float = 0f
    private var wakeLock: PowerManager.WakeLock? = null
    private var isTestMode = false

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MediaControl::GestureSensorLock"
        )
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSensing()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val neutralZone = intent?.getFloatExtra("neutralZoneDeg", 20f) ?: 20f
                val tiltThreshold = intent?.getFloatExtra("tiltThresholdDeg", 35f) ?: 35f
                isTestMode = intent?.getBooleanExtra("testMode", false) ?: false

                gestureEngine = GestureEngine(
                    GestureEngine.Config(
                        neutralZoneDeg = neutralZone,
                        tiltThresholdDeg = tiltThreshold,
                        tiltMinDurationMs = 100L,
                        doubleTiltWindowMs = 2000L,
                        neutralReturnMs = 150L,
                        globalCooldownMs = 300L
                    )
                )

                val notificationText = if (isTestMode) "Modo teste ativo" else "Controle ativo"
                startForeground(NOTIFICATION_ID, buildNotification(notificationText))
                startSensing()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopSensing()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSensing() {
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(WAKE_LOCK_TIMEOUT)
            }
        }

        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopSensing() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rollDeg = rotationVectorToRollDeg(event.values)
            smoothedRoll = ROLL_ALPHA * smoothedRoll + (1f - ROLL_ALPHA) * rollDeg

            if (isTestMode) {
                broadcastSensorData(smoothedRoll)
            }

            val cmd = gestureEngine.onRoll(smoothedRoll, System.currentTimeMillis())
            if (cmd != null) {
                executeCommand(cmd, smoothedRoll)
            }
        }
    }

    private fun broadcastSensorData(rollDeg: Float) {
        val side = when {
            rollDeg > gestureEngine.config.tiltThresholdDeg -> "DIREITA"
            rollDeg < -gestureEngine.config.tiltThresholdDeg -> "ESQUERDA"
            else -> "NEUTRO"
        }

        val intent = Intent(BROADCAST_SENSOR_UPDATE).apply {
            putExtra(EXTRA_ROLL_DEG, rollDeg)
            putExtra(EXTRA_SIDE, side)
        }
        sendBroadcast(intent)
    }

    private fun executeCommand(cmd: GestureEngine.Command, rollDeg: Float) {
        val ok = if (!isTestMode) {
            when (cmd) {
                GestureEngine.Command.NEXT -> MediaCommander.skipNext()
                GestureEngine.Command.PREVIOUS -> MediaCommander.skipPrevious()
            }
        } else {
            true
        }

        val msg = if (isTestMode) {
            when (cmd) {
                GestureEngine.Command.NEXT -> "✓ DETECTADO: Próxima"
                GestureEngine.Command.PREVIOUS -> "✓ DETECTADO: Anterior"
            } + " (${rollDeg.roundToInt()}°)"
        } else if (ok) {
            when (cmd) {
                GestureEngine.Command.NEXT -> "Próxima faixa"
                GestureEngine.Command.PREVIOUS -> "Faixa anterior"
            } + " (roll=${rollDeg.roundToInt()}°)"
        } else {
            "Sem mídia ativa (abra Spotify/YT Music e dê play)"
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(msg))
    }

    private fun rotationVectorToRollDeg(values: FloatArray): Float {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val rollRad = orientationAngles[2]
        return rollRad * 180f / Math.PI.toFloat()
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MediaControl")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}