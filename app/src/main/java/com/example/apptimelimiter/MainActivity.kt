package com.example.apptimelimiter

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.apptimelimiter.ui.theme.AppTimeLimiterTheme
import kotlinx.coroutines.delay

// ---------- data helpers ----------

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
        .filter { it.packageName != context.packageName }
        .sortedBy { it.label.lowercase() }
}

fun loadSelected(context: Context): Set<String> =
    context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        .getStringSet("selected", emptySet())!!.toSet()

fun saveSelected(context: Context, selected: Set<String>) {
    context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        .edit().putStringSet("selected", selected).apply()
}

fun fmtMinutes(m: Int): String = when {
    m == 0 -> "Always blocked"
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}

// ---------- activity ----------

class MainActivity : ComponentActivity() {
    private var refreshTick by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTimeLimiterTheme { AppRoot(refreshTick) }
        }
    }
}

@Composable
fun AppRoot(refreshTick: Int) {
    val context = LocalContext.current
    val apps = remember { loadApps(context) }
    var selected by remember { mutableStateOf(loadSelected(context)) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        BackHandler { showPicker = false }
        PickerScreen(
            apps = apps,
            selected = selected,
            onChange = { selected = it; saveSelected(context, it) },
            onDone = { showPicker = false }
        )
    } else {
        HomeScreen(
            apps = apps,
            selected = selected,
            refreshTick = refreshTick,
            onAdd = { showPicker = true },
            onRemove = { pkg ->
                selected = selected - pkg
                saveSelected(context, selected)
            }
        )
    }
}

// ---------- home ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    apps: List<InstalledApp>,
    selected: Set<String>,
    refreshTick: Int,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }       // auto-refresh usage
    var version by remember { mutableIntStateOf(0) }    // bump after rule edits
    var editing by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) {
        while (true) { delay(30_000); tick++ }
    }

    val hasAccess = remember(refreshTick) { hasUsageAccess(context) }
    val hasBlocker = remember(refreshTick) { isBlockerEnabled(context) }
    val usage = remember(tick, refreshTick, hasAccess) {
        if (hasAccess) usageTodayMs(context) else emptyMap()
    }
    val limited = apps.filter { it.packageName in selected }

    Scaffold(
        topBar = { TopAppBar(title = { Text("App Limits") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("+ Add app") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasAccess || !hasBlocker) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                            Text("Limits won't work until both permissions are on.")
                            if (!hasAccess) {
                                Button(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }) { Text("1. Grant usage access") }
                            }
                            if (!hasBlocker) {
                                Button(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }) { Text("2. Enable blocker") }
                            }
                        }
                    }
                }
            }

            if (limited.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No limits yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap “Add app” to pick an app to limit.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(limited, key = { it.packageName }) { app ->
                AppCard(app, usage, version) { editing = app }
            }
        }
    }

    editing?.let { app ->
        ModalBottomSheet(onDismissRequest = { editing = null }) {
            EditSheet(
                app = app,
                onChanged = { version++ },
                onStop = { onRemove(app.packageName); editing = null }
            )
        }
    }
}

@Composable
fun AppCard(app: InstalledApp, usage: Map<String, Long>, version: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val pkg = app.packageName
    val usedMin = ((usage[pkg] ?: 0L) / 60_000).toInt()
    val limit = remember(version) { loadLimit(context, pkg) }
    val windows = remember(version) { loadWindows(context, pkg) }
    val over = usedMin >= limit
    val blockedNow = over || !isAllowedNow(context, pkg)
    val progress = if (limit == 0) 1f else (usedMin / limit.toFloat()).coerceIn(0f, 1f)

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (limit == 0) "Always blocked" else "$usedMin / $limit min today",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (blockedNow) {
                    Text(
                        "Blocked now",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (over) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (windows.isEmpty()) "Allowed any time"
                else "Allowed " + windows.joinToString(", ") {
                    "${formatMin(it.startMin)}–${formatMin(it.endMin)}"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------- edit sheet ----------

@Composable
fun EditSheet(app: InstalledApp, onChanged: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val pkg = app.packageName
    var limit by remember { mutableFloatStateOf(loadLimit(context, pkg).toFloat()) }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(app.label, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(20.dp))
        Text("Daily limit", style = MaterialTheme.typography.labelLarge)
        Text(fmtMinutes(limit.toInt()), style = MaterialTheme.typography.headlineSmall)
        Slider(
            value = limit,
            onValueChange = { limit = it },
            onValueChangeFinished = {
                saveLimit(context, pkg, limit.toInt())
                onChanged()
            },
            valueRange = 0f..240f,
            steps = 47   // 5-minute steps
        )

        Spacer(Modifier.height(12.dp))
        Text("Allowed times", style = MaterialTheme.typography.labelLarge)
        WindowsEditor(pkg, onChanged)

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Stop limiting this app") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun WindowsEditor(pkg: String, onChanged: () -> Unit) {
    val context = LocalContext.current
    var windows by remember(pkg) { mutableStateOf(loadWindows(context, pkg)) }

    fun pickTime(title: String, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, 12, 0, true)
            .apply { setTitle(title) }
            .show()
    }

    Column {
        if (windows.isEmpty()) {
            Text("Any time of day", style = MaterialTheme.typography.bodyMedium)
        }
        windows.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMin(w.startMin)} – ${formatMin(w.endMin)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                TextButton(onClick = {
                    windows = windows - w
                    saveWindows(context, pkg, windows)
                    onChanged()
                }) { Text("Remove") }
            }
        }
        TextButton(onClick = {
            pickTime("Allowed from") { start ->
                pickTime("Allowed until") { end ->
                    if (start != end) {
                        windows = windows + TimeWindow(start, end)
                        saveWindows(context, pkg, windows)
                        onChanged()
                    }
                }
            }
        }) { Text("+ Add allowed window") }
    }
}

// ---------- add-app picker ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(
    apps: List<InstalledApp>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onDone: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add apps") },
                actions = { TextButton(onClick = onDone) { Text("Done") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChange(if (checked) selected - app.packageName else selected + app.packageName)
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                onChange(if (it) selected + app.packageName else selected - app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}