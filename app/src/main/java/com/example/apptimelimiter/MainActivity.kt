package com.example.apptimelimiter

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTimeLimiterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    AppPickerScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun AppPickerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps = remember { loadApps(context) }
    var selected by remember { mutableStateOf(loadSelected(context)) }

    Column(modifier) {
        Text(
            "Select apps to limit (${selected.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn {
            items(apps, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            selected = if (isChecked) selected + app.packageName
                            else selected - app.packageName
                            saveSelected(context, selected)
                        }
                    )
                }
            }
        }
    }
}