package com.incognitoblocker

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class IncognitoBlockerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("IncognitoBlockerApp", "=== Application CREATED ===")
    }

    companion object {
        private const val PREFS_NAME = "IncognitoBlockerPrefs"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_LAST_DETECTION_TIME = "last_detection_time"
        private const val KEY_DETECTION_COUNT = "detection_count"
        private const val TAG = "IncognitoBlockerApp"
        private const val DEBUG_VERBOSE = true

        @Suppress("UNUSED_PARAMETER")
        fun isMonitoringEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
            if (DEBUG_VERBOSE) Log.d(TAG, "isMonitoringEnabled: $enabled")
            return enabled
        }

        fun setMonitoringEnabled(context: Context, enabled: Boolean) {
            Log.i(TAG, "setMonitoringEnabled: $enabled")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
        }

        fun getLastDetectionTime(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val time = prefs.getLong(KEY_LAST_DETECTION_TIME, 0)
            if (DEBUG_VERBOSE) Log.d(TAG, "getLastDetectionTime: $time")
            return time
        }

        fun setLastDetectionTime(context: Context, time: Long) {
            if (DEBUG_VERBOSE) Log.d(TAG, "setLastDetectionTime: $time")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_DETECTION_TIME, time).apply()
        }

        fun getDetectionCount(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_DETECTION_COUNT, 0)
            if (DEBUG_VERBOSE) Log.d(TAG, "getDetectionCount: $count")
            return count
        }

        fun incrementDetectionCount(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_DETECTION_COUNT, 0) + 1
            prefs.edit().putInt(KEY_DETECTION_COUNT, count).apply()
            Log.i(TAG, "incrementDetectionCount: $count")
            return count
        }
    }
}