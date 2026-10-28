package com.auroratv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * One layer of the mountain scene. Four separate views are stacked by HomeActivity:
 * SKY, FAR, MID (behind the clock) and FG (in front of the clock). By default every layer is drawn
 * procedurally; if photo bitmaps are supplied (photoMode) they are drawn instead.
 */
class ScenicLayer(context: Context, private val kind: Int) : View(context) {

    companion object {
        const val SKY = 0
        const val FAR = 1
        const val MID = 2
        const val FG = 3
    }

    var palette: Palette = Palettes.SUNSET
        set(value) {
            field = value
            rebuild()
            invalidate()
        }

    /** 0 = almost clear sky, 1 = heavily clouded. Only used by the SKY layer. */
    var cloudiness: Float = 0.35f
        set(value) {
            field = value
            invalidate()
        }

    private var photos: Array<Bitmap?> = arrayOfNulls(0)
    private var weights = FloatArray(3)

    /** Photos for morning, day, night (in that order); cross-faded by [setPhotoWeights]. */
    fun setPhotos(b: Array<Bitmap?>) {
        photos = b
        invalidate()
    }

    fun setPhotoWeights(w: FloatArray) {
        weights = w
        invalidate()
    }

    var photoMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ridge = Path()
    private val trees = Path()
    private val lakeRect = RectF()
    private val srcRect = Rect()
    private val dstRect = Rect()
    private var fill: Shader? = null
    private var glow: Shader? = null
    private var snow: Shader? = null
    private var cloud: Shader? = null
    private var lake: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun ridged(t: Float, seed: Float): Float {
        var sum = 0f
        var amp = 1f
        var freq = 2.2f
        var norm = 0f
        for (i in 0 until 5) {
            val phase = seed * (i + 1) * 0.37f
            val v = 1f - abs(sin((t * freq + phase) * Math.PI.toFloat()))
            sum += v * v * amp
            norm += amp
            amp *= 0.5f
            freq *= 2.1f
        }
        return sum / norm
    }

    private fun ridgeY(x: Float): Float {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = x / w
        return when (kind) {
            FAR -> h * (0.50f - 0.20f * ridged(t, 1.3f))
            MID -> h * (0.57f - 0.22f * ridged(t, 4.1f))
            else -> {
                val d = (t - 0.30f) / 0.28f
                h * (0.46f - 0.12f * ridged(t, 2.7f) - 0.03f * exp(-d * d))
            }
        }
    }

