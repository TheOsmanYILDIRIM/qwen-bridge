package com.qwenbridge.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.qwenbridge.MainActivity
import com.qwenbridge.R
import com.qwenbridge.challenge.ChallengeOverlayManager
import com.qwenbridge.data.ConfigManager
import com.qwenbridge.overlay.FloatingStatusOverlay
import com.qwenbridge.proxy.EmbeddedProxyServer

class QwenBridgeService : Service() {

    private var proxyServer: EmbeddedProxyServer? = null
    private lateinit var keepAliveManager: KeepAliveManager
    private lateinit var challengeOverlayManager: ChallengeOverlayManager
    private lateinit var configManager: ConfigManager
    private var floatingStatusOverlay: FloatingStatusOverlay? = null

    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager.getInstance(this)
        keepAliveManager = KeepAliveManager(this)
        challengeOverlayManager = ChallengeOverlayManager.getInstance(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val config = configManager.config.value
        val port = config.port

        startForegroundServiceWithNotification(port)

        try {
            if (proxyServer == null) {
                proxyServer = EmbeddedProxyServer(this, port).apply {
                    start()
                }
                configManager.setServiceRunning(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (config.keepAliveWakeLock) {
            keepAliveManager.acquireWakeLock()
        }
        if (config.keepAliveMediaSession) {
            keepAliveManager.startMediaSessionKeepAlive()
        }

        challengeOverlayManager.initOverlay()
        if (config.floatingOverlay) {
            floatingStatusOverlay = FloatingStatusOverlay.getInstance(this)
            floatingStatusOverlay?.show()
        }
        WatchdogReceiver.scheduleWatchdog(this)

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification(port: Int) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, QwenBridgeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Qwen Bridge")
            .setContentText("API proxy running on http://localhost:$port")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            proxyServer?.stop()
            proxyServer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        keepAliveManager.cleanup()
        challengeOverlayManager.destroy()
        floatingStatusOverlay?.destroy()
        configManager.setServiceRunning(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "qwen_bridge_channel"
        const val NOTIFICATION_ID = 8787
        const val ACTION_STOP = "com.qwenbridge.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, QwenBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, QwenBridgeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
