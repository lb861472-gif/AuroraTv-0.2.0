package com.auroratv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import kotlin.math.min

/**
 * "Liquid Glass" building blocks.
 *
 * Frosting is simulated cheaply: the scenery is drawn ONCE into a tiny bitmap ([Glass.capture]) and every
 * [GlassPanel] stretches that bitmap behind itself (bilinear filtering = a soft blur), then adds a tint,
 * a light sheen and a bright edge. No per-frame blur, so it is light on TV hardware. If "Simple tint" is
 * chosen in Settings (or no backdrop exists) panels fall back to the tint + sheen + edge only.
 */
object Glass {
    @Volatile
    var backdrop: Bitmap? = null

    fun capture(layers: List<View>, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val bw = 192
        val bh = (bw * h / w.toFloat()).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(bw / w.toFloat(), bh / h.toFloat())
        for (v in layers) {
            if (v.visibility != View.VISIBLE) continue
            c.save()
            c.translate(v.left.toFloat(), v.top.toFloat())
            v.draw(c)
            c.restore()
        }
        backdrop = bmp
    }

    fun strength(ctx: Context): Int = Prefs.getInt(ctx, "glass_strength", 55)

    fun tintAlpha(ctx: Context): Int = (50 + strength(ctx) * 1.8f).toInt().coerceIn(40, 235)
}

class GlassPanel(context: Context) : FrameLayout(context) {
    var radius: Float = 40f
        set(v) {
            field = v
            rebuild()
            invalidate()
        }
    var tint: Int = Color.argb(120, 14, 14, 28)
    var accent: Int = Color.WHITE
    var frosted: Boolean = true
    var showFocusRing: Boolean = false

    private val path = Path()
    private val rect = RectF()
    private val edgeRect = RectF()
    private val dst = RectF()
    private val loc = IntArray(2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var sheen: Shader? = null
    private var edge: Shader? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    fun applyTheme(p: Palette) {
        tint = ColorUtils.setAlphaComponent(p.dockTint, Glass.tintAlpha(context))
        accent = p.accent
        frosted = Prefs.get(context, Options.GLASS) == 0
        invalidate()
    }

    private fun rebuild() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        val r = min(radius, min(w, h) / 2f)
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        sheen = LinearGradient(0f, 0f, 0f, h, Color.argb(70, 255, 255, 255), Color.argb(6, 255, 255, 255), Shader.TileMode.CLAMP)
        edge = LinearGradient(0f, 0f, w, h, Color.argb(160, 255, 255, 255), Color.argb(34, 255, 255, 255), Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (showFocusRing) {
            val s = if (gainFocus) 1.04f else 1f
            animate().scaleX(s).scaleY(s).setDuration(Anim.ms(context, 140)).start()
            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width > 0 && height > 0) {
            val u = resources.displayMetrics.heightPixels / 1080f
            val r = min(radius, min(width, height) / 2f)
            canvas.save()
            canvas.clipPath(path)
            val bd = Glass.backdrop
            if (frosted && bd != null) {
                getLocationInWindow(loc)
                val dm = resources.displayMetrics
                canvas.save()
                canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
                dst.set(0f, 0f, dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
                paint.shader = null
                paint.color = Color.WHITE
                canvas.drawBitmap(bd, null, dst, paint)
                canvas.restore()
            }
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = tint
            canvas.drawRect(rect, paint)
            paint.shader = sheen
            canvas.drawRect(rect, paint)
            if (showFocusRing && isFocused) {
                paint.shader = null
                paint.color = ColorUtils.setAlphaComponent(accent, 60)
                canvas.drawRect(rect, paint)
            }
            canvas.restore()

            val sw = 1.6f * u
            edgeRect.set(sw / 2, sw / 2, width - sw / 2, height - sw / 2)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sw
            paint.shader = edge
            canvas.drawRoundRect(edgeRect, r, r, paint)
            if (showFocusRing && isFocused) {
                val fw = 3.5f * u
                edgeRect.set(fw / 2, fw / 2, width - fw / 2, height - fw / 2)
                paint.shader = null
                paint.color = accent
                paint.strokeWidth = fw
                canvas.drawRoundRect(edgeRect, r, r, paint)
            }
            paint.style = Paint.Style.FILL
            paint.shader = null
        }
        super.dispatchDraw(canvas)
    }
}

/** Small UI helpers shared by the settings, dialogs and app-pinning screens. */
object Ui {
    fun u(ctx: Context): Float = ctx.resources.displayMetrics.heightPixels / 1080f

    fun palette(ctx: Context): Palette = Palettes.forIndex(Prefs.get(ctx, Options.PALETTE), Palettes.nowHours())

    fun text(ctx: Context, s: String, sizeU: Float, color: Int = Color.WHITE): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextColor(color)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeU * u(ctx))
        t.setShadowLayer(6f * u(ctx), 0f, 2f * u(ctx), Color.argb(150, 0, 0, 0))
        return t
    }

    /** A focusable glass row (used for settings rows and buttons). */
    fun focusPanel(ctx: Context, pal: Palette, heightU: Float, clickable: Boolean = true): GlassPanel {
        val u = u(ctx)
        val r = GlassPanel(ctx)
        r.radius = 22f * u
        r.applyTheme(pal)
        r.tint = ColorUtils.setAlphaComponent(pal.dockTint, 80)
        r.frosted = false
        r.isFocusable = true
        r.isClickable = clickable
        r.showFocusRing = true
        r.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (heightU > 0) (heightU * u).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = (10 * u).toInt()
        r.layoutParams = lp
        return r
    }

    fun centered(child: View): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)

    /** Apply the current palette to every GlassPanel under [v]. */
    fun themeAll(v: View, pal: Palette) {
        if (v is GlassPanel) {
            val ring = v.showFocusRing
            v.applyTheme(pal)
            if (ring) {
                // focusable rows/buttons use a plain tint (no backdrop) so they stay readable
                v.frosted = false
                v.tint = ColorUtils.setAlphaComponent(pal.dockTint, 80)
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) themeAll(v.getChildAt(i), pal)
        }
        v.invalidate()
    }
}

/** Custom on/off switch (not an Android Switch widget). */
class SwitchView(context: Context) : View(context) {
    var on = false
        set(v) {
            field = v
            invalidate()
        }
    var accent = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val u = Ui.u(context)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((88 * u).toInt(), (46 * u).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = if (on) accent else Color.argb(90, 255, 255, 255)
        c.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, p)
        p.color = Color.WHITE
        val cx = if (on) w - h / 2 else h / 2
        c.drawCircle(cx, h / 2, h / 2 - 5 * u, p)
    }
}

/** Custom slider (not an Android SeekBar). */
class SliderView(context: Context) : View(context) {
    var fraction = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var accent = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val u = Ui.u(context)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((300 * u).toInt(), (46 * u).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val th = 10 * u
        val top = (h - th) / 2
        p.color = Color.argb(90, 255, 255, 255)
        c.drawRoundRect(0f, top, w, top + th, th / 2, th / 2, p)
        val x = th + (w - 2 * th) * fraction
        p.color = accent
        c.drawRoundRect(0f, top, x, top + th, th / 2, th / 2, p)
        p.color = Color.WHITE
        c.drawCircle(x, h / 2, 15 * u, p)
    }
}
