package com.example.apptimelimiter

import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.apptimelimiter.ui.theme.AppTimeLimiterTheme

data class InstalledApp(val label: String, val packageName: String, val icon: ImageBitmap)

fun loadApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map {
            InstalledApp(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

fun loadSelected(context: Context): Set<String> =
    context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        .getStringSet("selected", emptySet())!!.toSet()

fun saveSelected(context: Context, selected: Set<String>) {
    context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        .edit().putStringSet("selected", selected).apply()
}

class MainActivity : ComponentActivity() {
    private var refreshTick by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshTick++   // re-check permission and usage when returning to the app
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTimeLimiterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    AppPickerScreen(refreshTick, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun AppPickerScreen(refreshTick: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps = remember { loadApps(context) }
    var selected by remember { mutableStateOf(loadSelected(context)) }
    var limits by remember {
        mutableStateOf(loadSelected(context).associateWith { loadLimit(context, it) })
    }
    val hasAccess = remember(refreshTick) { hasUsageAccess(context) }
    val hasBlocker = remember(refreshTick) { isBlockerEnabled(context) }
    val usage = remember(refreshTick, hasAccess) {
        if (hasAccess) usageTodayMs(context) else emptyMap()
    }

    Column(modifier) {
        if (!hasAccess) {
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                modifier = Modifier.padding(16.dp)
            ) { Text("Grant usage access") }
        }
        if (!hasBlocker) {
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) { Text("Enable blocker (Accessibility)") }
        }
        Text(
            "Select apps to limit (${selected.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn {
            items(apps, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                val minutes = (usage[app.packageName] ?: 0L) / 60_000
                val limit = limits[app.packageName] ?: 30
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label)
                        if (checked) {
                            if (hasAccess) {
                                Text("$minutes / $limit min today", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    val n = (limit - 5).coerceAtLeast(0)
                                    limits = limits + (app.packageName to n)
                                    saveLimit(context, app.packageName, n)
                                }) { Text("−5") }
                                Text("$limit min/day")
                                TextButton(onClick = {
                                    val n = limit + 5
                                    limits = limits + (app.packageName to n)
                                    saveLimit(context, app.packageName, n)
                                }) { Text("+5") }
                            }
                        }
                    }
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            selected = if (isChecked) selected + app.packageName
                            else selected - app.packageName
                            if (isChecked) {
                                limits = limits + (app.packageName to loadLimit(context, app.packageName))
                            }
                            saveSelected(context, selected)
                        }
                    )
                }
            }
        }
    }
}