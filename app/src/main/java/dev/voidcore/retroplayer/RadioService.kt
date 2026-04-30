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
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession

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
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
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
