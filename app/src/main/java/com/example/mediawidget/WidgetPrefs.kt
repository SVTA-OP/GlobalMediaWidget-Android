package com.example.mediawidget

import android.content.Context

/** User settings, shared by the settings screen, the service and the widget. */
class WidgetPrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    var opacity: Int       // background opacity, 0..100
        get() = sp.getInt("opacity", 60); set(v) = sp.edit().putInt("opacity", v).apply()
    var frost: Int         // frosted tint, 0..100 (a tint, not a real blur)
        get() = sp.getInt("frost", 0); set(v) = sp.edit().putInt("frost", v).apply()
    var corner: Int        // widget corner radius in dp
        get() = sp.getInt("corner", 28); set(v) = sp.edit().putInt("corner", v).apply()
    var theme: Int         // 0 = follow system, 1 = light, 2 = dark
        get() = sp.getInt("theme", 0); set(v) = sp.edit().putInt("theme", v).apply()
    var intervalMs: Int    // progress refresh while playing; 0 = only on playback events
        get() = sp.getInt("interval", 1000); set(v) = sp.edit().putInt("interval", v).apply()
    var skipTracks: Boolean // false = rewind/forward seek, true = previous/next track
        get() = sp.getBoolean("skip", false); set(v) = sp.edit().putBoolean("skip", v).apply()
    var seekSec: Int
        get() = sp.getInt("seekSec", 10); set(v) = sp.edit().putInt("seekSec", v).apply()
}
