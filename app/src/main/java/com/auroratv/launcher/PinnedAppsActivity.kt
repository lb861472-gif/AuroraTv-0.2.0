package com.auroratv.launcher

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Choose which apps are pinned to the front of "Most used" (also possible by holding OK on a tile). */
class PinnedAppsActivity : BaseActivity() {

    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val u = Ui.u(this)
        val pal = Ui.palette(this)
        val dm = resources.displayMetrics

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(8, 9, 20))
        val panel = GlassPanel(this)
        panel.radius = 52f * u
        panel.applyTheme(pal)
        panel.frosted = false
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setPadding((40 * u).toInt(), (32 * u).toInt(), (40 * u).toInt(), (26 * u).toInt())
        outer.addView(Ui.text(this, "Pinned apps", 46f))
        val hint = Ui.text(this, "Select an app to pin or unpin it. Pinned apps come first in \"Most used\".", 23f, Color.argb(190, 255, 255, 255))
        hint.setPadding(0, (4 * u).toInt(), 0, (14 * u).toInt())
        outer.addView(hint)
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding((8 * u).toInt(), (8 * u).toInt(), (8 * u).toInt(), (8 * u).toInt())
        scroll.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(outer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams((dm.widthPixels * 0.62f).toInt(), (dm.heightPixels * 0.88f).toInt(), Gravity.CENTER))
        attachVolume(root)
        setContentView(root)

        Thread {
            val apps = AppScanner.scan(this, Prefs.get(this, Options.ALL_APPS) == 1)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                for (a in apps) addApp(a, pal)
                if (col.childCount > 0) col.getChildAt(0).requestFocus()
            }
        }.start()
    }

    private fun addApp(a: AppEntry, pal: Palette) {
        val row = Ui.focusPanel(this, pal, 80f)
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.HORIZONTAL
        inner.gravity = Gravity.CENTER_VERTICAL
        val u = Ui.u(this)
        inner.setPadding((28 * u).toInt(), 0, (28 * u).toInt(), 0)
        row.addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        inner.addView(Ui.text(this, a.label, 29f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val state = Ui.text(this, "", 28f)
        inner.addView(state)
        fun refresh() {
            val pinned = Prefs.getPins(this).contains(a.pkg)
            state.text = if (pinned) "\u2605 Pinned" else "Not pinned"
            state.setTextColor(if (pinned) pal.accent else Color.argb(150, 255, 255, 255))
        }
        refresh()
        row.setOnClickListener {
            Prefs.togglePin(this, a.pkg)
            refresh()
        }
        col.addView(row)
    }
}
