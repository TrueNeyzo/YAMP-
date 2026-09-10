package com.neyzo.yamp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: NotificationManager

    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private val artworkGeneration = AtomicLong(0L)

    @Volatile private var currentTitle = "YAMP"
    @Volatile private var currentArtist = ""
    @Volatile private var currentAlbum = ""
    @Volatile private var currentDurationMs = 0L
    @Volatile private var currentPositionMs = 0L
    @Volatile private var currentPlaybackRate = 1f
    @Volatile private var currentPlaying = false
    @Volatile private var currentArtwork: Bitmap? = null

    companion object {
        private const val CHANNEL_ID = "yamp_playback"
        private const val NOTIFICATION_ID = 29015
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createMediaChannel()
        createMediaSession()

        webView = WebView(this)
        setContentView(webView)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
        }

        // Safe here because YAMP loads its own local HTML, while normal http/https
        // navigation is always handed to the external browser below.
        webView.addJavascriptInterface(NativeMediaBridge(), "NativeMedia")
        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()

                if (scheme == "http" || scheme == "https") {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
                return false
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun createMediaChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "YAMP playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "YAMP media controls"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createMediaSession() {
        mediaSession = MediaSession(this, "YAMP").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = runJs("window.yampNativePlay&&window.yampNativePlay()")
                override fun onPause() = runJs("window.yampNativePause&&window.yampNativePause()")
                override fun onSkipToNext() = runJs("window.yampNativeNext&&window.yampNativeNext()")
                override fun onSkipToPrevious() = runJs("window.yampNativePrev&&window.yampNativePrev()")
                override fun onSeekTo(pos: Long) =
                    runJs("window.yampNativeSeekTo&&window.yampNativeSeekTo(${pos.coerceAtLeast(0L)})")
            })
            isActive = true
        }

        publishPlaybackState()
        publishMetadata()
    }

    private fun runJs(js: String) {
        if (!::webView.isInitialized) return
        runOnUiThread {
            try { webView.evaluateJavascript(js, null) } catch (_: Throwable) {}
        }
    }

    private fun fallbackArtwork(): Bitmap? {
        return try {
            BitmapFactory.decodeResource(resources, R.drawable.yamp_icon)
        } catch (_: Throwable) {
            null
        }
    }

    private fun publishMetadata() {
        if (!::mediaSession.isInitialized) return

        val art = currentArtwork ?: fallbackArtwork()
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, currentAlbum)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, currentDurationMs)

        if (art != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, art)
            builder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art)
        }

        mediaSession.setMetadata(builder.build())
        publishNotification()
    }

    private fun publishPlaybackState() {
        if (!::mediaSession.isInitialized) return

        val actions =
            PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO

        val state = if (currentPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val speed = if (currentPlaying) currentPlaybackRate else 0f

        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, currentPositionMs.coerceAtLeast(0L), speed)
                .build()
        )

        publishNotification()
    }

    private fun publishNotification() {
        if (!::mediaSession.isInitialized || !::notificationManager.isInitialized) return

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val art = currentArtwork ?: fallbackArtwork()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(
                when {
                    currentArtist.isNotBlank() && currentAlbum.isNotBlank() ->
                        "$currentArtist · $currentAlbum"
                    currentArtist.isNotBlank() -> currentArtist
                    else -> "YAMP"
                }
            )
            .setLargeIcon(art)
            .setContentIntent(contentIntent)
            .setOngoing(currentPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
            )
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {}
    }

    private fun loadArtwork(url: String, generation: Long) {
        if (url.isBlank() || !url.startsWith("http")) {
            currentArtwork = fallbackArtwork()
            publishMetadata()
            return
        }

        artworkExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 7000
                connection.readTimeout = 7000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "YAMP/29.15 Android")
                connection.connect()

                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null && artworkGeneration.get() == generation) {
                    runOnUiThread {
                        if (artworkGeneration.get() == generation) {
                            currentArtwork = bitmap
                            publishMetadata()
                        }
                    }
                }
            } catch (_: Throwable) {
                if (artworkGeneration.get() == generation) {
                    runOnUiThread {
                        if (artworkGeneration.get() == generation) {
                            currentArtwork = fallbackArtwork()
                            publishMetadata()
                        }
                    }
                }
            } finally {
                try { connection?.disconnect() } catch (_: Throwable) {}
            }
        }
    }

    inner class NativeMediaBridge {

        @JavascriptInterface
        fun ready() {
            runOnUiThread {
                publishMetadata()
                publishPlaybackState()
            }
        }

        @JavascriptInterface
        fun updateMetadata(json: String) {
            try {
                val obj = JSONObject(json)
                currentTitle = obj.optString("title", "YAMP").ifBlank { "YAMP" }
                currentArtist = obj.optString("artist", "")
                currentAlbum = obj.optString("album", "")
                val hintedDuration = obj.optLong("durationMs", 0L)
                if (hintedDuration > 0L) currentDurationMs = hintedDuration

                // Immediately show the YAMP logo, then replace it with the
                // real cover only if that download succeeds for THIS track.
                val generation = artworkGeneration.incrementAndGet()
                currentArtwork = fallbackArtwork()

                runOnUiThread { publishMetadata() }
                loadArtwork(obj.optString("artwork", ""), generation)
            } catch (_: Throwable) {}
        }

        @JavascriptInterface
        fun updatePlaybackState(state: String) {
            currentPlaying = state.equals("playing", ignoreCase = true)
            runOnUiThread { publishPlaybackState() }
        }

        @JavascriptInterface
        fun updatePosition(positionMs: Long, durationMs: Long, playbackRate: Double) {
            currentPositionMs = positionMs.coerceAtLeast(0L)
            if (durationMs > 0L) currentDurationMs = durationMs
            currentPlaybackRate = playbackRate.toFloat().takeIf { it > 0f } ?: 1f

            runOnUiThread {
                publishPlaybackState()
                // Duration lives in metadata, so refresh it after the browser
                // discovers the final media duration.
                publishMetadata()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try { notificationManager.cancel(NOTIFICATION_ID) } catch (_: Throwable) {}
        try { mediaSession.isActive = false } catch (_: Throwable) {}
        try { mediaSession.release() } catch (_: Throwable) {}
        artworkExecutor.shutdownNow()

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
