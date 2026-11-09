package com.example.mediawidget

import android.content.ComponentName
import android.graphics.*
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.*

/**
 * Being an enabled NotificationListenerService is what lets us call
 * MediaSessionManager.getActiveSessions() — i.e. see EVERY app's media session.
 */
class MediaListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var msm: MediaSessionManager
    private val me by lazy { ComponentName(this, MediaListenerService::class.java) }
    private var controllers: List<MediaController> = emptyList()
    private var artKey: String? = null
    private var artBitmap: Bitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { refresh(it) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    override fun onListenerConnected() {
        msm = getSystemService(MediaSessionManager::class.java)
        msm.addOnActiveSessionsChangedListener(sessionsListener, me)
        refresh(msm.getActiveSessions(me))
        scope.launch {                       // tick the progress bar while playing
            val prefs = WidgetPrefs(applicationContext)
            while (isActive) {
                val iv = prefs.intervalMs
                delay(if (iv > 0) iv.toLong() else 1000L)
                if (iv > 0 && MediaStore.info?.isPlaying == true) publish()
            }
        }
    }

    override fun onListenerDisconnected() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { it.unregisterCallback(callback) }
        scope.coroutineContext.cancelChildren()
        MediaStore.info = null; MediaStore.controller = null
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun refresh(list: List<MediaController>?) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list.orEmpty()
        controllers.forEach { it.registerCallback(callback) }
        publish()
    }

    private fun publish() {
        // Prefer whatever is playing; otherwise the most recent session.
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null }
        MediaStore.controller = c
        if (c == null) {
            MediaStore.info = null
        } else {
            val md = c.metadata
            val st = c.playbackState
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Unknown"
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
            val key = "${c.packageName}|$title|$artist"
            if (key != artKey) {
                artKey = key
                val raw = md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                artBitmap = raw?.let { toThumb(it) }
            }
            MediaStore.info = MediaInfo(
                title = title, artist = artist, art = artBitmap,
                isPlaying = st?.state == PlaybackState.STATE_PLAYING,
                basePositionMs = st?.position ?: 0L,
                baseTimeMs = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
                speed = st?.playbackSpeed ?: 1f,
                durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                pkg = c.packageName,
                appName = appLabel(c.packageName),
            )
        }
        scope.launch { refreshWidgets(applicationContext) }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })

    /** Downscale (widget IPC has a size limit); keeps the original colours. */
    private fun toThumb(src: Bitmap, max: Int = 320): Bitmap {
        val s = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
        if (s >= 1f) return src
        val w = (src.width * s).toInt().coerceAtLeast(1)
        val h = (src.height * s).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
