package com.qwenbridge.service

import android.content.Context
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class KeepAliveManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null

    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "QwenBridge::ProxyKeepAlive"
            ).apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L) // 30 mins timeout
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wakeLock = null
        }
    }

    fun startMediaSessionKeepAlive() {
        if (mediaSession != null) return
        try {
            mediaSession = MediaSessionCompat(context, "QwenBridgeKeepAlive").apply {
                isActive = true
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                        .build()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopMediaSessionKeepAlive() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaSession = null
        }
    }

    fun cleanup() {
        releaseWakeLock()
        stopMediaSessionKeepAlive()
    }
}
