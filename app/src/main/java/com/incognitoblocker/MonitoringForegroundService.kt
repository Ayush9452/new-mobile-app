package com.incognitoblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MonitoringForegroundService : Service() {

    companion object {
        private const val TAG = "MonitoringService"
        private const val CHANNEL_ID = "monitoring_channel"
        private const val NOTIFICATION_ID = 1002
        private const val DEBUG_VERBOSE = true

        fun startService(context: Context) {
            Log.i(TAG, "Starting MonitoringForegroundService")
            val intent = Intent(context, MonitoringForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            Log.i(TAG, "Stopping MonitoringForegroundService")
            val intent = Intent(context, MonitoringForegroundService::class.java)
            context.stopService(intent)
        }

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val running = manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == MonitoringForegroundService::class.java.name
            }
            Log.d(TAG, "isServiceRunning: $running")
            return running
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "=== Monitoring Foreground Service CREATED ===")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "=== Monitoring service STARTED === flags=$flags, startId=$startId")

        val notification = buildNotification()
        // targetSdk 35 requires a foreground service type. dataSync (=1) is appropriate
        // for a background monitoring service and needs no special permission.
        startForeground(NOTIFICATION_ID, notification, 1)
        Log.d(TAG, "startForeground successful")

        // Keep service alive - monitoring is done via AccessibilityService
        // This service just keeps the process alive
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "onTaskRemoved - service may be killed")
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "Creating notification channel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incognito Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps incognito detection running in background"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }

    private fun buildNotification(): Notification {
        if (DEBUG_VERBOSE) Log.d(TAG, "Building notification")
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Incognito Blocker Active")
            .setContentText("Monitoring Chrome for incognito tabs")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "=== Monitoring Foreground Service DESTROYED ===")
    }
}