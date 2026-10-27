package com.incognitoblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"
    private val DEBUG_VERBOSE = true

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: "NULL"
        Log.i(TAG, "=== Boot received: $action ===")
        if (DEBUG_VERBOSE) Log.d(TAG, "Intent: $intent")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.i(TAG, "Boot completed detected, checking monitoring state")
            
            // Check if monitoring was enabled before reboot
            val prefs = context.getSharedPreferences("IncognitoBlockerPrefs", Context.MODE_PRIVATE)
            val monitoringEnabled = prefs.getBoolean("monitoring_enabled", false)
            val lastDetection = prefs.getLong("last_detection_time", 0)
            val detectionCount = prefs.getInt("detection_count", 0)
            
            Log.d(TAG, "Prefs: monitoringEnabled=$monitoringEnabled, lastDetection=$lastDetection, detectionCount=$detectionCount")

            if (monitoringEnabled) {
                Log.i(TAG, "Monitoring was enabled, restarting service after boot")
                // Start foreground service
                MonitoringForegroundService.startService(context)
            } else {
                Log.d(TAG, "Monitoring was NOT enabled, not starting service")
            }
        } else {
            Log.d(TAG, "Ignoring non-boot action: $action")
        }
    }
}