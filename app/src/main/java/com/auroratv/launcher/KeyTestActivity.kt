package com.auroratv.launcher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shows exactly which key events AuroraTV receives from the remote. Every key is swallowed here (that is the
 * point of the test), so volume keys will not change the volume while this screen is open.
 * Keys the TV firmware handles itself (Home, usually Settings) never appear. Press Back three times to leave.
 */
class KeyTestActivity : Activity() {
    private lateinit var log: TextView
    private val lines = ArrayList<String>()
    private var backCount = 0
    private var lastBack = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val u = Ui.u(this)
        val pal = Ui.palette(this)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(8, 9, 20))
        val panel = GlassPanel(this)
        panel.radius = 50f * u
        panel.applyTheme(pal)
        panel.frosted = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding((44 * u).toInt(), (36 * u).toInt(), (44 * u).toInt(), (30 * u).toInt())
        col.addView(Ui.text(this, "Remote key test", 46f))
        val hint = Ui.text(this, "Press buttons on your remote (Settings, Menu, volume, colour keys...). Keys the TV handles itself, such as Home, won't appear here. Press Back 3 times to leave.", 23f, Color.argb(200, 255, 255, 255))
        hint.setPadding(0, (8 * u).toInt(), 0, (16 * u).toInt())
        col.addView(hint)
        log = Ui.text(this, "Waiting for a key...", 28f, pal.accent)
        log.setLineSpacing(6f * u, 1f)
        col.addView(log)
        panel.addView(col)
        root.addView(panel, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.7f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            lines.add(0, KeyEvent.keyCodeToString(event.keyCode) + "  (" + event.keyCode + ")")
            while (lines.size > 9) lines.removeAt(lines.size - 1)
            log.text = lines.joinToString("\n")
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                val now = SystemClock.uptimeMillis()
                backCount = if (now - lastBack < 1800) backCount + 1 else 1
                lastBack = now
                if (backCount >= 3) finish()
            }
        }
        return true
    }
}
