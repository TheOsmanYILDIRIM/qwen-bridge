package com.qwenbridge

import android.app.Application
import com.qwenbridge.data.ConfigManager
import com.qwenbridge.service.QwenBridgeService

class QwenBridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val configManager = ConfigManager.getInstance(this)
        if (configManager.config.value.autoStartOnBoot) {
            QwenBridgeService.start(this)
        }
    }
}
