package com.example.apptimelimiter

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

/** Milliseconds each app has been in the foreground since local midnight. */
fun usageTodayMs(context: Context): Map<String, Long> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val now = System.currentTimeMillis()

    val events = usm.queryEvents(start, now)
    val e = UsageEvents.Event()
    val totals = mutableMapOf<String, Long>()
    val openSince = mutableMapOf<String, Long>()

    while (events.hasNextEvent()) {
        events.getNextEvent(e)
        val pkg = e.packageName
        @Suppress("DEPRECATION")
        when (e.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> openSince[pkg] = e.timeStamp
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val s = openSince.remove(pkg)
                if (s != null) totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - s)
            }
        }
    }
    // apps that are open right now
    openSince.forEach { (pkg, s) -> totals[pkg] = (totals[pkg] ?: 0L) + (now - s) }
    return totals
}