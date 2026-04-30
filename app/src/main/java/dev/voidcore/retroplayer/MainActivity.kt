package dev.voidcore.retroplayer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var timeText: TextView
    private lateinit var serverStatusText: TextView
    private var isServerReceiverRegistered = false

    // Time handling
    private val timeFormatter =
        SimpleDateFormat("HH:mm • dd MMM yyyy", Locale.getDefault())

    private val timeHandler = Handler(Looper.getMainLooper())

    private val timeRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            timeText.text = timeFormatter.format(now)
            timeHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ MUST be first
        setContentView(R.layout.activity_main)

        // Views (NOW they exist)
        val playButton = findViewById<ImageButton>(R.id.playButton)
        val stopButton = findViewById<ImageButton>(R.id.stopButton)
        timeText = findViewById(R.id.timeText)
        serverStatusText = findViewById(R.id.serverStatusText)

        // Optional: default text so it’s never "-"
        serverStatusText.text = "Server: idle"

        // Register receiver AFTER views exist
        registerReceiver(
            serverStatusReceiver,
            IntentFilter(ACTION_SERVER_STATUS),
            Context.RECEIVER_NOT_EXPORTED
        )
        isServerReceiverRegistered = true

        createNotificationChannel()

        // Time updates
        timeHandler.post(timeRunnable)

        playButton.setOnClickListener {
            startForegroundService(Intent(this, RadioService::class.java))
        }

        stopButton.setOnClickListener {
            stopService(Intent(this, RadioService::class.java))
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(timeRunnable)


        if (isServerReceiverRegistered) {
            unregisterReceiver(serverStatusReceiver)
            isServerReceiverRegistered = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "radio_playback",
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private val serverStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SERVER_STATUS) {
                val status = intent.getStringExtra(EXTRA_SERVER_STATUS)
                serverStatusText.text = "Server: $status"
            }
        }
    }
}
