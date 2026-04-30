package dev.voidcore.retroplayer

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import android.view.View
import java.net.HttpURLConnection
import java.net.URL

class StationManagerDialog : DialogFragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var stationsContainer: LinearLayout

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        prefs = requireContext().getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
        val ctx = requireContext()

        val scrollView = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        stationsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(stationsContainer)

        // Divider
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.topMargin = dp(16); it.bottomMargin = dp(12) }
            setBackgroundColor(0x1A506169.toInt())
        })

        // Add station section
        root.addView(TextView(ctx).apply {
            text = "Add Station"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFC9D1D9.toInt())
        })

        val categoryLabels = arrayOf("English", "German", "Hungarian", "Custom")
        val categoryKeys = arrayOf("english", "german", "hungarian", "custom")

        val categorySpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, categoryLabels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(3)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(6) }
        }

        val nameInput = EditText(ctx).apply {
            hint = "Station name (not needed for .m3u)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(6) }
        }

        val urlInput = EditText(ctx).apply {
            hint = "Stream URL or .m3u playlist URL"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI or
                    android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(4) }
        }

        val addBtn = Button(ctx).apply {
            text = "Add"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(6) }
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(ctx, "Enter a URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val cat = categoryKeys[categorySpinner.selectedItemPosition]
                if (url.substringBefore("?").lowercase().endsWith(".m3u")) {
                    importM3u(url, cat, nameInput, urlInput)
                } else {
                    if (name.isEmpty()) {
                        Toast.makeText(ctx, "Enter a station name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val stations = loadStations(prefs)
                    stations.add(Station(name, url, cat))
                    saveStations(prefs, stations)
                    nameInput.setText("")
                    urlInput.setText("")
                    refreshList()
                }
            }
        }

        root.addView(categorySpinner)
        root.addView(nameInput)
        root.addView(urlInput)
        root.addView(addBtn)

        scrollView.addView(root)
        refreshList()

        return AlertDialog.Builder(ctx)
            .setTitle("Radio Stations")
            .setView(scrollView)
            .setNegativeButton("Close", null)
            .create()
    }

    fun refreshList() {
        stationsContainer.removeAllViews()
        val ctx = requireContext()
        val stations = loadStations(prefs)
        val currentUrl = prefs.getString("stream_url", "")

        val sections = listOf(
            "english" to "English",
            "german" to "German",
            "hungarian" to "Hungarian",
            "custom" to "Custom"
        )

        for ((key, label) in sections) {
            val list = stations.filter { it.category == key }
            if (list.isEmpty()) continue

            stationsContainer.addView(TextView(ctx).apply {
                text = label
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFC9D1D9.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = if (stationsContainer.childCount > 0) dp(12) else 0
                    it.bottomMargin = dp(2)
                }
            })

            for (station in list) {
                val isSelected = station.url == currentUrl
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                    )
                    if (isSelected) setBackgroundColor(0x1A506169)
                    setPadding(dp(4), 0, 0, 0)
                }

                val nameView = TextView(ctx).apply {
                    text = station.name
                    textSize = 14f
                    setTextColor(0xFFC9D1D9.toInt())
                    if (isSelected) setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                    setOnClickListener {
                        prefs.edit().putString("stream_url", station.url).apply()
                        (requireActivity() as? MainActivity)?.onStationSelectedFromDialog(station)
                        dismiss()
                    }
                }

                val deleteBtn = Button(ctx).apply {
                    text = "×"
                    textSize = 18f
                    setTextColor(0xFFAAAAAA.toInt())
                    background = null
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    setPadding(0, 0, 0, 0)
                    setOnClickListener {
                        val all = loadStations(prefs)
                        all.removeAll { it.url == station.url && it.name == station.name }
                        saveStations(prefs, all)
                        if (station.url == currentUrl) {
                            val remaining = loadStations(prefs)
                            if (remaining.isNotEmpty()) {
                                prefs.edit().putString("stream_url", remaining[0].url).apply()
                                (requireActivity() as? MainActivity)
                                    ?.onStationSelectedFromDialog(remaining[0])
                            }
                        }
                        refreshList()
                    }
                }

                row.addView(nameView)
                row.addView(deleteBtn)
                stationsContainer.addView(row)
            }
        }
    }

    private fun importM3u(url: String, category: String, nameInput: EditText, urlInput: EditText) {
        val ctx = requireContext()
        val handler = Handler(Looper.getMainLooper())
        Toast.makeText(ctx, "Importing playlist…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "RetroPlayer/1.0 (Android)")
                val content = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val channels = parseM3u(content)
                handler.post {
                    if (!isAdded) return@post
                    if (channels.isEmpty()) {
                        Toast.makeText(ctx, "No channels found in playlist", Toast.LENGTH_SHORT).show()
                        return@post
                    }
                    val stations = loadStations(prefs)
                    channels.forEach { (name, streamUrl) -> stations.add(Station(name, streamUrl, category)) }
                    saveStations(prefs, stations)
                    nameInput.setText("")
                    urlInput.setText("")
                    Toast.makeText(ctx, "Added ${channels.size} channel(s)", Toast.LENGTH_SHORT).show()
                    refreshList()
                }
            } catch (e: Exception) {
                handler.post {
                    if (!isAdded) return@post
                    Toast.makeText(ctx, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
