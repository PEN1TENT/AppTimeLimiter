package com.example.apptimelimiter

import android.content.Context

private fun prefs(c: Context) = c.getSharedPreferences("rules", Context.MODE_PRIVATE)

/** Daily limit in minutes. Default is 30. A limit of 0 means always blocked. */
fun loadLimit(c: Context, pkg: String): Int = prefs(c).getInt("limit_$pkg", 30)

fun saveLimit(c: Context, pkg: String, minutes: Int) =
    prefs(c).edit().putInt("limit_$pkg", minutes).apply()