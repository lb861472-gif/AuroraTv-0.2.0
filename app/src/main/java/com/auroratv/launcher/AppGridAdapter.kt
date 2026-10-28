package com.auroratv.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppEntry(
    val label: String,
    val icon: Drawable?,
    val banner: Drawable?,
    val intent: Intent?,
    val isSettings: Boolean = false,
    val pkg: String = ""
)

/** One row element of the home grid: a section header, or an app tile. */
class GridItem(val header: String?, val app: AppEntry?)

/**
 * Home grid: "Most used" header + 8 tiles, then "All apps" header + the rest. Headers span the full width
 * (see GridLayoutManager.SpanSizeLookup in HomeActivity) and are NOT focusable, so D-pad Up/Down always lands
 * on a real tile and focus can never get stuck on a header.
 */
class AppGridAdapter(
    private val onClick: (AppEntry) -> Unit,
    private val onLongPress: (AppEntry) -> Unit,
    private val onFocused: (Int, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var wide = true
    var accent = Color.WHITE
    var textColor = Color.WHITE
    var pins: Set<String> = emptySet()
    private var items: List<GridItem> = emptyList()

    class HeaderVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    class VH(
        val item: LinearLayout,
        val plate: FrameLayout,
        val icon: ImageView,
        val label: TextView,
        val star: TextView
    ) : RecyclerView.ViewHolder(item)

    fun submit(list: List<GridItem>) {
        items = list
        notifyDataSetChanged()
    }

    fun isHeader(pos: Int): Boolean = pos in items.indices && items[pos].header != null

    fun firstAppPosition(): Int {
        for (i in items.indices) if (items[i].app != null) return i
        return 0
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        if (items[position].header != null) 2 else if (wide) 1 else 0

    private fun plateBg(focused: Boolean, u: Float): Drawable {
        val base = GradientDrawable()
        base.shape = GradientDrawable.RECTANGLE
        base.cornerRadius = 20f * u
        if (focused) {
            base.setColor(Color.argb(90, 255, 255, 255))
            base.setStroke((3.5f * u).toInt().coerceAtLeast(2), accent)
        } else {
            base.setColor(Color.argb(40, 255, 255, 255))
            base.setStroke((1f * u).toInt().coerceAtLeast(1), Color.argb(60, 255, 255, 255))
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(if (focused) 80 else 45, 255, 255, 255), Color.argb(0, 255, 255, 255))
        )
        sheen.shape = GradientDrawable.RECTANGLE
        sheen.cornerRadius = 20f * u
        return LayerDrawable(arrayOf(base, sheen))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val u = ctx.resources.displayMetrics.heightPixels / 1080f

        if (viewType == 2) {
            val tv = TextView(ctx)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28f * u)
            tv.setShadowLayer(6f * u, 0f, 2f * u, Color.argb(150, 0, 0, 0))
            tv.setPadding((16 * u).toInt(), (16 * u).toInt(), 0, (4 * u).toInt())
            tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            tv.isFocusable = false
            return HeaderVH(tv)
        }

        val isWide = viewType == 1
        val plateW = ((if (isWide) 178 else 100) * u).toInt()
        val plateH = (100 * u).toInt()

        val item = LinearLayout(ctx)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER_HORIZONTAL
        item.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        item.setPadding(0, (8 * u).toInt(), 0, (6 * u).toInt())
        item.isFocusable = true
        item.isClickable = true
        item.isLongClickable = true
        item.clipChildren = false

        val plate = FrameLayout(ctx)
        plate.layoutParams = LinearLayout.LayoutParams(plateW, plateH)
        plate.background = plateBg(false, u)
        plate.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f * u)
            }
        }
        plate.clipToOutline = true

        val icon = ImageView(ctx)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        plate.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val star = TextView(ctx)
        star.text = "\u2605"
        star.setTextColor(Color.rgb(255, 214, 120))
        star.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f * u)
        star.setShadowLayer(4f * u, 0f, 1f * u, Color.argb(200, 0, 0, 0))
        val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
        slp.topMargin = (4 * u).toInt()
        slp.rightMargin = (8 * u).toInt()
        plate.addView(star, slp)

        val label = TextView(ctx)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f * u)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.alpha = 0f
        label.setShadowLayer(6f * u, 0f, 2f * u, Color.argb(170, 0, 0, 0))

        val labelLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        labelLp.topMargin = (8 * u).toInt()
        item.addView(plate)
        item.addView(label, labelLp)

        val vh = VH(item, plate, icon, label, star)

        item.setOnClickListener {
            val p = vh.bindingAdapterPosition
            val a = if (p != RecyclerView.NO_POSITION) items[p].app else null
            if (a != null) onClick(a)
        }
        item.setOnLongClickListener {
            val p = vh.bindingAdapterPosition
            val a = if (p != RecyclerView.NO_POSITION) items[p].app else null
            if (a != null) {
                onLongPress(a)
                true
            } else {
                false
            }
        }
        item.setOnFocusChangeListener { v, has ->
            val p = vh.bindingAdapterPosition
            val s = if (has) 1.10f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(Anim.ms(ctx, 170)).start()
            plate.background = plateBg(has, u)
            val a = if (p != RecyclerView.NO_POSITION) items[p].app else null
            val keepLabel = isWide && a != null && a.banner == null
            label.animate().alpha(if (has || keepLabel) 1f else 0f).setDuration(Anim.ms(ctx, 150)).start()
            if (has && p != RecyclerView.NO_POSITION) onFocused(p, v)
        }
        return vh
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val it = items[position]
        if (holder is HeaderVH) {
            holder.tv.text = it.header
            holder.tv.setTextColor(textColor)
            return
        }
        val h = holder as VH
        val e = it.app ?: return
        val u = h.item.resources.displayMetrics.heightPixels / 1080f
        val banner = e.banner
        if (wide && banner != null) {
            h.icon.scaleType = ImageView.ScaleType.CENTER_CROP
            h.icon.setPadding(0, 0, 0, 0)
            h.icon.setImageDrawable(banner)
        } else {
            h.icon.scaleType = ImageView.ScaleType.FIT_CENTER
            val ip = ((if (wide) 14 else 12) * u).toInt()
            h.icon.setPadding(ip, ip, ip, ip)
            h.icon.setImageDrawable(e.icon)
        }
        h.label.text = e.label
        h.label.setTextColor(textColor)
        h.item.contentDescription = e.label
        h.star.visibility = if (e.pkg.isNotEmpty() && pins.contains(e.pkg)) View.VISIBLE else View.GONE
        if (!h.item.isFocused) {
            h.item.scaleX = 1f
            h.item.scaleY = 1f
            h.label.alpha = if (wide && banner == null) 1f else 0f
            h.plate.background = plateBg(false, u)
        }
    }
}
