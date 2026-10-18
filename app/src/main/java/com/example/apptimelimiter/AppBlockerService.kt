package com.example.apptimelimiter

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

class AppBlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null

    // Re-checks every 20s while a limited app is on screen
    private val checker = object : Runnable {
        override fun run() {
            val pkg = currentPkg ?: return
            if (!blockIfOverLimit(pkg)) handler.postDelayed(this, 20_000)
        }
    }

    private fun blockIfOverLimit(pkg: String): Boolean {
        val usedMs = usageTodayMs(this)[pkg] ?: 0L
        val limitMs = loadLimit(this, pkg) * 60_000L
        if (usedMs < limitMs) return false

        currentPkg = null
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("pkg", pkg)
        )
        return true
    }

    // Keyboards and the notification shade fire events too; ignore them
    private fun imePackages(): Set<String> {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.map { it.packageName }.toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == "com.android.systemui" || pkg in imePackages()) return

        handler.removeCallbacks(checker)

        if (pkg == packageName || pkg !in loadSelected(this)) {
            currentPkg = null            // left the limited app
            return
        }

        currentPkg = pkg
        if (!blockIfOverLimit(pkg)) handler.postDelayed(checker, 20_000)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(checker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(checker)
        super.onDestroy()
    }
}

fun isBlockerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains("${context.packageName}/${AppBlockerService::class.java.name}")
}