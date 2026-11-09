package com.example.mediawidget

import android.graphics.Bitmap
import android.media.session.MediaController
import android.os.SystemClock

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val basePositionMs: Long,
    val baseTimeMs: Long,      // elapsedRealtime when basePositionMs was valid
    val speed: Float,
    val durationMs: Long,
    val pkg: String,
    val appName: String,
) {
    fun positionNow(): Long {
        val p = if (isPlaying) basePositionMs + ((SystemClock.elapsedRealtime() - baseTimeMs) * speed).toLong()
        else basePositionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Process-wide state shared by the listener service and the widget. */
object MediaStore {
    @Volatile var info: MediaInfo? = null
    @Volatile var controller: MediaController? = null
}
