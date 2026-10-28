package com.auroratv.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class VolumeBar(context: Context) : View(context) {
    var fraction = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var fill = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val tw = w * 0.36f
        val l = (w - tw) / 2
        p.color = Color.argb(80, 255, 255, 255)
        c.drawRoundRect(l, 0f, l + tw, h, tw / 2, tw / 2, p)
        val fh = h * fraction
        p.color = fill
        c.drawRoundRect(l, h - fh, l + tw, h, tw / 2, tw / 2, p)
        p.color = Color.WHITE
        c.drawCircle(w / 2, (h - fh).coerceIn(tw, h - tw), tw * 0.85f, p)
    }
}

/**
 * AuroraTV's own volume panel. It drives the real STREAM_MUSIC volume through AudioManager and only
 * appears while an AuroraTV screen is in front.
 *
 * Android TV caveats (verified against the user's photos: the TV shows its OWN volume bar over AuroraTV):
 *  - The firmware may handle volume keys before any app sees them; then this class never runs.
 *  - Fixed-volume outputs (some HDMI/ARC setups) cannot be changed by apps. In that case, or if a change
 *    has no effect, we stop handling and let the TV's own volume bar take over (the result is shown in
 *    Settings > Audio & volume).
 */
class VolumeController(private val ctx: Context, host: FrameLayout) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val u = Ui.u(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val panel = GlassPanel(ctx)
    private val bar = VolumeBar(ctx)
    private val num: TextView = Ui.text(ctx, "", 34f)
    private val icon = ImageView(ctx)
    private var failed = false
    private val hide = Runnable { hidePanel() }

    init {
        panel.radius = 48f * u
        panel.visibility = View.GONE
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(0, (22 * u).toInt(), 0, (22 * u).toInt())
        num.gravity = Gravity.CENTER
        col.addView(num, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val blp = LinearLayout.LayoutParams((64 * u).toInt(), 0, 1f)
        blp.topMargin = (14 * u).toInt()
        blp.bottomMargin = (14 * u).toInt()
        col.addView(bar, blp)
        col.addView(icon, LinearLayout.LayoutParams((46 * u).toInt(), (46 * u).toInt()))
        panel.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lp = FrameLayout.LayoutParams((112 * u).toInt(), (420 * u).toInt(), Gravity.END or Gravity.CENTER_VERTICAL)
        lp.rightMargin = (ctx.resources.displayMetrics.widthPixels * 0.05f).toInt()
        host.addView(panel, lp)
    }

    fun isVolumeKey(e: KeyEvent): Boolean =
        e.keyCode == KeyEvent.KEYCODE_VOLUME_UP || e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || e.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE

    private fun fail() {
        failed = true
        Prefs.setString(ctx, "vol_status", "fallback")
    }

    /** Returns true if the key was handled here. False means: let Android / the TV handle it as usual. */
    fun handle(e: KeyEvent): Boolean {
        if (!isVolumeKey(e)) return false
        if (Prefs.get(ctx, Options.VOL_PANEL) != 0 || failed || am.isVolumeFixed) return false
        if (e.action != KeyEvent.ACTION_DOWN) return true

        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream)
        val before = am.getStreamVolume(stream)
        val wasMuted = am.isStreamMute(stream)
        val step = intArrayOf(1, 2, 5)[Prefs.get(ctx, Options.VOL_STEP)]

        when (e.keyCode) {
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                am.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                if (am.isStreamMute(stream) == wasMuted) {
                    fail()
                    return false
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (wasMuted) am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                val target = (before + step).coerceAtMost(max)
                am.setStreamVolume(stream, target, 0)
                if (target != before && am.getStreamVolume(stream) == before && !wasMuted) {
                    fail()
                    return false
                }
            }
            else -> {
                val target = (before - step).coerceAtLeast(0)
                am.setStreamVolume(stream, target, 0)
                if (target != before && am.getStreamVolume(stream) == before) {
                    fail()
                    return false
                }
            }
        }
        Prefs.setString(ctx, "vol_status", "ok")
        show(max)
        return true
    }

    private fun show(max: Int) {
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
        panel.applyTheme(Ui.palette(ctx))
        bar.fill = Ui.palette(ctx).accent
        bar.fraction = if (muted || max <= 0) 0f else cur.toFloat() / max
        num.text = if (muted) "Mute" else cur.toString()
        icon.setImageResource(if (muted || cur == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        handler.removeCallbacks(hide)
        if (panel.visibility != View.VISIBLE) {
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.translationX = 40f * u
        }
        panel.animate().alpha(1f).translationX(0f).setDuration(Anim.ms(ctx, 220)).start()
        val ms = longArrayOf(1500L, 2500L, 4000L)[Prefs.get(ctx, Options.VOL_TIME)]
        handler.postDelayed(hide, ms)
    }

    private fun hidePanel() {
        panel.animate().alpha(0f).translationX(40f * u).setDuration(Anim.ms(ctx, 200)).withEndAction {
            panel.visibility = View.GONE
        }.start()
    }
}

/** Base class: gives every AuroraTV screen the Liquid Glass volume panel. */
abstract class BaseActivity : Activity() {
    protected var volume: VolumeController? = null

    protected fun attachVolume(host: FrameLayout) {
        volume = VolumeController(this, host)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (volume?.handle(event) == true) return true
        return super.dispatchKeyEvent(event)
    }
}
