package com.auroratv.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Liquid Glass confirmation / input dialog. Back dismisses it. */
object GlassDialog {

    private fun button(act: Activity, pal: Palette, label: String): GlassPanel {
        val u = Ui.u(act)
        val b = Ui.focusPanel(act, pal, 76f)
        (b.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0
            weight = 1f
            leftMargin = (8 * u).toInt()
            rightMargin = (8 * u).toInt()
        }
        val t = Ui.text(act, label, 28f)
        t.gravity = Gravity.CENTER
        b.addView(t, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return b
    }

    fun show(
        act: Activity,
        title: String,
        message: String?,
        positive: String,
        negative: String?,
        hint: String? = null,
        onNegative: (() -> Unit)? = null,
        onPositive: (String) -> Unit
    ): Dialog {
        val u = Ui.u(act)
        val pal = Ui.palette(act)
        val dlg = Dialog(act, android.R.style.Theme_Translucent_NoTitleBar)
        val root = FrameLayout(act)
        root.setBackgroundColor(Color.argb(130, 0, 0, 0))

        val panel = GlassPanel(act)
        panel.radius = 44f * u
        panel.applyTheme(pal)

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        val pad = (40 * u).toInt()
        col.setPadding(pad, pad, pad, (28 * u).toInt())
        col.addView(Ui.text(act, title, 40f))

        if (message != null) {
            val m: TextView = Ui.text(act, message, 25f, Color.argb(225, 255, 255, 255))
            m.setPadding(0, (14 * u).toInt(), 0, (18 * u).toInt())
            col.addView(m)
        }

        var edit: EditText? = null
        if (hint != null) {
            val e = EditText(act)
            e.hint = hint
            e.setTextColor(Color.WHITE)
            e.setHintTextColor(Color.argb(130, 255, 255, 255))
            e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28f * u)
            e.inputType = InputType.TYPE_CLASS_TEXT
            e.isSingleLine = true
            col.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            edit = e
        }

        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        val pos = button(act, pal, positive)
        pos.setOnClickListener {
            dlg.dismiss()
            onPositive(edit?.text?.toString() ?: "")
        }
        row.addView(pos)
        if (negative != null) {
            val neg = button(act, pal, negative)
            neg.setOnClickListener {
                dlg.dismiss()
                onNegative?.invoke()
            }
            row.addView(neg)
        }
        val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = (12 * u).toInt()
        col.addView(row, rlp)

        panel.addView(col)
        root.addView(
            panel,
            FrameLayout.LayoutParams((act.resources.displayMetrics.widthPixels * 0.52f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        dlg.setContentView(root)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dlg.setOnCancelListener { onNegative?.invoke() }
        dlg.show()
        val first: android.view.View = edit ?: pos
        first.requestFocus()
        return dlg
    }
}
