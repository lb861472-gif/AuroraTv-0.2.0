package com.auroratv.launcher

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import java.util.Calendar

class Palette(
    val skyTop: Int,
    val skyMid: Int,
    val skyHorizon: Int,
    val sun: Int,
    val sunGlow: Int,
    val far: Int,
    val mid: Int,
    val midRim: Int,
    val fg: Int,
    val fgBottom: Int,
    val starAmount: Float,
    val accent: Int,
    val dockTint: Int,
    val text: Int
) {
    /** Dull the colours towards grey, used for overcast / rainy weather. */
    fun grayed(amount: Float): Palette {
        if (amount <= 0.01f) return this
        val g = Color.parseColor("#6A7080")
        fun m(c: Int): Int = ColorUtils.blendARGB(c, g, amount)
        return Palette(
            m(skyTop), m(skyMid), m(skyHorizon),
            ColorUtils.blendARGB(sun, g, amount * 0.8f), m(sunGlow),
            m(far), m(mid), m(midRim),
            fg, fgBottom,
            starAmount * (1f - amount),
            accent, dockTint, text
        )
    }
}

object Palettes {
    private fun c(s: String): Int = Color.parseColor(s)

    val SUNRISE = Palette(
        c("#4E6FB0"), c("#F0A3B0"), c("#FFD7A0"),
        c("#FFF4D0"), c("#FFD08A"),
        c("#A99BBB"), c("#5C5679"), c("#EFC0AC"),
        c("#1C2131"), c("#090B11"), 0f,
        c("#FFD6A0"), c("#78402A3A"), c("#FFF6EA")
    )

    val DAY = Palette(
        c("#2F6FC0"), c("#6FA6E0"), c("#CFE3F5"),
        c("#FFFFFF"), c("#FFF2C0"),
        c("#8FA9C6"), c("#4C6A8C"), c("#B6CCE0"),
        c("#14301F"), c("#07140D"), 0f,
        c("#A8D4FF"), c("#70101E32"), c("#FFFFFF")
    )

    val SUNSET = Palette(
        c("#2B2A5E"), c("#B8456F"), c("#FFB059"),
        c("#FFE7A8"), c("#FF9A4D"),
        c("#7B5A8E"), c("#3C3459"), c("#C98AA0"),
        c("#14161F"), c("#07080C"), 0f,
        c("#FFB059"), c("#78280E28"), c("#FFF1E8")
    )

    val TWILIGHT = Palette(
        c("#0B1033"), c("#2A2C6B"), c("#7A5AA6"),
        c("#E8ECFF"), c("#7F8CFF"),
        c("#3A3F7A"), c("#20244F"), c("#6C74C0"),
        c("#0A0C1C"), c("#040510"), 1f,
        c("#A9B4FF"), c("#780A0C2A"), c("#EEF0FF")
    )

    // Time of day (hours) -> look. Colours are blended smoothly between neighbouring keys.
    private val KEYS: List<Pair<Float, Palette>> = listOf(
        0f to TWILIGHT,
        5f to TWILIGHT,
        6.5f to SUNRISE,
        9f to SUNRISE,
        11f to DAY,
        16f to DAY,
        18.5f to SUNSET,
        20f to SUNSET,
        21.5f to TWILIGHT,
        24f to TWILIGHT
    )

    private fun b(x: Int, y: Int, t: Float): Int = ColorUtils.blendARGB(x, y, t)

    fun lerp(a: Palette, o: Palette, t: Float): Palette = Palette(
        b(a.skyTop, o.skyTop, t), b(a.skyMid, o.skyMid, t), b(a.skyHorizon, o.skyHorizon, t),
        b(a.sun, o.sun, t), b(a.sunGlow, o.sunGlow, t),
        b(a.far, o.far, t), b(a.mid, o.mid, t), b(a.midRim, o.midRim, t),
        b(a.fg, o.fg, t), b(a.fgBottom, o.fgBottom, t),
        a.starAmount + (o.starAmount - a.starAmount) * t,
        b(a.accent, o.accent, t), b(a.dockTint, o.dockTint, t), b(a.text, o.text, t)
    )

    fun auto(hours: Float): Palette {
        for (i in 0 until KEYS.size - 1) {
            val h0 = KEYS[i].first
            val h1 = KEYS[i + 1].first
            if (hours >= h0 && hours <= h1) {
                val raw = if (h1 == h0) 0f else (hours - h0) / (h1 - h0)
                val t = raw * raw * (3f - 2f * raw)
                return lerp(KEYS[i].second, KEYS[i + 1].second, t)
            }
        }
        return TWILIGHT
    }

    fun nowHours(): Float {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
    }

    fun forIndex(pref: Int, hours: Float): Palette = when (pref) {
        1 -> SUNRISE
        2 -> DAY
        3 -> SUNSET
        4 -> TWILIGHT
        else -> auto(hours)
    }
}
