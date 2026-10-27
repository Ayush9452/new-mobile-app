package com.incognitoblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class IncognitoAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_INCOGNITO_DETECTED =
            "com.incognitoblocker.INCOGNITO_DETECTED"

        const val EXTRA_IS_INCOGNITO = "is_incognito"
        const val EXTRA_DETECTION_REASON = "detection_reason"

        private const val TAG = "IncognitoAccessibility"
        private const val PREFS_NAME = "IncognitoBlockerPrefs"
        private const val KEY_LAST_HANDLED = "last_handled"

        private const val NOTIFICATION_CHANNEL_ID = "incognito_alerts"
        private const val NOTIFICATION_ID = 1001

        private const val CHECK_DELAY_MS = 250L
        private const val DETECTION_COOLDOWN_MS = 5_000L

        private const val AUTO_CLOSE_ENABLED = true

        private const val TAB_SWITCHER_DELAY_MS = 700L
        private const val MENU_OPEN_DELAY_MS = 700L
        private const val CONFIRMATION_DIALOG_DELAY_MS = 700L
        private const val CLOSE_VERIFICATION_DELAY_MS = 900L

        private const val TAB_SWITCHER_BUTTON_ID =
            "com.android.chrome:id/tab_switcher_button"

        private const val TAB_LIST_ID =
            "com.android.chrome:id/tab_list_recycler_view"

        private const val MANAGE_TABS_MENU_BUTTON_ID =
            "com.android.chrome:id/menu_button"

        private const val CLOSE_INCOGNITO_TABS_MENU_ID =
            "com.android.chrome:id/close_all_incognito_tabs_menu_id"

        private const val CLOSE_CONFIRMATION_TITLE_ID =
            "com.android.chrome:id/title"

        private const val CLOSE_CONFIRMATION_TITLE =
            "Close all Incognito tabs?"

        private const val CLOSE_CONFIRMATION_BUTTON_ID =
            "com.android.chrome:id/positive_button"

        private const val DEBUG_TREE_MAX_DEPTH = 20
        private const val DEBUG_TREE_DUMP_INTERVAL_MS = 1_500L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val chromePackages = setOf(
        IncognitoDetectionLogic.CHROME_STABLE_PACKAGE
    )

    private var pendingCheckRunnable: Runnable? = null
    private var pendingCloseRunnable: Runnable? = null

    private var detectionState =
        IncognitoDetectionLogic.DetectionState.INCONCLUSIVE

    private var lastDebugTreeDumpTime = 0L
    private var closeAttemptInProgress = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

            notificationTimeout = 100
            packageNames = chromePackages.toTypedArray()
        }

        setServiceInfo(serviceInfo)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in chromePackages) return

        if (!IncognitoBlockerApplication.isMonitoringEnabled(this)) {
            cancelPendingCheck()
            cancelPendingClose()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                scheduleDelayedCheck()
            }
        }
    }

    private fun scheduleDelayedCheck() {
        cancelPendingCheck()

        val runnable = Runnable {
            pendingCheckRunnable = null
            performDelayedCheck()
        }

        pendingCheckRunnable = runnable
        handler.postDelayed(runnable, CHECK_DELAY_MS)
    }

    private fun cancelPendingCheck() {
        pendingCheckRunnable?.let(handler::removeCallbacks)
        pendingCheckRunnable = null
    }

    private fun cancelPendingClose() {
        pendingCloseRunnable?.let(handler::removeCallbacks)
        pendingCloseRunnable = null
        closeAttemptInProgress = false
    }

    private fun performDelayedCheck() {
        if (!IncognitoBlockerApplication.isMonitoringEnabled(this)) return

        val root = rootInActiveWindow

        if (root == null) {
            Log.d(TAG, "No active accessibility window")
            return
        }

        val detectionResult: Boolean?

        try {
            detectionResult = detectIncognitoLandingPage(root)

            Log.d(
                TAG,
                "Actual Incognito landing-page title found: " +
                        (detectionResult == true)
            )

            maybeDumpAccessibilityTree(root)
        } catch (exception: Exception) {
            Log.e(TAG, "Error inspecting Chrome accessibility tree", exception)
            return
        } finally {
            root.recycle()
        }

        updateDetectionState(detectionResult)
    }

    /**
     * Searches for Chrome's actual Incognito landing-page title.
     *
     * It intentionally ignores:
     * - Text typed into the address bar.
     * - Autocomplete suggestions such as "Refine: You've gone Incognito".
     * - Search results and ordinary webpage text.
     *
     * Finding no title is inconclusive; it does not prove regular browsing.
     */
    private fun detectIncognitoLandingPage(
        root: AccessibilityNodeInfo
    ): Boolean? {
        val titleNodes = try {
            root.findAccessibilityNodeInfosByViewId(
                IncognitoDetectionLogic.INCOGNITO_LANDING_TITLE_ID
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Could not query Incognito title node", exception)
            return null
        }

        if (titleNodes.isNullOrEmpty()) {
            return null
        }

        try {
            val matched = titleNodes.any { node ->
                node.isVisibleToUser &&
                        IncognitoDetectionLogic.isIncognitoLandingTitle(
                            node.viewIdResourceName,
                            node.text?.toString()
                        )
            }

            if (matched) {
                Log.i(TAG, "Exact Incognito landing-page title matched")
                return true
            }

            return null
        } finally {
            titleNodes.forEach { node ->
                try {
                    node.recycle()
                } catch (_: Exception) {
                    // Ignore stale accessibility nodes.
                }
            }
        }
    }

    private fun maybeDumpAccessibilityTree(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()

        if (now - lastDebugTreeDumpTime < DEBUG_TREE_DUMP_INTERVAL_MS) {
            return
        }

        lastDebugTreeDumpTime = now

        Log.d(TAG, "===== CHROME ACCESSIBILITY TREE START =====")
        dumpAccessibilityTree(root, 0)
        Log.d(TAG, "===== CHROME ACCESSIBILITY TREE END =====")
    }

    private fun dumpAccessibilityTree(
        node: AccessibilityNodeInfo?,
        depth: Int
    ) {
        if (node == null || depth > DEBUG_TREE_MAX_DEPTH) return

        val indent = "  ".repeat(depth)

        Log.d(
            TAG,
            "$indent" +
                    "class=${node.className}, " +
                    "text=${node.text}, " +
                    "desc=${node.contentDescription}, " +
                    "viewId=${node.viewIdResourceName}, " +
                    "clickable=${node.isClickable}, " +
                    "visible=${node.isVisibleToUser}"
        )

        for (index in 0 until node.childCount) {
            val child = node.getChild(index)

            try {
                dumpAccessibilityTree(child, depth + 1)
            } finally {
                child?.recycle()
            }
        }
    }

    private fun updateDetectionState(isIncognito: Boolean?) {
        val transition = IncognitoDetectionLogic.evaluateTransition(
            previous = detectionState,
            isIncognito = isIncognito
        )

        if (!transition.changed) {
            Log.d(TAG, "Duplicate state ignored: ${transition.current}")
            return
        }

        detectionState = transition.current

        when (transition.current) {
            IncognitoDetectionLogic.DetectionState.INCOGNITO -> {
                Log.w(TAG, "Incognito landing page detected")
                handleIncognitoDetected()
            }

            IncognitoDetectionLogic.DetectionState.NOT_INCOGNITO -> {
                // No action required.
            }

            IncognitoDetectionLogic.DetectionState.INCONCLUSIVE -> {
                Log.d(TAG, "Browser mode is inconclusive")
            }
        }
    }

    private fun handleIncognitoDetected() {
        val preferences =
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val now = System.currentTimeMillis()
        val lastHandled = preferences.getLong(KEY_LAST_HANDLED, 0L)

        if (now - lastHandled < DETECTION_COOLDOWN_MS) {
            Log.d(TAG, "Detection skipped because cooldown is active")
            return
        }

        preferences.edit()
            .putLong(KEY_LAST_HANDLED, now)
            .apply()

        IncognitoBlockerApplication.incrementDetectionCount(this)
        IncognitoBlockerApplication.setLastDetectionTime(this, now)

        showIncognitoNotification()
        sendDetectionBroadcast()

        if (AUTO_CLOSE_ENABLED) {
            beginAutomaticIncognitoClosure()
        }
    }

    /**
     * Safe closure flow:
     * 1. Look for the exact Incognito-only close action.
     * 2. Otherwise, open Chrome's tab overview.
     * 3. Try the tab-management menu once.
     * 4. Confirm only the exact Incognito confirmation dialog.
     *
     * No generic "Close all tabs" fallback is allowed.
     */
    private fun beginAutomaticIncognitoClosure() {
        if (closeAttemptInProgress) {
            Log.d(TAG, "Closure attempt already in progress")
            return
        }

        if (!IncognitoBlockerApplication.isMonitoringEnabled(this)) {
            Log.d(TAG, "Monitoring disabled; skipping closure")
            return
        }

        closeAttemptInProgress = true
        Log.i(TAG, "Starting targeted Incognito closure attempt")

        if (clickIncognitoCloseMenuAction()) {
            return
        }

        if (hasVisibleNodeByViewId(TAB_LIST_ID)) {
            Log.i(TAG, "Already in Chrome tab overview")
            openManageTabsMenu()
            return
        }

        if (!clickVisibleNodeByViewId(TAB_SWITCHER_BUTTON_ID)) {
            Log.w(TAG, "Tab switcher unavailable; stopping safely")
            finishCloseAttempt()
            return
        }

        Log.i(TAG, "Tab switcher click accepted; waiting for overview")

        scheduleCloseStep(TAB_SWITCHER_DELAY_MS) {
            if (!hasVisibleNodeByViewId(TAB_LIST_ID)) {
                Log.w(
                    TAG,
                    "Tab overview not detected; no close action performed"
                )
                dumpCurrentAccessibilityTree()
                finishCloseAttempt()
                return@scheduleCloseStep
            }

            Log.i(TAG, "Tab overview confirmed")
            openManageTabsMenu()
        }
    }

    private fun openManageTabsMenu() {
        if (!IncognitoBlockerApplication.isMonitoringEnabled(this)) {
            finishCloseAttempt()
            return
        }

        if (clickIncognitoCloseMenuAction()) {
            return
        }

        if (!hasVisibleNodeByViewId(TAB_LIST_ID)) {
            Log.w(TAG, "Tab overview disappeared; stopping safely")
            finishCloseAttempt()
            return
        }

        if (!clickVisibleNodeByViewId(MANAGE_TABS_MENU_BUTTON_ID)) {
            Log.w(TAG, "Manage-open-tabs button unavailable")
            dumpCurrentAccessibilityTree()
            finishCloseAttempt()
            return
        }

        Log.i(
            TAG,
            "Manage-open-tabs click accepted; checking resulting UI"
        )

        scheduleCloseStep(MENU_OPEN_DELAY_MS) {
            if (!IncognitoBlockerApplication.isMonitoringEnabled(this)) {
                finishCloseAttempt()
                return@scheduleCloseStep
            }

            if (clickIncognitoCloseMenuAction()) {
                return@scheduleCloseStep
            }

            Log.w(
                TAG,
                "Incognito-specific close action is not visible " +
                        "after the menu click"
            )

            // This dump is useful for determining which actions this
            // particular Chrome version exposes in its tab overview.
            dumpCurrentAccessibilityTree()
            finishCloseAttempt()
        }
    }

    /**
     * This method clicks only Chrome's Incognito-specific close action.
     * It never searches for or clicks a generic close-all-tabs action.
     */
    private fun clickIncognitoCloseMenuAction(): Boolean {
        if (!clickVisibleNodeByViewId(CLOSE_INCOGNITO_TABS_MENU_ID)) {
            return false
        }

        Log.i(TAG, "Incognito-specific close action click accepted")

        scheduleCloseStep(CONFIRMATION_DIALOG_DELAY_MS) {
            confirmIncognitoTabClosure()
        }

        return true
    }

    private fun confirmIncognitoTabClosure() {
        if (!IncognitoBlockerApplication.isMonitoringEnabled(this)) {
            Log.d(TAG, "Monitoring disabled before confirmation")
            finishCloseAttempt()
            return
        }

        val correctDialogVisible = hasExactVisibleTextByViewId(
            CLOSE_CONFIRMATION_TITLE_ID,
            CLOSE_CONFIRMATION_TITLE
        )

        if (!correctDialogVisible) {
            Log.w(
                TAG,
                "Exact Incognito confirmation title not found; " +
                        "refusing to confirm"
            )
            dumpCurrentAccessibilityTree()
            finishCloseAttempt()
            return
        }

        Log.i(TAG, "Exact Incognito confirmation dialog verified")

        if (!clickVisibleNodeByViewId(CLOSE_CONFIRMATION_BUTTON_ID)) {
            Log.w(TAG, "Confirmation button unavailable")
            finishCloseAttempt()
            return
        }

        Log.i(TAG, "Confirmation button click accepted")

        scheduleCloseStep(CLOSE_VERIFICATION_DELAY_MS) {
            verifyIncognitoClosure()
        }
    }

    private fun verifyIncognitoClosure() {
        val dialogStillVisible = hasExactVisibleTextByViewId(
            CLOSE_CONFIRMATION_TITLE_ID,
            CLOSE_CONFIRMATION_TITLE
        )

        if (dialogStillVisible) {
            Log.w(TAG, "Confirmation dialog remains visible")
        } else {
            Log.i(
                TAG,
                "Confirmation dialog dismissed; closure result " +
                        "still requires independent verification"
            )
        }

        dumpCurrentAccessibilityTree()
        finishCloseAttempt()
    }

    private fun dumpCurrentAccessibilityTree() {
        val root = rootInActiveWindow ?: run {
            Log.d(TAG, "Cannot dump tree: no active window")
            return
        }

        try {
            Log.d(TAG, "===== CURRENT CHROME TREE START =====")
            dumpAccessibilityTree(root, 0)
            Log.d(TAG, "===== CURRENT CHROME TREE END =====")
        } finally {
            root.recycle()
        }
    }

    private fun hasExactVisibleTextByViewId(
        viewId: String,
        expectedText: String
    ): Boolean {
        val root = rootInActiveWindow ?: return false

        try {
            val matches = try {
                root.findAccessibilityNodeInfosByViewId(viewId)
            } catch (exception: Exception) {
                Log.e(TAG, "Error searching for $viewId", exception)
                return false
            }

            if (matches.isNullOrEmpty()) return false

            try {
                return matches.any { node ->
                    node.isVisibleToUser &&
                            node.text?.toString()
                                ?.trim()
                                .equals(expectedText, ignoreCase = true)
                }
            } finally {
                matches.forEach { node ->
                    try {
                        node.recycle()
                    } catch (_: Exception) {
                        // Ignore stale nodes.
                    }
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun hasVisibleNodeByViewId(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false

        try {
            val matches = try {
                root.findAccessibilityNodeInfosByViewId(viewId)
            } catch (exception: Exception) {
                Log.e(TAG, "Error searching for $viewId", exception)
                return false
            }

            if (matches.isNullOrEmpty()) return false

            try {
                return matches.any { it.isVisibleToUser }
            } finally {
                matches.forEach { node ->
                    try {
                        node.recycle()
                    } catch (_: Exception) {
                        // Ignore stale nodes.
                    }
                }
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * A true result means Android accepted the accessibility click.
     * It does not guarantee that Chrome changed screens.
     */
    private fun clickVisibleNodeByViewId(viewId: String): Boolean {
        val root = rootInActiveWindow ?: run {
            Log.d(TAG, "No active window while searching for $viewId")
            return false
        }

        try {
            val matches = try {
                root.findAccessibilityNodeInfosByViewId(viewId)
            } catch (exception: Exception) {
                Log.e(TAG, "Error searching for $viewId", exception)
                return false
            }

            if (matches.isNullOrEmpty()) {
                Log.d(TAG, "Chrome node not found: $viewId")
                return false
            }

            try {
                for (node in matches) {
                    if (
                        node.isVisibleToUser &&
                        node.isEnabled &&
                        node.isClickable
                    ) {
                        Log.d(TAG, "Attempting click on $viewId")

                        val clicked = node.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )

                        if (clicked) {
                            Log.i(TAG, "Accessibility click accepted: $viewId")
                            return true
                        }

                        Log.w(TAG, "Accessibility click failed: $viewId")
                    }
                }
            } finally {
                matches.forEach { node ->
                    try {
                        node.recycle()
                    } catch (_: Exception) {
                        // Ignore stale nodes.
                    }
                }
            }

            Log.w(TAG, "No visible, enabled, clickable node found: $viewId")
            return false
        } finally {
            root.recycle()
        }
    }

    private fun scheduleCloseStep(
        delayMs: Long,
        action: () -> Unit
    ) {
        pendingCloseRunnable?.let(handler::removeCallbacks)

        val runnable = Runnable {
            pendingCloseRunnable = null
            action()
        }

        pendingCloseRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun finishCloseAttempt() {
        pendingCloseRunnable?.let(handler::removeCallbacks)
        pendingCloseRunnable = null
        closeAttemptInProgress = false
    }

    private fun sendDetectionBroadcast() {
        val broadcastIntent =
            Intent(ACTION_INCOGNITO_DETECTED).apply {
                putExtra(EXTRA_IS_INCOGNITO, true)
                putExtra(
                    EXTRA_DETECTION_REASON,
                    "exact_incognito_landing_page_title"
                )
            }

        sendBroadcast(broadcastIntent)
        Log.d(TAG, "Incognito detection broadcast sent")
    }

    private fun showIncognitoNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Incognito Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Alerts when Chrome's Incognito landing page is detected"
            }

            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(
            this,
            NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Incognito detected")
            .setContentText(
                "Chrome's Incognito landing page was detected."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
        cancelPendingCheck()
        cancelPendingClose()
    }

    override fun onDestroy() {
        cancelPendingCheck()
        cancelPendingClose()
        handler.removeCallbacksAndMessages(null)

        Log.i(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }
}