package com.auroratv.launcher

import android.content.Context

class Option(val key: String, val title: String, val values: List<String>, val default: Int)

object Options {
    val PALETTE = Option("palette2", "Scenery", listOf("Auto (follows real time)", "Sunrise", "Day", "Sunset", "Twilight"), 0)
    val WALLPAPER = Option("wallpaper2", "Wallpaper", listOf("Drawn mountains", "Photos (change with time of day)"), 1)
    val CLOCK_FORMAT = Option("clock_format", "Clock format", listOf("System", "12-hour", "24-hour"), 0)
    val CLOCK_POS = Option("clock_pos", "Clock position", listOf("Left", "Center"), 0)
    val CLOCK_HEIGHT = Option("clock_height", "Clock height (how much the ridge covers)", listOf("Lower", "Default", "Higher"), 2)
    val SHOW_DATE = Option("show_date", "Show date", listOf("On", "Off"), 0)
    val DOCK_STYLE = Option("dock_style", "App tile style", listOf("Wide tiles", "Square icons"), 0)
    val PARALLAX = Option("parallax", "Parallax when navigating", listOf("On", "Off"), 0)
    val AMBIENT = Option("ambient", "Ambient mode after", listOf("Never", "15 seconds", "30 seconds", "1 minute", "2 minutes"), 3)
    val DRIFT = Option("drift", "Slow scenery motion in ambient mode", listOf("On", "Off"), 0)
    val ALL_APPS = Option("all_apps", "Apps shown", listOf("TV apps only", "All apps"), 0)
    val WEATHER_ON = Option("weather", "Weather (uses internet)", listOf("On", "Off"), 0)
    val TEMP_UNIT = Option("temp_unit", "Temperature unit", listOf("\u00B0F", "\u00B0C"), 0)
    val WEATHER_SCENERY = Option("weather_scenery", "Weather changes the scenery", listOf("On", "Off"), 0)
    val GLASS = Option("glass", "Glass effect", listOf("Frosted (blurred scenery)", "Simple tint"), 0)
    val ANIM = Option("anim", "Animation intensity", listOf("Full", "Reduced", "Off"), 0)
    val VOL_PANEL = Option("vol_panel", "AuroraTV volume panel", listOf("On", "Off"), 0)
    val VOL_STEP = Option("vol_step", "Volume step per press", listOf("1", "2", "5"), 0)
    val VOL_TIME = Option("vol_time", "Volume panel stays visible", listOf("1.5 seconds", "2.5 seconds", "4 seconds"), 1)
    val HOME_ALIAS = Option("home_alias", "AuroraTV can be chosen as Home app", listOf("Off", "On"), 1)

    fun ambientMillis(index: Int): Long = longArrayOf(0L, 15000L, 30000L, 60000L, 120000L).getOrElse(index) { 0L }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("aurora_prefs", Context.MODE_PRIVATE)

    fun get(c: Context, o: Option): Int = sp(c).getInt(o.key, o.default).coerceIn(0, o.values.size - 1)

    fun set(c: Context, o: Option, value: Int) {
        sp(c).edit().putInt(o.key, value).apply()
    }

    fun getInt(c: Context, key: String, def: Int): Int = sp(c).getInt(key, def)

    fun setInt(c: Context, key: String, value: Int) {
        sp(c).edit().putInt(key, value).apply()
    }

    fun getString(c: Context, key: String, def: String): String = sp(c).getString(key, def) ?: def

    fun setString(c: Context, key: String, value: String) {
        sp(c).edit().putString(key, value).apply()
    }

    /** Pinned apps: package names in the order they were pinned. */
    fun getPins(c: Context): List<String> =
        getString(c, "pins", "").split(",").filter { it.isNotEmpty() }

    /** Returns true if the app is now pinned, false if it was unpinned. */
    fun togglePin(c: Context, pkg: String): Boolean {
        val list = getPins(c).toMutableList()
        val nowPinned: Boolean
        if (list.contains(pkg)) {
            list.remove(pkg)
            nowPinned = false
        } else {
            list.add(pkg)
            nowPinned = true
        }
        setString(c, "pins", list.joinToString(","))
        return nowPinned
    }

    fun all(c: Context): Map<String, *> = sp(c).all
    fun remove(c: Context, keys: List<String>) {
        val e = sp(c).edit()
        for (k in keys) e.remove(k)
        e.apply()
    }
}

/** "Most used" counting. Only launches made from AuroraTV are counted, stored on this TV only. */
object Usage {
    private const val P = "use_"

    fun record(c: Context, pkg: String) {
        if (pkg.isEmpty()) return
        Prefs.setInt(c, P + pkg, Prefs.getInt(c, P + pkg, 0) + 1)
    }

    fun counts(c: Context): Map<String, Int> {
        val out = HashMap<String, Int>()
        for ((k, v) in Prefs.all(c)) {
            if (k.startsWith(P) && v is Int) out[k.removePrefix(P)] = v
        }
        return out
    }

    fun clear(c: Context) {
        Prefs.remove(c, Prefs.all(c).keys.filter { it.startsWith(P) })
    }
}

object Anim {
    /** 1 = full, 0.5 = reduced, 0 = off */
    fun scale(c: Context): Float = when (Prefs.get(c, Options.ANIM)) {
        1 -> 0.5f
        2 -> 0f
        else -> 1f
    }

    fun ms(c: Context, base: Long): Long = (base * scale(c)).toLong()
}
