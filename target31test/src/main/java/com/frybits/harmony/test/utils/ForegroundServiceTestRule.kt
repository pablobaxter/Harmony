package com.frybits.harmony.test.utils

import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule

class ForegroundServiceTestRule : ServiceTestRule() {

    fun startForegroundService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            InstrumentationRegistry.getInstrumentation().targetContext.startForegroundService(intent)
        }
        startService(intent)
    }
}
