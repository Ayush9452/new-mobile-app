package com.incognitoblocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.incognitoblocker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    // UI Elements
    private lateinit var btnAccessibility: Button
    private lateinit var btnMonitoring: Button
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvMonitoringStatus: TextView
    private lateinit var ivAccessibilityIcon: ImageView
    private lateinit var ivMonitoringIcon: ImageView
    private lateinit var tvLastDetection: TextView

    // State
    private var isAccessibilityEnabled = false
    private var isMonitoringActive = false

    // Dynamically registered broadcast receiver (stored to prevent leaks)
    private var incognitoReceiver: BroadcastReceiver? = null
    private val incognitoFilter: IntentFilter = IntentFilter().apply {
        addAction(IncognitoAccessibilityService.ACTION_INCOGNITO_DETECTED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupClickListeners()
        checkInitialState()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        checkInitialState()
        registerIncognitoReceiver()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        unregisterIncognitoReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        unregisterIncognitoReceiver()
    }

    private fun initViews() {
        Log.d(TAG, "Initializing views")
        btnAccessibility = binding.btnAccessibility
        btnMonitoring = binding.btnMonitoring
        tvAccessibilityStatus = binding.tvAccessibilityStatus
        tvMonitoringStatus = binding.tvMonitoringStatus
        ivAccessibilityIcon = binding.ivAccessibilityIcon
        ivMonitoringIcon = binding.ivMonitoringIcon
        tvLastDetection = binding.tvLastDetection
        val btnViewLogs = binding.btnViewLogs
    }

    private fun setupClickListeners() {
        btnAccessibility.setOnClickListener {
            Log.d(TAG, "Accessibility button clicked")
            openAccessibilitySettings()
        }

        btnMonitoring.setOnClickListener {
            Log.d(TAG, "Monitoring button clicked, current state: isMonitoringActive=$isMonitoringActive")
            toggleMonitoring()
        }

        binding.btnViewLogs.setOnClickListener {
            Log.d(TAG, "View Logs button clicked")
            val intent = Intent(this, LogViewerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkInitialState() {
        Log.d(TAG, "Checking initial state")
        checkAccessibilityEnabled()
        checkMonitoringState()
        updateLastDetectionInfo()
    }

    private fun checkAccessibilityEnabled() {
        val enabled = isAccessibilityServiceEnabled()
        Log.d(TAG, "Accessibility service enabled: $enabled (was: $isAccessibilityEnabled)")
        if (enabled != isAccessibilityEnabled) {
            isAccessibilityEnabled = enabled
            updateAccessibilityUI()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        // Build the expected service id using the short-form component name.
        val serviceId = ComponentName(
            packageName,
            IncognitoAccessibilityService::class.java.name
        ).flattenToShortString()

        // Get ALL enabled services regardless of feedback type (fixes Android 16 detection)
        val allServices = accessibilityManager.getEnabledAccessibilityServiceList(-1)
        val isEnabled = allServices.any { it.id == serviceId }

        Log.d(TAG, "Checking accessibility service: serviceId=$serviceId, isEnabled=$isEnabled, totalServices=${allServices.size}")
        if (DEBUG_VERBOSE) {
            allServices.forEach { Log.d(TAG, "  Enabled service: ${it.id}, feedbackType=${it.feedbackType}") }
        }
        return isEnabled
    }

    private fun checkMonitoringState() {
        val prefsEnabled = IncognitoBlockerApplication.isMonitoringEnabled(this)
        val serviceRunning = MonitoringForegroundService.isServiceRunning(this)
        val newState = prefsEnabled && serviceRunning
        Log.d(TAG, "Monitoring state: prefsEnabled=$prefsEnabled, serviceRunning=$serviceRunning, newState=$newState (was: $isMonitoringActive)")
        if (newState != isMonitoringActive) {
            isMonitoringActive = newState
            updateMonitoringUI()
        }
    }

    private fun updateAccessibilityUI() {
        runOnUiThread {
            Log.d(TAG, "Updating accessibility UI: isAccessibilityEnabled=$isAccessibilityEnabled")
            if (isAccessibilityEnabled) {
                tvAccessibilityStatus.text = getString(R.string.status_accessibility_enabled)
                tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
                ivAccessibilityIcon.setImageResource(android.R.drawable.checkbox_on_background)
                btnAccessibility.text = "Accessibility Enabled"
                btnAccessibility.isEnabled = false
                btnAccessibility.alpha = 0.6f
            } else {
                tvAccessibilityStatus.text = getString(R.string.status_accessibility_disabled)
                tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.red))
                ivAccessibilityIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                btnAccessibility.text = getString(R.string.btn_enable_accessibility)
                btnAccessibility.isEnabled = true
                btnAccessibility.alpha = 1f
            }
        }
    }

    private fun updateMonitoringUI() {
        runOnUiThread {
            Log.d(TAG, "Updating monitoring UI: isMonitoringActive=$isMonitoringActive")
            if (isMonitoringActive) {
                tvMonitoringStatus.text = getString(R.string.status_monitoring_active)
                tvMonitoringStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
                ivMonitoringIcon.setImageResource(android.R.drawable.ic_media_play)
                btnMonitoring.text = getString(R.string.btn_stop_monitoring)
                btnMonitoring.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red))
            } else {
                tvMonitoringStatus.text = getString(R.string.status_monitoring_inactive)
                tvMonitoringStatus.setTextColor(ContextCompat.getColor(this, R.color.red))
                ivMonitoringIcon.setImageResource(android.R.drawable.ic_media_pause)
                btnMonitoring.text = getString(R.string.btn_start_monitoring)
                btnMonitoring.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.purple_500))
            }
        }
    }

    private fun updateLastDetectionInfo() {
        val lastDetection = IncognitoBlockerApplication.getLastDetectionTime(this)
        val count = IncognitoBlockerApplication.getDetectionCount(this)
        Log.d(TAG, "Last detection: time=$lastDetection, count=$count")

        if (lastDetection > 0) {
            val timeAgo = formatTimeAgo(System.currentTimeMillis() - lastDetection)
            tvLastDetection.text = "Last detection: $timeAgo ago | Total blocked: $count"
            tvLastDetection.visibility = View.VISIBLE
        } else {
            tvLastDetection.visibility = View.GONE
        }
    }

    private fun formatTimeAgo(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
            else -> "${seconds / 86400}d"
        }
    }

    private fun openAccessibilitySettings() {
        Log.d(TAG, "Opening accessibility settings")
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Find 'Incognito Tab Detector' and enable it", Toast.LENGTH_LONG).show()
    }

    private fun toggleMonitoring() {
        if (isMonitoringActive) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        Log.d(TAG, "Starting monitoring")
        if (!isAccessibilityEnabled) {
            Log.w(TAG, "Accessibility not enabled, cannot start monitoring")
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting notification permission")
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
                return
            }
        }

        Log.i(TAG, "Enabling monitoring")
        IncognitoBlockerApplication.setMonitoringEnabled(this, true)
        MonitoringForegroundService.startService(this)

        isMonitoringActive = true
        updateMonitoringUI()
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring")
        IncognitoBlockerApplication.setMonitoringEnabled(this, false)
        MonitoringForegroundService.stopService(this)

        isMonitoringActive = false
        updateMonitoringUI()
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    private fun registerIncognitoReceiver() {
        if (incognitoReceiver != null) {
            Log.d(TAG, "Broadcast receiver already registered, skipping")
            return
        }

        Log.d(TAG, "Registering broadcast receiver for INCOGNITO_DETECTED")
        incognitoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.w(TAG, "Received INCOGNITO_DETECTED broadcast")
                val isIncognito = intent?.getBooleanExtra(IncognitoAccessibilityService.EXTRA_IS_INCOGNITO, false) ?: false
                val reason = intent?.getStringExtra(IncognitoAccessibilityService.EXTRA_DETECTION_REASON) ?: "unknown"
                Log.i(TAG, "Detection broadcast: isIncognito=$isIncognito, reason=$reason")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.incognito_detected, Toast.LENGTH_LONG).show()
                    updateLastDetectionInfo()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(incognitoReceiver, incognitoFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(incognitoReceiver, incognitoFilter)
        }
        Log.d(TAG, "Broadcast receiver registered")
    }

    private fun unregisterIncognitoReceiver() {
        incognitoReceiver?.let { receiver ->
            Log.d(TAG, "Unregistering broadcast receiver")
            try {
                unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered (already unregistered)", e)
            }
            incognitoReceiver = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "Permission result: requestCode=$requestCode, grantResults=${grantResults.contentToString()}")
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Notification permission granted, starting monitoring")
            startMonitoring()
        } else if (requestCode == 1001) {
            Log.w(TAG, "Notification permission denied")
            Toast.makeText(this, "Notification permission required for alerts", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val DEBUG_VERBOSE = true
    }
}