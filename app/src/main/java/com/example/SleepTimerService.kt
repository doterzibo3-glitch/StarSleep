package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class SleepTimerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var timerJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private lateinit var audioManager: AudioManager
    private var initialVolume: Int = 0

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_WAKE_SCREEN = "ACTION_WAKE_SCREEN"
        const val ACTION_ADD_TIME = "ACTION_ADD_TIME"

        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        const val EXTRA_FADE_VOLUME = "EXTRA_FADE_VOLUME"
        const val EXTRA_DIM_SCREEN = "EXTRA_DIM_SCREEN"
        
        const val NOTIFICATION_CHANNEL_ID = "sleep_timer_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                val fade = intent.getBooleanExtra(EXTRA_FADE_VOLUME, false)
                val dim = intent.getBooleanExtra(EXTRA_DIM_SCREEN, false)
                startTimer(duration, fade, dim)
            }
            ACTION_STOP -> {
                stopTimer()
            }
            ACTION_WAKE_SCREEN -> {
                removeOverlay()
            }
            ACTION_ADD_TIME -> {
                addTime(10 * 60 * 1000L) // 10 minutes
            }
        }
        return START_NOT_STICKY
    }

    private var timerEndTimeMs: Long = 0L

    private fun addTime(extraMs: Long) {
        if (TimerState.isRunning.value) {
            timerEndTimeMs += extraMs
            TimerState.totalMillis.value += extraMs
        }
    }

    private fun startTimer(durationMs: Long, fadeVolume: Boolean, dimScreen: Boolean) {
        if (durationMs <= 0) return

        initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        TimerState.isRunning.value = true
        TimerState.totalMillis.value = durationMs
        TimerState.remainingMillis.value = durationMs

        val notification = createNotification("Таймер запущен")
        startForeground(NOTIFICATION_ID, notification)

        if (dimScreen) {
            showOverlay()
        }

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            timerEndTimeMs = System.currentTimeMillis() + durationMs

            while (System.currentTimeMillis() < timerEndTimeMs) {
                val remaining = timerEndTimeMs - System.currentTimeMillis()
                TimerState.remainingMillis.value = remaining
                
                if (fadeVolume) {
                    handleVolumeFade(remaining, TimerState.totalMillis.value)
                }
                
                updateNotification(remaining)
                delay(1000)
            }
            
            onTimerComplete()
        }
    }

    private fun handleVolumeFade(remainingMs: Long, totalMs: Long) {
        // Fade in the last 20% of the time, or at least 1 minute
        val fadeStartTimeMs = minOf(totalMs * 2 / 10, 60_000L).coerceAtMost(totalMs)
        if (remainingMs <= fadeStartTimeMs) {
            val progress = remainingMs.toFloat() / fadeStartTimeMs.toFloat()
            val targetVolume = (initialVolume * progress).toInt()
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            
            // Only adjust if target is lower and we haven't manually changed it upwards
            if (targetVolume < currentVolume) {
                 audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            }
        }
    }

    private fun onTimerComplete() {
        TimerState.remainingMillis.value = 0
        
        // Pause media playback
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        audioManager.dispatchMediaKeyEvent(eventDown)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
        audioManager.dispatchMediaKeyEvent(eventUp)
        
        stopTimer()
    }

    private fun stopTimer() {
        timerJob?.cancel()
        TimerState.isRunning.value = false
        TimerState.remainingMillis.value = 0
        TimerState.totalMillis.value = 0
        
        // Restore volume if we were fading
        // audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initialVolume, 0)
        
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.screenBrightness = 0.0f
        params.gravity = Gravity.TOP or Gravity.START
        
        overlayView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            // Double tap to wake
            var lastClickTime = 0L
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastClickTime < 300) {
                        removeOverlay() // Double tap wakes screen
                    }
                    lastClickTime = clickTime
                }
                true // Consume touches so they don't pass through to YouTube
            }
        }
        
        try {
            windowManager?.addView(overlayView, params)
            TimerState.isOverlayActive.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
                TimerState.isOverlayActive.value = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sleep Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val stopIntent = Intent(this, SleepTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val addTimeIntent = Intent(this, SleepTimerService::class.java).apply {
            action = ACTION_ADD_TIME
        }
        val pendingAddTimeIntent = PendingIntent.getService(
            this, 1, addTimeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val appIntent = Intent(this, MainActivity::class.java)
        val pendingAppIntent = PendingIntent.getActivity(
            this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Звездный Сон")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingAppIntent)
            .addAction(android.R.drawable.ic_input_add, "+10 мин", pendingAddTimeIntent)
            .addAction(android.R.drawable.ic_media_pause, "Стоп", pendingStopIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(remainingMs: Long) {
        val minutes = (remainingMs / 1000) / 60
        val seconds = (remainingMs / 1000) % 60
        val timeStr = String.format("%02d:%02d", minutes, seconds)
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification("Осталось: \$timeStr"))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        removeOverlay()
        super.onDestroy()
    }
}
