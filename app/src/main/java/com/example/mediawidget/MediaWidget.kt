package com.example.mediawidget

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider

private val CMD = ActionParameters.Key<String>("cmd")

class MediaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediaWidget()
}

class MediaWidget : GlanceAppWidget() {
    // Glance only recomposes a live session when OBSERVABLE state changes, so every refresh bumps a tick in
    // the widget's own state (see refreshWidgets). Without this, updates right after a tap were ignored.
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact   // layout adapts to whatever size the user drags to

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }
}

private val TICK = longPreferencesKey("tick")

/** Push the current MediaStore / prefs state to every placed widget. */
suspend fun refreshWidgets(ctx: Context) {
    val ids = GlanceAppWidgetManager(ctx).getGlanceIds(MediaWidget::class.java)
    ids.forEach { id ->
        updateAppWidgetState(ctx, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply { this[TICK] = System.nanoTime() }
        }
        MediaWidget().update(ctx, id)
    }
}

/** Monochrome palette built from the user's settings. */
private class Palette(private val p: WidgetPrefs) {
    private fun cp(day: Color, night: Color): ColorProvider = when (p.theme) {
        1 -> ColorProvider(day, day)
        2 -> ColorProvider(night, night)
        else -> ColorProvider(day, night)
    }
    val fg = cp(Color.Black, Color.White)
    val dim = cp(Color(0x99000000), Color(0x99FFFFFF))
    val inv = cp(Color.White, Color.Black)
    val track = cp(Color(0x33000000), Color(0x33FFFFFF))
    val bg: ColorProvider = run {
        val a = p.opacity / 100f
        val f = p.frost / 100f
        val day = lerp(Color(0xFFEDEDED), Color.White, f).copy(alpha = a)
        val night = lerp(Color(0xFF1A1A1A), Color(0xFF6A6A6A), f * 0.6f).copy(alpha = a)
        cp(day, night)
    }
}

@Composable
private fun Content(ctx: Context) {
    val size = LocalSize.current
    @Suppress("UNUSED_VARIABLE")
    val tick = currentState<Preferences>()[TICK]   // makes this composition depend on refreshWidgets()
    val prefs = WidgetPrefs(ctx)
    val pal = Palette(prefs)
    val info = MediaStore.info
    val hasAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    // Tapping the widget background opens the app that is playing
    val launch = info?.let {
        ctx.packageManager.getLaunchIntentForPackage(it.pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    var root = GlanceModifier.fillMaxSize().background(pal.bg).cornerRadius(prefs.corner.dp).padding(12.dp)
    if (launch != null) root = root.clickable(actionStartActivity(launch))

    Box(modifier = root, contentAlignment = Alignment.Center) {
        when {
            !hasAccess -> Text(
                "Tap to allow notification access",
                modifier = GlanceModifier.clickable(
                    actionStartActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                ),
                style = TextStyle(color = pal.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            info == null -> Text("No media playing", style = TextStyle(color = pal.dim, fontSize = 13.sp))
            else -> Player(info, size.width, size.height, pal, prefs)
        }
    }
}

@Composable
private fun Player(info: MediaInfo, w: Dp, h: Dp, pal: Palette, prefs: WidgetPrefs) {
    val compact = h < 125.dp
    val showArt = w >= 180.dp
    val artSize = (h * 0.30f).coerceIn(36.dp, 72.dp)
    val sideBox = if (compact) 26.dp else 30.dp
    val sideIcon = if (compact) 18.dp else 22.dp
    val centerBox = if (compact) 30.dp else 36.dp
    val centerIcon = if (compact) 24.dp else 30.dp
    val pos = info.positionNow()
    val progress = if (info.durationMs > 0) pos.toFloat() / info.durationMs else 0f
    val titleSize = if (w < 160.dp) 11.sp else 14.sp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text("Playing on ${info.appName}", maxLines = 1,
            style = TextStyle(color = pal.dim, fontSize = 10.sp, fontWeight = FontWeight.Medium))

        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            if (showArt) {
                val artMod = GlanceModifier.size(artSize).cornerRadius(artSize / 2)
                if (info.art != null) {
                    Image(ImageProvider(info.art), "Thumbnail", artMod, contentScale = ContentScale.Crop)
                } else {
                    Box(artMod.background(pal.track), contentAlignment = Alignment.Center) {
                        Image(ImageProvider(R.drawable.ic_music), null, GlanceModifier.size(artSize / 2),
                            colorFilter = ColorFilter.tint(pal.fg))
                    }
                }
                Spacer(GlanceModifier.width(10.dp))
            }
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(info.title, maxLines = if (compact) 1 else 2,
                    style = TextStyle(color = pal.fg, fontSize = titleSize, fontWeight = FontWeight.Bold))
                if (!compact && info.artist.isNotBlank())
                    Text(info.artist, maxLines = 1, style = TextStyle(color = pal.dim, fontSize = 11.sp))
            }
        }

        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = pal.fg, backgroundColor = pal.track,
        )
        if (!compact) {
            Row(GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(fmt(pos), style = TextStyle(color = pal.dim, fontSize = 10.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text(if (info.durationMs > 0) fmt(info.durationMs) else "00:00",
                    style = TextStyle(color = pal.dim, fontSize = 10.sp))
            }
        }
        Spacer(GlanceModifier.height(2.dp))

        // Plain icons: no circles or backgrounds, centre one slightly bigger
        Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically) {
            IconBtn(if (prefs.skipTracks) R.drawable.ic_prev else R.drawable.ic_rewind,
                "rewind", "Back", sideBox, sideIcon, pal.fg)
            Spacer(GlanceModifier.width(20.dp))
            IconBtn(if (info.isPlaying) R.drawable.ic_pause else R.drawable.ic_play, "toggle",
                if (info.isPlaying) "Pause" else "Play", centerBox, centerIcon, pal.fg)
            Spacer(GlanceModifier.width(20.dp))
            IconBtn(if (prefs.skipTracks) R.drawable.ic_next else R.drawable.ic_forward,
                "forward", "Forward", sideBox, sideIcon, pal.fg)
        }
    }
}

@Composable
private fun IconBtn(res: Int, cmd: String, desc: String, box: Dp, icon: Dp, tint: ColorProvider) {
    Box(
        GlanceModifier.size(box).clickable(actionRunCallback<MediaAction>(actionParametersOf(CMD to cmd))),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(res), desc, GlanceModifier.size(icon), colorFilter = ColorFilter.tint(tint))
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

class MediaAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = MediaStore.controller ?: return
        val info = MediaStore.info
        val prefs = WidgetPrefs(context)
        val t = c.transportControls
        val step = prefs.seekSec * 1000L
        when (parameters[CMD]) {
            "toggle" -> {
                if (info?.isPlaying == true) t.pause() else t.play()
                // Show the new state immediately; the app's own callback confirms or corrects it.
                info?.let {
                    MediaStore.info = it.copy(
                        isPlaying = !it.isPlaying,
                        basePositionMs = it.positionNow(),
                        baseTimeMs = SystemClock.elapsedRealtime(),
                    )
                }
                refreshWidgets(context)
            }
            "rewind" -> if (prefs.skipTracks) t.skipToPrevious()
                else info?.let { t.seekTo((it.positionNow() - step).coerceAtLeast(0)) }
            "forward" -> if (prefs.skipTracks) t.skipToNext()
                else info?.let {
                    val max = if (it.durationMs > 0) it.durationMs else Long.MAX_VALUE
                    t.seekTo((it.positionNow() + step).coerceAtMost(max))
                }
        }
    }
}
