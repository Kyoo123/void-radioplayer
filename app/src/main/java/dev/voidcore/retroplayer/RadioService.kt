package dev.voidcore.retroplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import java.net.HttpURLConnection
import java.net.URL

const val ACTION_SERVER_STATUS = "dev.voidcore.retroplayer.SERVER_STATUS"
const val EXTRA_SERVER_STATUS = "status"
const val CHANNEL_ID = "radio_playback"

@androidx.media3.common.util.UnstableApi
class RadioService : Service() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var currentUrl: String = ""
    private var hadPlaybackError = false

    private val connectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (hadPlaybackError) {
                hadPlaybackError = false
                startPlayback(currentUrl)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("RetroPlayer/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Icy-MetaData" to "1",
                "Accept" to "*/*"
            ))

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                hadPlaybackError = true
                broadcastServerStatus("error")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    hadPlaybackError = false
                    broadcastServerStatus("playing")
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        val prefs = getSharedPreferences("stream_prefs", MODE_PRIVATE)
        currentUrl = prefs.getString("stream_url", BuildConfig.PRIMARY_STREAM_URL)
            ?: BuildConfig.PRIMARY_STREAM_URL

        broadcastServerStatus("playing")
        startPlayback(currentUrl)

        return START_STICKY
    }

    private fun startPlayback(url: String) {
        if (url.isBlank()) return
        val path = url.substringBefore("?").lowercase()
        when {
            // Known audio formats — ExoPlayer handles directly via ProgressiveMediaSource
            path.endsWith(".mp3") || path.endsWith(".aac") || path.endsWith(".ogg") ||
            path.endsWith(".flac") || path.endsWith(".wav") || path.endsWith(".opus") ->
                playDirect(url)
            // Adaptive formats — ExoPlayer knows from extension alone
            path.endsWith(".m3u8") -> playWithMime(url, MimeTypes.APPLICATION_M3U8)
            path.endsWith(".mpd")  -> playWithMime(url, MimeTypes.APPLICATION_MPD)
            // Plain playlist — must resolve to the actual stream URL first
            path.endsWith(".m3u")  -> resolveM3uAndPlay(url)
            // Unknown — probe Content-Type via HEAD so ExoPlayer gets the right hint
            else -> probeAndPlay(url)
        }
    }

    private fun playDirect(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    private fun playWithMime(url: String, mimeType: String) {
        player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mimeType).build())
        player.prepare()
        player.play()
    }

    private fun probeAndPlay(url: String) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("User-Agent", "RetroPlayer/1.0 (Android)")
                conn.setRequestProperty("Accept", "*/*")
                conn.instanceFollowRedirects = true
                conn.connect()
                val ct = conn.contentType?.lowercase() ?: ""
                val finalUrl = conn.url.toString()
                val finalPath = finalUrl.substringBefore("?").lowercase()
                conn.disconnect()

                handler.post {
                    when {
                        finalPath.endsWith(".m3u8") ||
                        (ct.contains("mpegurl") && ct.contains("apple")) ->
                            playWithMime(finalUrl, MimeTypes.APPLICATION_M3U8)
                        finalPath.endsWith(".mpd") || ct.contains("dash") ->
                            playWithMime(finalUrl, MimeTypes.APPLICATION_MPD)
                        finalPath.endsWith(".m3u") || ct.contains("mpegurl") ->
                            resolveM3uAndPlay(finalUrl)
                        else ->
                            playDirect(finalUrl)
                    }
                }
            } catch (e: Exception) {
                // HEAD not supported by server — try direct and let ExoPlayer sniff
                handler.post { playDirect(url) }
            }
        }.start()
    }

    private fun resolveM3uAndPlay(m3uUrl: String) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val conn = URL(m3uUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "RetroPlayer/1.0 (Android)")
                conn.setRequestProperty("Accept", "*/*")
                conn.instanceFollowRedirects = true
                val contentType = conn.contentType?.lowercase() ?: ""
                val content = conn.inputStream.bufferedReader().use { it.readText() }
                val finalUrl = conn.url.toString()
                conn.disconnect()

                val looksLikeM3u = contentType.contains("mpegurl") ||
                        content.trimStart().startsWith("#EXTM3U") ||
                        content.trimStart().startsWith("#EXTINF")

                if (looksLikeM3u) {
                    val channels = parseM3u(content)
                    // Skip non-HTTP entries (metadata stubs, local paths, etc.)
                    val streamUrl = channels.firstOrNull { (_, u) ->
                        u.startsWith("http://") || u.startsWith("https://")
                    }?.second
                    if (streamUrl != null) {
                        handler.post { startPlayback(streamUrl) }
                        return@Thread
                    }
                }
                // Not a playlist (server returned audio directly, possibly after a redirect)
                handler.post { playDirect(finalUrl) }
            } catch (e: Exception) {
                broadcastServerStatus("error")
            }
        }.start()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoidRadio")
            .setContentText("Playing Live Radio")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for radio playback"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun broadcastServerStatus(status: String) {
        sendBroadcast(Intent(ACTION_SERVER_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_SERVER_STATUS, status)
        })
    }

    override fun onDestroy() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // already unregistered
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        broadcastServerStatus("idle")
        super.onDestroy()
    }
}
