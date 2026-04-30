package dev.voidcore.retroplayer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Station(val name: String, val url: String, val category: String)

val defaultStations: List<Station> = listOf(
    // English
    Station("BBC Radio 1", "http://stream.live.vc.bbcmedia.co.uk/bbc_radio_one", "english"),
    Station("BBC Radio 6 Music", "http://stream.live.vc.bbcmedia.co.uk/bbc_6music", "english"),
    Station("KEXP 90.3", "https://kexp-mp3-128.streamguys1.com/kexp128.mp3", "english"),
    // German
    Station("Ö3", "https://orf-live.ors-shoutcast.at/oe3-q2a.m3u", "german"),
    Station("Energy", "http://stream1.energy.at:8000/vie.m3u", "german"),
    Station("Kronehit", "https://secureonair.krone.at/kronehit-hp.mp3", "german"),
    // Hungarian
    Station("Retro Radio", "https://icast.connectmedia.hu/5001/live.mp3", "hungarian"),
    Station("Sláger FM", "https://icast.connectmedia.hu/4741/live.mp3", "hungarian"),
    Station("Rádió 1", "https://icast.connectmedia.hu/5201/live.mp3", "hungarian")
)

fun loadStations(prefs: SharedPreferences): MutableList<Station> {
    val json = prefs.getString("stations_json", null)
        ?: return defaultStations.toMutableList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Station(obj.getString("name"), obj.getString("url"), obj.getString("category"))
        }.toMutableList()
    } catch (e: Exception) {
        defaultStations.toMutableList()
    }
}

fun saveStations(prefs: SharedPreferences, stations: List<Station>) {
    val array = JSONArray()
    stations.forEach { station ->
        val obj = JSONObject()
        obj.put("name", station.name)
        obj.put("url", station.url)
        obj.put("category", station.category)
        array.put(obj)
    }
    prefs.edit().putString("stations_json", array.toString()).apply()
}
