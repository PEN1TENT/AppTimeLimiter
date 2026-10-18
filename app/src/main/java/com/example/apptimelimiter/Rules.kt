package com.example.apptimelimiter

import android.content.Context
import java.util.Calendar

private fun prefs(c: Context) = c.getSharedPreferences("rules", Context.MODE_PRIVATE)

/** Daily limit in minutes. Default is 30. A limit of 0 means always blocked. */
fun loadLimit(c: Context, pkg: String): Int = prefs(c).getInt("limit_$pkg", 30)

fun saveLimit(c: Context, pkg: String, minutes: Int) =
    prefs(c).edit().putInt("limit_$pkg", minutes).apply()

data class TimeWindow(val startMin: Int, val endMin: Int)   // minutes since midnight

fun loadWindows(c: Context, pkg: String): List<TimeWindow> {
    val raw = prefs(c).getString("windows_$pkg", "") ?: ""
    if (raw.isBlank()) return emptyList()
    return raw.split(";").mapNotNull {
        val p = it.split("-")
        val s = p.getOrNull(0)?.toIntOrNull()
        val e = p.getOrNull(1)?.toIntOrNull()
        if (s != null && e != null) TimeWindow(s, e) else null
    }
}

fun saveWindows(c: Context, pkg: String, list: List<TimeWindow>) =
    prefs(c).edit()
        .putString("windows_$pkg", list.joinToString(";") { "${it.startMin}-${it.endMin}" })
        .apply()

private fun inWindow(w: TimeWindow, now: Int): Boolean =
    if (w.startMin < w.endMin) now >= w.startMin && now < w.endMin   // normal: 12:00-13:00
    else now >= w.startMin || now < w.endMin                         // crosses midnight: 22:00-01:00

fun isAllowedNow(c: Context, pkg: String): Boolean {
    val windows = loadWindows(c, pkg)
    if (windows.isEmpty()) return true
    val cal = Calendar.getInstance()
    val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return windows.any { inWindow(it, now) }
}

fun formatMin(m: Int): String = "%02d:%02d".format(m / 60, m % 60)