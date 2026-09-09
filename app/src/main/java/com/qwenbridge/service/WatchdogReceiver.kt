package com.qwenbridge.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.qwenbridge.data.ConfigManager

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val configManager = ConfigManager.getInstance(context)
        if (!isServiceRunning(context, QwenBridgeService::class.java)) {
            QwenBridgeService.start(context)
        }
        scheduleWatchdog(context)
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    companion object {
        fun scheduleWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                8788,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = SystemClock.elapsedRealtime() + (15 * 60 * 1000L) // 15 mins

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
    }
}