    private fun rebuild() {
        if (width == 0 || height == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val p = palette
        ridge.reset()
        trees.reset()

        if (kind == SKY) {
            fill = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(p.skyTop, p.skyMid, p.skyHorizon, p.skyHorizon),
                floatArrayOf(0f, 0.42f, 0.70f, 1f),
                Shader.TileMode.CLAMP
            )
            glow = RadialGradient(
                w * 0.72f, h * 0.43f, h * 0.5f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(p.sunGlow, 210),
                    ColorUtils.setAlphaComponent(p.sunGlow, 70),
                    ColorUtils.setAlphaComponent(p.sunGlow, 0)
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            val cc = ColorUtils.blendARGB(Color.WHITE, p.skyMid, 0.35f)
            cloud = RadialGradient(
                0f, 0f, 100f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(cc, 170),
                    ColorUtils.setAlphaComponent(cc, 70),
                    ColorUtils.setAlphaComponent(cc, 0)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            return
        }

        fill = when (kind) {
            FAR -> LinearGradient(
                0f, h * 0.28f, 0f, h * 0.62f,
                p.far, ColorUtils.blendARGB(p.far, p.skyHorizon, 0.55f), Shader.TileMode.CLAMP
            )
            MID -> LinearGradient(0f, h * 0.33f, 0f, h * 0.70f, p.midRim, p.mid, Shader.TileMode.CLAMP)
            else -> LinearGradient(0f, h * 0.34f, 0f, h, p.fg, p.fgBottom, Shader.TileMode.CLAMP)
        }

        // Snow caps: a white band painted over the ridge only shows on peaks that rise above it.
        val snowColor = ColorUtils.blendARGB(Color.WHITE, p.sunGlow, 0.30f)
        val snowClear = ColorUtils.setAlphaComponent(snowColor, 0)
        snow = when (kind) {
            FAR -> LinearGradient(0f, h * 0.28f, 0f, h * 0.41f, intArrayOf(snowColor, snowColor, snowClear), floatArrayOf(0f, 0.5f, 0.62f), Shader.TileMode.CLAMP)
            MID -> LinearGradient(0f, h * 0.33f, 0f, h * 0.47f, intArrayOf(snowColor, snowColor, snowClear), floatArrayOf(0f, 0.5f, 0.62f), Shader.TileMode.CLAMP)
            else -> null
        }

        val step = 8f
        ridge.moveTo(0f, h)
        var x = 0f
        while (x < w) {
            ridge.lineTo(x, ridgeY(x))
            x += step
        }
        ridge.lineTo(w, ridgeY(w))
        ridge.lineTo(w, h)
        ridge.close()

        if (kind == FG) {
            lakeRect.set(w * 0.60f, h * 0.605f, w * 0.97f, h * 0.665f)
            lake = LinearGradient(
                w * 0.60f, 0f, w * 0.97f, 0f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(p.skyHorizon, 200),
                    ColorUtils.setAlphaComponent(p.sunGlow, 215),
                    ColorUtils.setAlphaComponent(p.skyMid, 190)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            val u = h / 1080f
            var tx = 6f * u
            var i = 0
            while (tx < w) {
                val y0 = ridgeY(tx)
                val th = (26f + 30f * abs(sin(i * 12.9898f))) * u
                val hw = th * 0.28f
                trees.moveTo(tx - hw, y0 + 6f * u)
                trees.lineTo(tx, y0 - th)
                trees.lineTo(tx + hw, y0 + 6f * u)
                trees.close()
                tx += (16f + 10f * abs(sin(i * 4.1f))) * u
                i++
            }
        }
    }

    private fun drawPhoto(canvas: Canvas, b: Bitmap, alpha: Int) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = maxOf(vw / b.width, vh / b.height)
        val sw = (vw / scale).toInt()
        val sh = (vh / scale).toInt()
        val sx = (b.width - sw) / 2
        val sy = (b.height - sh) / 2
        srcRect.set(sx, sy, sx + sw, sy + sh)
        dstRect.set(0, 0, width, height)
        paint.shader = null
        paint.color = Color.argb(alpha.coerceIn(0, 255), 255, 255, 255)
        canvas.drawBitmap(b, srcRect, dstRect, paint)
    }

    override fun onDraw(canvas: Canvas) {
        if (photoMode) {
            // Sky layer: opaque photos blended in order. Cut-out layers: each photo drawn at its own weight.
            var acc = 0f
            for (i in photos.indices) {
                val b = photos[i] ?: continue
                val wgt = if (i < weights.size) weights[i] else 0f
                if (wgt <= 0.002f) continue
                if (kind == SKY) {
                    acc += wgt
                    drawPhoto(canvas, b, (255 * (wgt / acc)).toInt())
                } else {
                    drawPhoto(canvas, b, (255 * wgt).toInt())
                }
            }
            return
        }
        val p = palette
        val w = width.toFloat()
        val h = height.toFloat()
        val u = h / 1080f

        if (kind == SKY) {
            paint.shader = fill
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = glow
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            paint.color = p.sun
            canvas.drawCircle(w * 0.72f, h * 0.43f, h * 0.032f, paint)

            if (p.starAmount > 0.03f) {
                for (i in 0 until 80) {
                    val sx = ((i * 0.61803f) % 1f) * w
                    val sy = ((i * 0.75487f + 0.13f) % 1f) * h * 0.45f
                    val r = (0.8f + (i % 3) * 0.5f) * u
                    paint.color = Color.argb(((120 + (i * 37) % 110) * p.starAmount).toInt(), 255, 255, 255)
                    canvas.drawCircle(sx, sy, r, paint)
                }
            }

            // Soft clouds: a few flattened radial puffs; more and denser when cloudiness is high.
            paint.shader = cloud
            paint.color = Color.WHITE
            paint.alpha = (255 * (0.35f + 0.65f * cloudiness)).toInt().coerceIn(0, 255)
            val n = (2 + cloudiness * 9f).toInt()
            for (i in 0 until n) {
                val cx = ((i * 0.381966f + 0.07f) % 1f) * w * 1.1f - w * 0.05f
                val cy = h * (0.08f + ((i * 0.618f) % 1f) * 0.22f)
                val s = h * (0.10f + ((i * 0.37f) % 1f) * 0.10f)
                val r = s * 0.55f
                val offs = floatArrayOf(-0.9f, -0.3f, 0.35f, 0.95f)
                val dys = floatArrayOf(0.1f, -0.1f, 0f, 0.12f)
                for (k in 0 until 4) {
                    canvas.save()
                    canvas.translate(cx + offs[k] * s, cy + dys[k] * s)
                    canvas.scale(r / 100f, r / 100f * 0.45f)
                    canvas.drawCircle(0f, 0f, 100f, paint)
                    canvas.restore()
                }
            }
            paint.alpha = 255
            paint.shader = null
            return
        }

        paint.shader = fill
        canvas.drawPath(ridge, paint)
        val sn = snow
        if (sn != null) {
            paint.shader = sn
            canvas.drawPath(ridge, paint)
        }
        if (kind == FG) {
            paint.shader = lake
            canvas.drawOval(lakeRect, paint)
            paint.shader = null
            paint.color = p.fg
            canvas.drawPath(trees, paint)
        }
        paint.shader = null
    }
}
