package com.auroratv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils

/**
 * AuroraTV's own settings, drawn entirely with Liquid Glass components (no Android settings widgets).
 * Home page = list of sections; Back inside a section returns to the list (focus restored); Back on the list
 * returns to the home screen.
 */
class SettingsActivity : BaseActivity() {

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var layers: List<ScenicLayer>
    private var u = 1f
    private var pal: Palette = Palettes.SUNSET
    private var page = -1
    private var built = false
    private var locationView: TextView? = null

    private val sections = listOf(
        "Appearance", "Home screen", "Clock & scenery", "Ambient mode",
        "Audio & volume", "Navigation & Home app", "About AuroraTV", "Recovery"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        u = Ui.u(this)
        pal = Ui.palette(this)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val sky = ScenicLayer(this, ScenicLayer.SKY)
        val far = ScenicLayer(this, ScenicLayer.FAR)
        val mid = ScenicLayer(this, ScenicLayer.MID)
        val fg = ScenicLayer(this, ScenicLayer.FG)
        layers = listOf(sky, far, mid, fg)
        for (l in layers) {
            l.palette = pal
            root.addView(l, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val scrim = View(this)
        scrim.setBackgroundColor(Color.argb(70, 0, 0, 0))
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        attachVolume(root)
        setContentView(root)

        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (!built && r - l > 0 && b - t > 0) {
                built = true
                Glass.capture(layers, r - l, b - t)
                showHome(0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (built) {
            // Returning from a sub-screen (e.g. pinned apps): rebuild so values are current.
            if (page < 0) showHome(0) else showPage(page, 0)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (page >= 0) showHome(page) else super.onBackPressed()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HomeRole.REQ) {
            val ok = HomeRole.isHome(this)
            Toast.makeText(this, if (ok) "AuroraTV is now your Home app" else "Home app not changed", Toast.LENGTH_LONG).show()
            if (page >= 0) showPage(page, 0)
        }
    }

    // ---------------------------------------------------------------- page scaffolding

    private fun newPage(title: String, subtitle: String?): LinearLayout {
        content.removeAllViews()
        val dm = resources.displayMetrics
        val panel = GlassPanel(this)
        panel.radius = 52f * u
        panel.applyTheme(pal)
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setPadding((40 * u).toInt(), (32 * u).toInt(), (40 * u).toInt(), (26 * u).toInt())
        outer.addView(Ui.text(this, title, 46f))
        if (subtitle != null) {
            val s = Ui.text(this, subtitle, 22f, Color.argb(190, 255, 255, 255))
            s.setPadding(0, (4 * u).toInt(), 0, 0)
            outer.addView(s)
        }
        val spacer = View(this)
        outer.addView(spacer, LinearLayout.LayoutParams(1, (16 * u).toInt()))
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipToPadding = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding((8 * u).toInt(), (8 * u).toInt(), (8 * u).toInt(), (8 * u).toInt())
        scroll.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(outer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(
            panel,
            FrameLayout.LayoutParams((dm.widthPixels * 0.62f).toInt(), (dm.heightPixels * 0.88f).toInt(), Gravity.CENTER)
        )
        return col
    }

    private fun focusChild(col: LinearLayout, index: Int) {
        col.post {
            val n = col.childCount
            if (n == 0) return@post
            var i = index.coerceIn(0, n - 1)
            while (i < n && !col.getChildAt(i).isFocusable) i++
            if (i >= n) i = 0
            col.getChildAt(i).requestFocus()
        }
    }

    private fun showHome(focusIndex: Int) {
        page = -1
        val col = newPage("AuroraTV settings", "Version " + versionName() + "  \u00B7  press Back to return to the home screen")
        for (i in sections.indices) {
            addAction(col, sections[i], "\u203A") { showPage(i, 0) }
        }
        focusChild(col, focusIndex)
    }

    private fun showPage(index: Int, focusIndex: Int) {
        page = index
        val col = newPage(sections[index], null)
        when (index) {
            0 -> buildAppearance(col)
            1 -> buildHomeScreen(col)
            2 -> buildClock(col)
            3 -> buildAmbient(col)
            4 -> buildAudio(col)
            5 -> buildNavigation(col)
            6 -> buildAbout(col)
            else -> buildRecovery(col)
        }
        focusChild(col, focusIndex)
    }

    private fun rethemeAll() {
        pal = Ui.palette(this)
        for (l in layers) l.palette = pal
        Glass.capture(layers, root.width, root.height)
        Ui.themeAll(content, pal)
        root.invalidate()
    }

    // ---------------------------------------------------------------- row builders (all custom glass)

    private fun innerRow(row: GlassPanel): LinearLayout {
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.HORIZONTAL
        inner.gravity = Gravity.CENTER_VERTICAL
        inner.setPadding((28 * u).toInt(), 0, (28 * u).toInt(), 0)
        row.addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return inner
    }

    private fun addAction(col: LinearLayout, title: String, trailing: String?, onClick: () -> Unit): TextView {
        val row = Ui.focusPanel(this, pal, 80f)
        val inner = innerRow(row)
        val t = Ui.text(this, title, 29f)
        inner.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) inner.addView(Ui.text(this, trailing, 34f, pal.accent))
        row.setOnClickListener { onClick() }
        col.addView(row)
        return t
    }

    private fun addInfo(col: LinearLayout, text: String) {
        val row = Ui.focusPanel(this, pal, 0f, false)
        val t = Ui.text(this, text, 23f, Color.argb(230, 255, 255, 255))
        t.setPadding((28 * u).toInt(), (18 * u).toInt(), (28 * u).toInt(), (18 * u).toInt())
        t.setLineSpacing(4f * u, 1f)
        row.addView(t, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(row)
    }

    private fun addChoice(col: LinearLayout, o: Option, onChange: (() -> Unit)? = null) {
        val row = Ui.focusPanel(this, pal, 80f)
        val inner = innerRow(row)
        inner.addView(Ui.text(this, o.title, 29f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = Ui.text(this, "", 29f, pal.accent)
        inner.addView(v)
        fun refresh() {
            v.text = "\u2039  " + o.values[Prefs.get(this, o)] + "  \u203A"
        }
        fun cycle(d: Int) {
            val n = o.values.size
            Prefs.set(this, o, (Prefs.get(this, o) + d + n) % n)
            refresh()
            onChange?.invoke()
        }
        refresh()
        row.setOnClickListener { cycle(1) }
        row.setOnKeyListener { _, keyCode, ev -> sideKeys(keyCode, ev) { cycle(it) } }
        col.addView(row)
    }

    /** Toggle backed by a two-value option. [onIndex] is the option index that means "on". */
    private fun addToggle(col: LinearLayout, o: Option, onIndex: Int, onChange: (() -> Unit)? = null) {
        addSwitch(col, o.title, { Prefs.get(this, o) == onIndex }, { on ->
            Prefs.set(this, o, if (on) onIndex else 1 - onIndex)
            onChange?.invoke()
        })
    }

    private fun addSwitch(col: LinearLayout, title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val row = Ui.focusPanel(this, pal, 80f)
        val inner = innerRow(row)
        inner.addView(Ui.text(this, title, 29f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchView(this)
        sw.accent = pal.accent
        sw.on = get()
        inner.addView(sw)
        fun flip(to: Boolean) {
            set(to)
            sw.on = get()
        }
        row.setOnClickListener { flip(!get()) }
        row.setOnKeyListener { _, keyCode, ev -> sideKeys(keyCode, ev) { d -> flip(d > 0) } }
        col.addView(row)
    }

    private fun addSlider(col: LinearLayout, title: String, key: String, def: Int, min: Int, max: Int, step: Int) {
        val row = Ui.focusPanel(this, pal, 80f)
        val inner = innerRow(row)
        inner.addView(Ui.text(this, title, 29f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueText = Ui.text(this, "", 26f, pal.accent)
        valueText.setPadding(0, 0, (18 * u).toInt(), 0)
        inner.addView(valueText)
        val sl = SliderView(this)
        sl.accent = pal.accent
        inner.addView(sl)
        fun refresh() {
            val v = Prefs.getInt(this, key, def).coerceIn(min, max)
            sl.fraction = (v - min).toFloat() / (max - min)
            valueText.text = v.toString() + "%"
        }
        fun adjust(d: Int) {
            val v = (Prefs.getInt(this, key, def) + d * step).coerceIn(min, max)
            Prefs.setInt(this, key, v)
            refresh()
            rethemeAll()
        }
        refresh()
        row.setOnClickListener { adjust(1) }
        row.setOnKeyListener { _, keyCode, ev -> sideKeys(keyCode, ev) { adjust(it) } }
        col.addView(row)
    }

    private fun sideKeys(keyCode: Int, ev: KeyEvent, act: (Int) -> Unit): Boolean {
        if (ev.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            act(if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
            return true
        }
        return false
    }

    // ---------------------------------------------------------------- sections

    private fun buildAppearance(col: LinearLayout) {
        addChoice(col, Options.PALETTE) { rethemeAll() }
        if (PhotoLayers.available(this)) addChoice(col, Options.WALLPAPER)
        addChoice(col, Options.GLASS) { rethemeAll() }
        addSlider(col, "Glass tint strength", "glass_strength", 55, 10, 100, 5)
        addChoice(col, Options.ANIM)
        addInfo(col, "Scenery \"Auto\" blends sunrise, day, sunset and twilight with the real time. Photos cross-fade between your morning, day and night pictures. Animation intensity also affects parallax and ambient motion.")
    }

    private fun buildHomeScreen(col: LinearLayout) {
        addChoice(col, Options.DOCK_STYLE)
        addChoice(col, Options.ALL_APPS)
        addAction(col, "Pinned apps (shown first)", "\u203A") { startActivity(Intent(this, PinnedAppsActivity::class.java)) }
        addAction(col, "Reset \"most used\" counts", null) {
            GlassDialog.show(this, "Reset most-used counts?", "Pinned apps stay pinned. AuroraTV will start counting again.", "Reset", "Cancel") { _ ->
                Usage.clear(this)
                Toast.makeText(this, "Counts cleared", Toast.LENGTH_SHORT).show()
            }
        }
        addInfo(col, "\"Most used\" shows your pinned apps first, then the apps you open most often from AuroraTV. Android only lets launchers read other apps' usage with a special \"usage access\" permission, which AuroraTV does not ask for, so launches made from other places are not counted. Counts stay on this TV. Hold OK on any app on the home screen to pin or unpin it.")
    }

    private fun locationLabel(): String = "Weather location: " + Weather.place(this).label

    private fun buildClock(col: LinearLayout) {
        addChoice(col, Options.CLOCK_FORMAT)
        addChoice(col, Options.CLOCK_POS)
        addChoice(col, Options.CLOCK_HEIGHT)
        addToggle(col, Options.SHOW_DATE, 0)
        addToggle(col, Options.WEATHER_ON, 0)
        addChoice(col, Options.TEMP_UNIT)
        addToggle(col, Options.WEATHER_SCENERY, 0)
        locationView = addAction(col, locationLabel(), "\u203A") {
            GlassDialog.show(this, "Weather location", "Type a city, e.g. Bellbrook, Ohio", "Search", "Cancel", "City, State") { q -> searchLocation(q) }
        }
        addInfo(col, "Weather uses the free Open-Meteo service. Only the city's coordinates are sent, nothing else. Turn Weather off to stop all internet use by AuroraTV. Weather data by Open-Meteo.com.")
    }

    private fun searchLocation(q: String) {
        if (q.isBlank()) return
        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show()
        Thread {
            val p = Weather.geocode(q)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (p == null) {
                    Toast.makeText(this, "Couldn't find that place (is Weather on and the TV online?)", Toast.LENGTH_LONG).show()
                } else {
                    Weather.savePlace(this, p)
                    locationView?.text = locationLabel()
                    Toast.makeText(this, "Weather location: " + p.label, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun buildAmbient(col: LinearLayout) {
        addChoice(col, Options.AMBIENT)
        addToggle(col, Options.DRIFT, 0)
        addToggle(col, Options.PARALLAX, 0)
        addInfo(col, "After the chosen time without any button press, the apps fade away and only the scenery and clock remain. Any button brings them back (that first press only wakes the screen). Nothing runs while another app is open, so the TV can still go to sleep.")
    }

    private fun buildAudio(col: LinearLayout) {
        addToggle(col, Options.VOL_PANEL, 0)
        addChoice(col, Options.VOL_STEP)
        addChoice(col, Options.VOL_TIME)
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val status = when (Prefs.getString(this, "vol_status", "untested")) {
            "ok" -> "Working: AuroraTV changed the TV volume."
            "fallback" -> "AuroraTV could not change the volume on this TV, so the TV's own volume bar is used."
            else -> "Not tested yet: press volume up or down on the remote while an AuroraTV screen is open."
        }
        addInfo(
            col,
            "Volume now " + am.getStreamVolume(AudioManager.STREAM_MUSIC) + " of " + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) +
                "  \u00B7  fixed-volume output: " + (if (am.isVolumeFixed) "yes (apps cannot change it)" else "no") + "\n" + status
        )
        addAction(col, "Test remote keys", "\u203A") { startActivity(Intent(this, KeyTestActivity::class.java)) }
        addInfo(col, "The panel only appears on AuroraTV screens. Over other apps, the TV shows its own volume bar (as in your photo). If the TV firmware handles volume before apps see the key, AuroraTV's panel cannot appear; the key test shows what your remote really sends.")
    }

    private fun aliasComponent() = ComponentName(this, "com.auroratv.launcher.HomeAlias")

    private fun isAliasEnabled(): Boolean =
        packageManager.getComponentEnabledSetting(aliasComponent()).let {
            // DEFAULT means "whatever the manifest says", and the manifest has the alias enabled.
            it == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || it == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }

    private fun setAlias(on: Boolean) {
        packageManager.setComponentEnabledSetting(
            aliasComponent(),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        if (!on) {
            @Suppress("DEPRECATION")
            packageManager.clearPackagePreferredActivities(packageName)
            Toast.makeText(this, "AuroraTV can no longer be chosen as Home. Going to the original home screen.", Toast.LENGTH_LONG).show()
            OriginalHome.launch(this)
        }
    }

    private fun buildNavigation(col: LinearLayout) {
        addInfo(
            col,
            if (HomeRole.isHome(this)) "AuroraTV is currently your Home app. The Home button returns here."
            else "AuroraTV is NOT your Home app yet, so the Home button still opens the TCL launcher."
        )
        addAction(col, "Make AuroraTV my Home app (one time)", "\u203A") { HomeRole.requestOrExplain(this) }
        addSwitch(col, Options.HOME_ALIAS.title, { isAliasEnabled() }, { on -> setAlias(on) })
        addAction(col, "Test remote keys", "\u203A") { startActivity(Intent(this, KeyTestActivity::class.java)) }
        addInfo(col, "Settings button: on your TV it opens Google TV's quick-settings panel (Inputs, Sound, Display...), drawn by the TV itself over AuroraTV. Apps are not given that key, and AuroraTV does not use accessibility tricks to take it. Use the Settings chip at the top right of the home screen, or the Menu button if your remote has one.")
        addInfo(col, "Back on the home screen does nothing, so you cannot fall out by accident. Opening Settings or an app and coming back returns to the same tile. This is not a kiosk lock: the Home choice can always be changed.")
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.2.0"
    } catch (e: Exception) {
        "0.2.0"
    }

    private fun buildAbout(col: LinearLayout) {
        addInfo(col, "AuroraTV " + versionName() + "\nPackage: " + packageName + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        addInfo(col, "Permissions: internet only, for weather. No accounts, analytics or tracking. Preferences and app counts are stored only on this TV. Weather data by Open-Meteo.com.")
        addInfo(col, "Troubleshooting: if apps are missing, set Home screen > Apps shown to All apps. If the scenery looks off, try Appearance > Glass effect > Simple tint or Animation intensity > Reduced. If something breaks, see Recovery.")
    }

    private fun buildRecovery(col: LinearLayout) {
        addInfo(col, "Your TCL launcher is never removed or disabled, so you can always go back.")
        addAction(col, "Go to the original TV home screen", "\u203A") { OriginalHome.launch(this) }
        addAction(col, "Stop AuroraTV being a Home choice", null) {
            GlassDialog.show(this, "Return to the TCL launcher?", "AuroraTV will no longer be offered as a Home app. You can turn it back on later in Navigation & Home app.", "Return", "Cancel") { _ -> setAlias(false) }
        }
        addAction(col, "Open TV system settings", "\u203A") {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't open TV settings", Toast.LENGTH_SHORT).show()
            }
        }
        addInfo(col, "If AuroraTV ever crashes: wait for the TV to show the TCL home screen, or press Home again. Then uninstall it in Settings > Apps > AuroraTV, or from your PC run: adb uninstall com.auroratv.launcher. If you used the optional adb set-home-activity step, uninstalling restores the TCL launcher.")
    }
}
