package dev.voidcore.retroplayer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private val stations = listOf(
        "Retro Radio"         to "https://icast.connectmedia.hu/5001/live.mp3",
        "SomaFM: Groove Salad" to "https://ice1.somafm.com/groovesalad-256-mp3",
        "SomaFM: Lush"        to "https://ice1.somafm.com/lush-128-mp3",
        "KEXP 90.3"           to "https://kexp-mp3-128.streamguys1.com/kexp128.mp3",
        "Radio Paradise"      to "https://stream.radioparadise.com/mp3-128",
        "Custom..."           to ""
    )
    private val CUSTOM_INDEX get() = stations.size - 1

    // UI
    private lateinit var timeText: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var nowPlayingText: TextView
    private lateinit var stationSpinner: Spinner
    private lateinit var customUrlInput: EditText
    private lateinit var prefs: SharedPreferences
    private var isServiceRunning = false
    private var isServerReceiverRegistered = false

    // Time handling
    private val timeFormatter = SimpleDateFormat("HH:mm • dd MMM yyyy", Locale.getDefault())
    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            timeText.text = timeFormatter.format(Date())
            timeHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)

        val playButton = findViewById<ImageButton>(R.id.playButton)
        val stopButton = findViewById<ImageButton>(R.id.stopButton)
        val applyButton = findViewById<Button>(R.id.applyButton)
        timeText = findViewById(R.id.timeText)
        serverStatusText = findViewById(R.id.serverStatusText)
        nowPlayingText = findViewById(R.id.nowPlayingText)
        stationSpinner = findViewById(R.id.stationSpinner)
        customUrlInput = findViewById(R.id.customUrlInput)

        serverStatusText.text = "Server: idle"

        setupSpinner()

        registerReceiver(
            serverStatusReceiver,
            IntentFilter(ACTION_SERVER_STATUS),
            Context.RECEIVER_NOT_EXPORTED
        )
        isServerReceiverRegistered = true

        createNotificationChannel()
        timeHandler.post(timeRunnable)

        playButton.setOnClickListener {
            startForegroundService(Intent(this, RadioService::class.java))
        }

        stopButton.setOnClickListener {
            stopService(Intent(this, RadioService::class.java))
        }

        applyButton.setOnClickListener {
            applyStation()
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, stations.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        stationSpinner.adapter = adapter

        val savedIndex = prefs.getInt("station_index", 0)
        stationSpinner.setSelection(savedIndex)

        if (savedIndex == CUSTOM_INDEX) {
            customUrlInput.visibility = View.VISIBLE
            customUrlInput.setText(prefs.getString("custom_url", ""))
        }

        updateNowPlayingLabel(savedIndex)

        stationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                customUrlInput.visibility = if (position == CUSTOM_INDEX) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyStation() {
        val index = stationSpinner.selectedItemPosition
        val url = if (index == CUSTOM_INDEX) {
            customUrlInput.text.toString().trim()
        } else {
            stations[index].second
        }

        if (url.isEmpty()) {
            Toast.makeText(this, "Enter a stream URL", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putInt("station_index", index)
            .putString("custom_url", if (index == CUSTOM_INDEX) url else "")
            .putString("stream_url", url)
            .apply()

        updateNowPlayingLabel(index)

        if (isServiceRunning) {
            stopService(Intent(this, RadioService::class.java))
            startForegroundService(Intent(this, RadioService::class.java))
        }
    }

    private fun updateNowPlayingLabel(index: Int) {
        val name = if (index == CUSTOM_INDEX) "Custom" else stations[index].first
        nowPlayingText.text = "Now playing: $name"
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
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private val serverStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SERVER_STATUS) {
                val status = intent.getStringExtra(EXTRA_SERVER_STATUS)
                serverStatusText.text = "Server: $status"
                isServiceRunning = status == "playing"
            }
        }
    }
}
