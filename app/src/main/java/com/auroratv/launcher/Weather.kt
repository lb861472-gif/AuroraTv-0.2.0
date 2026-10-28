package com.auroratv.launcher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Current weather from Open-Meteo (free, no API key). Only latitude/longitude are sent.
 * Weather data by Open-Meteo.com (CC BY 4.0).
 */
object Weather {
    class Data(val temp: Int, val code: Int, val time: Long, val fahrenheit: Boolean, val loc: String)
    class Place(val lat: String, val lon: String, val label: String)

    private const val STALE_MS = 30L * 60L * 1000L
    private const val DEFAULT_LAT = "39.6359"
    private const val DEFAULT_LON = "-84.0705"
    private const val DEFAULT_LABEL = "Bellbrook, Ohio"

    @Volatile
    var current: Data? = null

    fun place(ctx: Context): Place = Place(
        Prefs.getString(ctx, "w_lat", DEFAULT_LAT),
        Prefs.getString(ctx, "w_lon", DEFAULT_LON),
        Prefs.getString(ctx, "w_label", DEFAULT_LABEL)
    )

    fun savePlace(ctx: Context, p: Place) {
        Prefs.setString(ctx, "w_lat", p.lat)
        Prefs.setString(ctx, "w_lon", p.lon)
        Prefs.setString(ctx, "w_label", p.label)
        current = null
    }

    private fun isF(ctx: Context) = Prefs.get(ctx, Options.TEMP_UNIT) == 0
    private fun locKey(ctx: Context): String {
        val p = place(ctx)
        return p.lat + "," + p.lon
    }

    fun needsRefresh(ctx: Context): Boolean {
        val d = current ?: return true
        return System.currentTimeMillis() - d.time > STALE_MS || d.fahrenheit != isF(ctx) || d.loc != locKey(ctx)
    }

    /** Restore the last saved reading so something shows immediately on start. */
    fun load(ctx: Context) {
        if (current != null) return
        val parts = Prefs.getString(ctx, "w_cache", "").split("|")
        if (parts.size != 5) return
        try {
            current = Data(parts[0].toInt(), parts[1].toInt(), parts[2].toLong(), parts[3] == "1", parts[4])
        } catch (e: Exception) {
            current = null
        }
    }

    private fun save(ctx: Context, d: Data) {
        Prefs.setString(ctx, "w_cache", d.temp.toString() + "|" + d.code + "|" + d.time + "|" + (if (d.fahrenheit) "1" else "0") + "|" + d.loc)
    }

    private fun httpGet(url: String): String? {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "AuroraTV/0.2.0 (personal Android TV launcher)")
                return if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            return null
        }
    }

    /** Blocking network call: run on a background thread. */
    fun fetch(ctx: Context): Data? {
        val p = place(ctx)
        val f = isF(ctx)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=" + p.lat + "&longitude=" + p.lon +
            "&current=temperature_2m,weather_code&timezone=auto" + (if (f) "&temperature_unit=fahrenheit" else "")
        val text = httpGet(url) ?: return null
        return try {
            val cur = JSONObject(text).getJSONObject("current")
            val d = Data(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), System.currentTimeMillis(), f, p.lat + "," + p.lon)
            current = d
            save(ctx, d)
            d
        } catch (e: Exception) {
            null
        }
    }

    /** Blocking: look a city up. "Bellbrook, Ohio" works (text after the comma narrows the match). */
    fun geocode(query: String): Place? {
        val name = query.substringBefore(",").trim()
        if (name.isEmpty()) return null
        val extra = query.substringAfter(",", "").lowercase().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        var text = httpGet("https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&format=json&name=" + URLEncoder.encode(name, "UTF-8"))
        if (text == null || !text.contains("results")) {
            val firstWord = name.split(" ")[0]
            text = httpGet("https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&format=json&name=" + URLEncoder.encode(firstWord, "UTF-8"))
        }
        if (text == null) return null
        return try {
            val arr = JSONObject(text).optJSONArray("results") ?: return null
            var found: JSONObject? = null
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val hay = (r.optString("name") + " " + r.optString("admin1") + " " + r.optString("country")).lowercase()
                if (extra.all { hay.contains(it) }) {
                    found = r
                    break
                }
            }
            val pick: JSONObject = found ?: arr.getJSONObject(0)
            val admin = pick.optString("admin1")
            val label = pick.optString("name") + (if (admin.isNotEmpty()) ", " + admin else "")
            Place(pick.getDouble("latitude").toString(), pick.getDouble("longitude").toString(), label)
        } catch (e: Exception) {
            null
        }
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }

    fun grayAmount(code: Int): Float = when (code) {
        0, 1 -> 0f
        2 -> 0.15f
        3 -> 0.35f
        45, 48 -> 0.45f
        in 51..67 -> 0.5f
        in 80..82 -> 0.5f
        in 71..77 -> 0.4f
        85, 86 -> 0.4f
        in 95..99 -> 0.6f
        else -> 0.1f
    }

    fun cloudiness(code: Int): Float = when (code) {
        0 -> 0.15f
        1 -> 0.3f
        2 -> 0.6f
        3 -> 1f
        45, 48 -> 0.9f
        in 51..99 -> 1f
        else -> 0.35f
    }
}
