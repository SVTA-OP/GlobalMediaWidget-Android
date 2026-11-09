package com.example.mediawidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Launcher screen AND the widget's configure/reconfigure screen. */
class SettingsActivity : ComponentActivity() {
    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // When opened as the widget's config screen, accept by default so Back keeps the widget.
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))

        setContent {
            val ctx = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            MaterialTheme(colorScheme = scheme) { SettingsScreen(hasAccess) }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }
}

@Composable
private fun SettingsScreen(hasAccess: Boolean) {
    val ctx = LocalContext.current
    val prefs = remember { WidgetPrefs(ctx) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { refreshWidgets(ctx) } }

    var opacity by remember { mutableIntStateOf(prefs.opacity) }
    var frost by remember { mutableIntStateOf(prefs.frost) }
    var corner by remember { mutableIntStateOf(prefs.corner) }
    var theme by remember { mutableIntStateOf(prefs.theme) }
    var interval by remember { mutableIntStateOf(prefs.intervalMs) }
    var skip by remember { mutableStateOf(prefs.skipTracks) }
    var seek by remember { mutableIntStateOf(prefs.seekSec) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Media Widget", style = MaterialTheme.typography.headlineMedium)

            if (!hasAccess) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notification access is off, so the widget can't see what's playing.")
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) { Text("Open notification access") }
                    }
                }
            }

            Section("Appearance")
            SliderRow("Transparency (background opacity)", opacity, 0..100, "%",
                onChange = { opacity = it; prefs.opacity = it }, onDone = ::refresh)
            SliderRow("Frost tint", frost, 0..100, "%",
                onChange = { frost = it; prefs.frost = it }, onDone = ::refresh)
            Text("Frost lightens the background to look frosted. Android doesn't let widgets blur " +
                "what's behind them, so this is a tint, not a real blur.",
                style = MaterialTheme.typography.bodySmall)
            SliderRow("Corner radius", corner, 0..40, "dp",
                onChange = { corner = it; prefs.corner = it }, onDone = ::refresh)
            Text("Theme")
            ChipRow(listOf("System" to 0, "Light" to 1, "Dark" to 2), theme) {
                theme = it; prefs.theme = it; refresh()
            }

            Section("Updates")
            Text("Progress refresh while playing")
            ChipRow(listOf("On events only" to 0, "0.5 s" to 500, "1 s" to 1000, "2 s" to 2000, "5 s" to 5000), interval) {
                interval = it; prefs.intervalMs = it; refresh()
            }
            Text("Play/pause, track and thumbnail changes always update instantly. This only sets how " +
                "often the progress bar and time move. Faster uses a bit more battery.",
                style = MaterialTheme.typography.bodySmall)

            Section("Buttons")
            ChipRow(listOf("Rewind / forward (seek)" to false, "Previous / next track" to true), skip) {
                skip = it; prefs.skipTracks = it; refresh()
            }
            if (!skip) {
                Text("Seek step")
                ChipRow(listOf("5 s" to 5, "10 s" to 10, "15 s" to 15, "30 s" to 30), seek) {
                    seek = it; prefs.seekSec = it
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, suffix: String,
                      onChange: (Int) -> Unit, onDone: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            Text("$value$suffix")
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, v) ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
        }
    }
}
