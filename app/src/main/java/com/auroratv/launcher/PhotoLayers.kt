package com.auroratv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Photo wallpaper: three scenes (morning, day, night), each split into sky / mid / foreground layers
 * (res/drawable-nodpi/photo_<scene>_<layer>). The scenes cross-fade with the time of day.
 */
object PhotoLayers {
    val SCENES = listOf("morning", "day", "night")
    val LAYERS = listOf("sky", "mid", "fg")

    private fun id(ctx: Context, name: String): Int = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)

    fun available(ctx: Context): Boolean =
        SCENES.all { id(ctx, "photo_${it}_sky") != 0 && id(ctx, "photo_${it}_fg") != 0 }

    fun load(ctx: Context, name: String, opaque: Boolean): Bitmap? {
        val rid = id(ctx, name)
        if (rid == 0) return null
        val targetW = ctx.resources.displayMetrics.widthPixels
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeResource(ctx.resources, rid, bounds)
        var s = 1
        while (bounds.outWidth / (s * 2) >= targetW) s *= 2
        val o = BitmapFactory.Options()
        o.inSampleSize = s
        if (opaque) o.inPreferredConfig = Bitmap.Config.RGB_565
        return try {
            BitmapFactory.decodeResource(ctx.resources, rid, o)
        } catch (e: Throwable) {
            null
        }
    }

    // Time of day (hours) -> which scene is shown: 0 morning, 1 day, 2 night.
    private val KEYS: List<Pair<Float, Int>> = listOf(
        0f to 2, 5f to 2, 7f to 0, 9f to 0, 11f to 1, 17f to 1, 20.5f to 2, 24f to 2
    )

    /** Weights for [morning, day, night]. [pref] is the Scenery setting (0 = follow real time). */
    fun weights(pref: Int, hours: Float): FloatArray {
        val w = FloatArray(3)
        when (pref) {
            1 -> w[0] = 1f
            2 -> w[1] = 1f
            3 -> w[0] = 1f // no sunset photo yet: warm morning scene is the closest match
            4 -> w[2] = 1f
            else -> {
                for (i in 0 until KEYS.size - 1) {
                    val h0 = KEYS[i].first
                    val h1 = KEYS[i + 1].first
                    if (hours >= h0 && hours <= h1) {
                        val a = KEYS[i].second
                        val b = KEYS[i + 1].second
                        val raw = if (h1 == h0) 0f else (hours - h0) / (h1 - h0)
                        val t = raw * raw * (3f - 2f * raw)
                        w[a] += 1f - t
                        w[b] += t
                        return w
                    }
                }
                w[2] = 1f
            }
        }
        return w
    }
}
