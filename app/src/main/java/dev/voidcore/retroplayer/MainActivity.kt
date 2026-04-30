package dev.voidcore.retroplayer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var playPauseButton: ImageButton
    private lateinit var networkErrorRing: View
    private lateinit var timeText: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var nowPlayingText: TextView
    private lateinit var prefs: SharedPreferences

    private var isServiceRunning = false
    private var isServerReceiverRegistered = false
    private var isNetworkAvailable = true
    private var wasPlayingBeforeNetworkLoss = false
    private var hasStreamError = false

    private val connectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }

    private val timeFormatter = SimpleDateFormat("HH:mm • dd MMM yyyy", Locale.getDefault())
    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            timeText.text = timeFormatter.format(Date())
            timeHandler.postDelayed(this, 1000)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                isNetworkAvailable = true
                updateErrorRing()
                if (wasPlayingBeforeNetworkLoss && !isServiceRunning) {
                    wasPlayingBeforeNetworkLoss = false
                    startForegroundService(Intent(this@MainActivity, RadioService::class.java))
                }
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                isNetworkAvailable = false
                if (isServiceRunning) wasPlayingBeforeNetworkLoss = true
                updateErrorRing()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)

        playPauseButton = findViewById(R.id.playPauseButton)
        networkErrorRing = findViewById(R.id.networkErrorRing)
        timeText = findViewById(R.id.timeText)
        serverStatusText = findViewById(R.id.serverStatusText)
        nowPlayingText = findViewById(R.id.nowPlayingText)

        serverStatusText.text = "Server: idle"

        ensureDefaultStation()
        updateNowPlayingLabel()

        registerReceiver(
            serverStatusReceiver,
            IntentFilter(ACTION_SERVER_STATUS),
            Context.RECEIVER_NOT_EXPORTED
        )
        isServerReceiverRegistered = true

        createNotificationChannel()
        timeHandler.post(timeRunnable)

        checkInitialNetworkState()
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        playPauseButton.setOnClickListener {
            if (isServiceRunning) {
                stopService(Intent(this, RadioService::class.java))
            } else {
                wasPlayingBeforeNetworkLoss = false
                startForegroundService(Intent(this, RadioService::class.java))
            }
        }

        findViewById<ImageButton>(R.id.editStationsButton).setOnClickListener {
            StationManagerDialog().show(supportFragmentManager, "stations")
        }
    }

    private fun ensureDefaultStation() {
        if (prefs.getString("stream_url", null) == null) {
            val first = loadStations(prefs).firstOrNull()
            if (first != null) {
                prefs.edit().putString("stream_url", first.url).apply()
            }
        }
    }

    private fun checkInitialNetworkState() {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        isNetworkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        updateErrorRing()
    }

    private fun updateErrorRing() {
        val showRing = !isNetworkAvailable || hasStreamError
        networkErrorRing.visibility = if (showRing) View.VISIBLE else View.GONE
    }

    private fun updatePlayPauseIcon() {
        playPauseButton.setImageResource(
            if (isServiceRunning) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    fun onStationSelectedFromDialog(station: Station) {
        nowPlayingText.text = "Now playing: ${station.name}"
        if (isServiceRunning) {
            stopService(Intent(this, RadioService::class.java))
            startForegroundService(Intent(this, RadioService::class.java))
        }
    }

    private fun updateNowPlayingLabel() {
        val url = prefs.getString("stream_url", "") ?: ""
        val station = loadStations(prefs).find { it.url == url }
        nowPlayingText.text = "Now playing: ${station?.name ?: "—"}"
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(timeRunnable)
        if (isServerReceiverRegistered) {
            unregisterReceiver(serverStatusReceiver)
            isServerReceiverRegistered = false
        }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // already unregistered
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "radio_playback",
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private val serverStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_SERVER_STATUS) return
            val status = intent.getStringExtra(EXTRA_SERVER_STATUS)
            when (status) {
                "playing" -> {
                    isServiceRunning = true
                    hasStreamError = false
                    serverStatusText.text = "Server: playing"
                }
                "error" -> {
                    isServiceRunning = true
                    hasStreamError = true
                    serverStatusText.text = "Server: reconnecting…"
                }
                "idle" -> {
                    isServiceRunning = false
                    hasStreamError = false
                    serverStatusText.text = "Server: idle"
                }
            }
            updatePlayPauseIcon()
            updateErrorRing()
        }
    }
}
