package dev.voidcore.retroplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Create Notification Channel (Required for Android 8.0+)
        createNotificationChannel()

        // 2. Setup Data Source
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("RetroPlayer/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // 3. Initialize ExoPlayer with Automatic Audio Focus handling
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // Automatically handles Audio Focus
            )
            .setHandleAudioBecomingNoisy(true) // Pauses when headphones unplugged
            .build()

        // 4. Initialize MediaSession
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start Foreground immediately to prevent system killing service
        startForeground(1, createNotification())

        broadcastServerStatus("playing")
        startPlayback(BuildConfig.PRIMARY_STREAM_URL)

        return START_STICKY
    }

    private fun startPlayback(url: String) {
        val mediaItem = MediaItem.fromUri(url)

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RetroPlayer")
            .setContentText("Playing Live Radio")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this exists!
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun broadcastServerStatus(status: String) {
        val intent = Intent(ACTION_SERVER_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_SERVER_STATUS, status)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        broadcastServerStatus("idle")
        super.onDestroy()
    }
}