package com.auroratv.launcher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.auroratv.launcher.assistant.Assistants

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val COLS = 8

/**
 * The AuroraTV home screen: layered scenery (sky / far / mid), the clock, the foreground ridge, a glass shelf
 * with "Most used" (8 tiles) and a scrolling "All apps" grid, date + weather, and a Settings chip.
 */
class HomeActivity : BaseActivity() {

    private lateinit var root: FrameLayout
    private lateinit var sky: ScenicLayer
    private lateinit var far: ScenicLayer
    private lateinit var mid: ScenicLayer
    private lateinit var fg: ScenicLayer
    private lateinit var clock: TextClock
    private lateinit var dateView: TextClock
    private lateinit var weatherView: TextView
    private lateinit var creditView: TextView
    private lateinit var topInfo: LinearLayout
    private lateinit var shelf: GlassPanel
    private lateinit var chip: GlassPanel
    private lateinit var chipLabel: TextView
    private lateinit var emptyView: TextView
    private lateinit var grid: RecyclerView
    private lateinit var adapter: AppGridAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val drift = ArrayList<ObjectAnimator>()

    private var unit = 1f
    private var lastW = 0
    private var lastH = 0
    private var ambient = false
    private var resumed = false
    private var parallaxOn = true
    private var wideTiles = true
    private var lastFocusPos = -1
    private var clockOffset = 0f
    private var photoActive = false
    private var photosLoading = false
    private var fetching = false
    private var promptShown = false
    private var receiverRegistered = false
    private var pal: Palette = Palettes.SUNSET

    private val idleRunnable = Runnable { enterAmbient() }
    private val reloadRunnable = Runnable { loadApps() }
    private val paletteTick = object : Runnable {
        override fun run() {
            applyPalette()
            refreshWeather(false)
            handler.postDelayed(this, 60_000L)
        }
    }
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.removeCallbacks(reloadRunnable)
            handler.postDelayed(reloadRunnable, 700L)
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unit = resources.displayMetrics.heightPixels / 1080f
        Weather.load(this)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.clipChildren = false
        setContentView(root)

        sky = ScenicLayer(this, ScenicLayer.SKY)
        far = ScenicLayer(this, ScenicLayer.FAR)
        mid = ScenicLayer(this, ScenicLayer.MID)
        fg = ScenicLayer(this, ScenicLayer.FG)

        clock = TextClock(this)
        clock.setIncludeFontPadding(false)
        clock.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        clock.gravity = Gravity.CENTER
        clock.setShadowLayer(14f * unit, 0f, 4f * unit, Color.argb(90, 0, 0, 0))

        dateView = TextClock(this)
        dateView.format12Hour = "EEEE d MMMM"
        dateView.format24Hour = "EEEE d MMMM"
        dateView.setShadowLayer(8f * unit, 0f, 2f * unit, Color.argb(140, 0, 0, 0))

        weatherView = TextView(this)
        weatherView.setShadowLayer(8f * unit, 0f, 2f * unit, Color.argb(140, 0, 0, 0))
        weatherView.setPadding((18 * unit).toInt(), 0, 0, 0)

        creditView = TextView(this)
        creditView.text = "Weather data by Open-Meteo.com"

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(dateView, LinearLayout.LayoutParams(WRAP, WRAP))
        row.addView(weatherView, LinearLayout.LayoutParams(WRAP, WRAP))
        topInfo = LinearLayout(this)
        topInfo.orientation = LinearLayout.VERTICAL
        topInfo.addView(row, LinearLayout.LayoutParams(WRAP, WRAP))
        topInfo.addView(creditView, LinearLayout.LayoutParams(WRAP, WRAP))

        // Settings chip (top right): a prominent, always-available way into AuroraTV settings.
        chip = GlassPanel(this)
        chip.radius = 60f * unit
        chip.isFocusable = true
        chip.isClickable = true
        chip.showFocusRing = true
        chip.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.gravity = Gravity.CENTER_VERTICAL
        chipRow.setPadding((22 * unit).toInt(), (10 * unit).toInt(), (26 * unit).toInt(), (10 * unit).toInt())
        val chipIcon = ImageView(this)
        chipIcon.setImageResource(R.drawable.ic_settings_sliders)
        chipRow.addView(chipIcon, LinearLayout.LayoutParams((38 * unit).toInt(), (38 * unit).toInt()))
        chipLabel = Ui.text(this, "Settings", 26f)
        chipLabel.setPadding((10 * unit).toInt(), 0, 0, 0)
        chipRow.addView(chipLabel, LinearLayout.LayoutParams(WRAP, WRAP))
        chip.addView(chipRow)
        chip.setOnClickListener { openSettings() }

        // Z-order, back to front: sky, far, mid, CLOCK, foreground ridge, info, shelf, chip, volume panel.
        root.addView(sky, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(far, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(mid, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(clock, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
        root.addView(fg, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(topInfo, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))

        adapter = AppGridAdapter(
            { entry -> launch(entry) },
            { entry -> togglePin(entry) },
            { pos, view -> onTileFocus(pos, view) }
        )
        grid = RecyclerView(this)
        val glm = GridLayoutManager(this, COLS)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (adapter.isHeader(position)) COLS else 1
        }
        grid.layoutManager = glm
        grid.adapter = adapter
        grid.clipChildren = false
        grid.clipToPadding = false
        grid.itemAnimator = null
        grid.overScrollMode = View.OVER_SCROLL_NEVER

        emptyView = Ui.text(this, "No apps found yet.\nInstall an app and it will appear here.", 30f)
        emptyView.gravity = Gravity.CENTER
        emptyView.visibility = View.GONE

        shelf = GlassPanel(this)
        shelf.radius = 56f * unit
        shelf.clipChildren = false
        shelf.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
        shelf.addView(emptyView, FrameLayout.LayoutParams(MATCH, MATCH))

        root.addView(shelf, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        root.addView(chip, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END))
        attachVolume(root)

        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = r - l
            val h = b - t
            if (w > 0 && h > 0 && (w != lastW || h != lastH)) {
                lastW = w
                lastH = h
                applyMetrics(w, h)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            val f = IntentFilter()
            f.addAction(Intent.ACTION_PACKAGE_ADDED)
            f.addAction(Intent.ACTION_PACKAGE_REMOVED)
            f.addAction(Intent.ACTION_PACKAGE_REPLACED)
            f.addDataScheme("package")
            ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        applyPrefs()
        refreshWeather(false)
        loadApps()
        scheduleIdle()
        handler.removeCallbacks(paletteTick)
        handler.postDelayed(paletteTick, 60_000L)
        maybePromptForHome()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        // Stop everything so the TV can sleep normally; nothing runs while we are in the background.
        handler.removeCallbacks(idleRunnable)
        handler.removeCallbacks(paletteTick)
        leaveAmbient(false)
        stopDrift()
    }

    override fun onStop() {
        super.onStop()
        if (receiverRegistered) {
            unregisterReceiver(pkgReceiver)
            receiverRegistered = false
        }
    }

    // ---------------------------------------------------------------- one-time Home setup

    private fun maybePromptForHome() {
        if (promptShown) return
        if (Prefs.getString(this, "home_prompt", "0") != "0") return
        if (HomeRole.isHome(this)) return
        promptShown = true
        handler.postDelayed({
            if (!isFinishing && !isDestroyed && resumed) {
                GlassDialog.show(
                    this,
                    "Make AuroraTV your Home screen",
                    "One-time step. Android will ask you to confirm. Your TCL launcher stays installed, and you can switch back any time in Settings > Recovery.",
                    "Set as Home",
                    "Not now",
                    null,
                    { Prefs.setString(this, "home_prompt", "1") }
                ) { _ ->
                    Prefs.setString(this, "home_prompt", "1")
                    HomeRole.requestOrExplain(this)
                }
            }
        }, 1500L)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HomeRole.REQ) {
            val ok = HomeRole.isHome(this)
            Toast.makeText(this, if (ok) "AuroraTV is now your Home app" else "Home app not changed", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- layout & style

    private fun applyMetrics(w: Int, h: Int) {
        val safeX = (w * 0.05f).toInt()
        val safeY = (h * 0.05f).toInt()

        applyLayerScale()

        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.31f)
        updateClockLayout()

        dateView.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.028f)
        weatherView.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.028f)
        creditView.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.016f)
        val tlp = topInfo.layoutParams as FrameLayout.LayoutParams
        tlp.topMargin = safeY
        tlp.leftMargin = safeX
        topInfo.layoutParams = tlp

        val clp = chip.layoutParams as FrameLayout.LayoutParams
        clp.topMargin = safeY
        clp.rightMargin = safeX
        chip.layoutParams = clp

        val slp = shelf.layoutParams as FrameLayout.LayoutParams
        slp.width = (w * 0.88f).toInt()
        slp.height = (h * 0.45f).toInt()
        slp.bottomMargin = safeY
        shelf.layoutParams = slp
        val padX = (22 * unit).toInt()
        grid.setPadding(padX, (6 * unit).toInt(), padX, (30 * unit).toInt())
        applyPalette()
    }

    private fun applyLayerScale() {
        for (l in listOf(sky, mid, fg)) {
            if (photoActive) {
                l.scaleX = 1.04f
                l.scaleY = 1.04f
            } else {
                l.scaleX = 1.10f
                l.scaleY = 1f
            }
        }
        far.scaleX = 1.10f
        far.scaleY = 1f
        far.visibility = if (photoActive) View.GONE else View.VISIBLE
    }

    private fun updateClockLayout() {
        if (lastH == 0) return
        val lp = clock.layoutParams as FrameLayout.LayoutParams
        val left = Prefs.get(this, Options.CLOCK_POS) == 0
        lp.gravity = if (left) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.leftMargin = if (left) (lastW * 0.085f).toInt() else 0
        lp.topMargin = ((0.162f + clockOffset) * lastH).toInt()
        clock.layoutParams = lp
    }

    private fun applyPrefs() {
        parallaxOn = Prefs.get(this, Options.PARALLAX) == 0 && Anim.scale(this) > 0f
        wideTiles = Prefs.get(this, Options.DOCK_STYLE) == 0
        adapter.wide = wideTiles

        when (Prefs.get(this, Options.CLOCK_FORMAT)) {
            1 -> {
                clock.format12Hour = "h:mm"
                clock.format24Hour = "h:mm"
            }
            2 -> {
                clock.format12Hour = "HH:mm"
                clock.format24Hour = "HH:mm"
            }
            else -> {
                clock.format12Hour = "h:mm"
                clock.format24Hour = "HH:mm"
            }
        }
        dateView.visibility = if (Prefs.get(this, Options.SHOW_DATE) == 0) View.VISIBLE else View.GONE

        clockOffset = when (Prefs.get(this, Options.CLOCK_HEIGHT)) {
            0 -> 0.04f
            2 -> -0.04f
            else -> 0f
        }
        updateClockLayout()

        if (!parallaxOn) {
            for (v in listOf<View>(sky, far, mid, clock, fg)) v.translationX = 0f
        }
        applyWallpaper()
        applyPalette()
        updateWeatherUi()
    }

    private fun applyPalette() {
        var p = Palettes.forIndex(Prefs.get(this, Options.PALETTE), Palettes.nowHours())
        val d = Weather.current
        val weatherOn = Prefs.get(this, Options.WEATHER_ON) == 0
        if (weatherOn && Prefs.get(this, Options.WEATHER_SCENERY) == 0 && d != null) {
            p = p.grayed(Weather.grayAmount(d.code))
            sky.cloudiness = Weather.cloudiness(d.code)
        } else {
            sky.cloudiness = 0.35f
        }
        pal = p
        sky.palette = p
        far.palette = p
        mid.palette = p
        fg.palette = p
        applyPhotoWeights()

        clock.setTextColor(ColorUtils.setAlphaComponent(p.text, 235))
        dateView.setTextColor(ColorUtils.setAlphaComponent(p.text, 225))
        weatherView.setTextColor(ColorUtils.setAlphaComponent(p.text, 225))
        creditView.setTextColor(ColorUtils.setAlphaComponent(p.text, 120))
        chipLabel.setTextColor(p.text)
        adapter.accent = p.accent
        adapter.textColor = p.text
        refreshGlass()
    }

    /** Re-capture the scenery for the frosted glass and re-theme the glass panels. */
    private fun refreshGlass() {
        if (lastW == 0) return
        Glass.capture(listOf(sky, far, mid, fg), lastW, lastH)
        shelf.applyTheme(pal)
        chip.applyTheme(pal)
        shelf.invalidate()
        chip.invalidate()
        grid.invalidate()
    }

    // ---------------------------------------------------------------- photo wallpaper

    private fun applyWallpaper() {
        val want = Prefs.get(this, Options.WALLPAPER) == 1 && PhotoLayers.available(this)
        if (!want) {
            if (photoActive || sky.photoMode) {
                photoActive = false
                for (l in listOf(sky, far, mid, fg)) l.photoMode = false
                applyLayerScale()
            }
            return
        }
        if (photoActive || photosLoading) return
        photosLoading = true
        Thread {
            val layers = listOf(sky, mid, fg)
            val loaded = ArrayList<Array<Bitmap?>>()
            for (li in PhotoLayers.LAYERS.indices) {
                val arr = arrayOfNulls<Bitmap>(PhotoLayers.SCENES.size)
                for (si in PhotoLayers.SCENES.indices) {
                    arr[si] = PhotoLayers.load(this, "photo_" + PhotoLayers.SCENES[si] + "_" + PhotoLayers.LAYERS[li], li == 0)
                }
                loaded.add(arr)
            }
            runOnUiThread {
                photosLoading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (Prefs.get(this, Options.WALLPAPER) != 1) return@runOnUiThread
                for (i in layers.indices) {
                    layers[i].setPhotos(loaded[i])
                    layers[i].photoMode = true
                }
                photoActive = true
                applyLayerScale()
                applyPhotoWeights()
                refreshGlass()
            }
        }.start()
    }

    private fun applyPhotoWeights() {
        if (!photoActive) return
        val w = PhotoLayers.weights(Prefs.get(this, Options.PALETTE), Palettes.nowHours())
        sky.setPhotoWeights(w)
        mid.setPhotoWeights(w)
        fg.setPhotoWeights(w)
    }

    // ---------------------------------------------------------------- weather

    private fun refreshWeather(force: Boolean) {
        if (Prefs.get(this, Options.WEATHER_ON) != 0) {
            updateWeatherUi()
            return
        }
        if (!force && !Weather.needsRefresh(this)) return
        if (fetching) return
        fetching = true
        Thread {
            Weather.fetch(this)
            runOnUiThread {
                fetching = false
                if (!isFinishing && !isDestroyed) {
                    updateWeatherUi()
                    applyPalette()
                }
            }
        }.start()
    }

    private fun updateWeatherUi() {
        val on = Prefs.get(this, Options.WEATHER_ON) == 0
        val d = Weather.current
        if (!on || d == null) {
            weatherView.visibility = View.GONE
            creditView.visibility = View.GONE
            return
        }
        weatherView.text = "|   " + d.temp + "\u00B0  " + Weather.describe(d.code)
        weatherView.visibility = View.VISIBLE
        creditView.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- apps

    private fun loadApps() {
        val includeAll = Prefs.get(this, Options.ALL_APPS) == 1
        Thread {
            val all = AppScanner.scan(this, includeAll)
            val pins = Prefs.getPins(this)
            val counts = Usage.counts(this)
            val byPkg = HashMap<String, AppEntry>()
            for (a in all) byPkg[a.pkg] = a

            // Top 8: pinned first, then the apps opened most from AuroraTV, then alphabetical fill.
            val top = ArrayList<AppEntry>()
            for (p in pins) {
                val a = byPkg[p]
                if (a != null && top.size < COLS && !top.contains(a)) top.add(a)
            }
            for (a in all.filter { (counts[it.pkg] ?: 0) > 0 }.sortedByDescending { counts[it.pkg] ?: 0 }) {
                if (top.size < COLS && !top.contains(a)) top.add(a)
            }
            for (a in all) {
                if (top.size < COLS && !top.contains(a)) top.add(a)
            }
            val rest = all.filter { !top.contains(it) }

            val items = ArrayList<GridItem>()
            if (all.isNotEmpty()) {
                items.add(GridItem("Most used", null))
                for (a in top) items.add(GridItem(null, a))
            }
            items.add(GridItem("All apps", null))
            val tvSettings = Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            items.add(GridItem(null, AppEntry("AuroraTV Settings", ContextCompat.getDrawable(this, R.drawable.ic_settings_sliders), null, null, true)))
            items.add(GridItem(null, AppEntry("TV Settings", ContextCompat.getDrawable(this, R.drawable.ic_tv_settings), null, tvSettings)))
            for (a in rest) items.add(GridItem(null, a))

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                adapter.wide = wideTiles
                adapter.pins = pins.toSet()
                adapter.submit(items)
                emptyView.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
                if (lastFocusPos < 0 || lastFocusPos >= items.size || adapter.isHeader(lastFocusPos)) lastFocusPos = adapter.firstAppPosition()
                if (!ambient) restoreFocus()
            }
        }.start()
    }

    private fun launch(entry: AppEntry) {
        if (entry.isSettings) {
            openSettings()
            return
        }
        try {
            startActivity(entry.intent)
            Usage.record(this, entry.pkg)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open " + entry.label, Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePin(entry: AppEntry) {
        if (entry.pkg.isEmpty()) return
        val nowPinned = Prefs.togglePin(this, entry.pkg)
        Toast.makeText(this, if (nowPinned) "Pinned " + entry.label else "Unpinned " + entry.label, Toast.LENGTH_SHORT).show()
        loadApps()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun restoreFocus() {
        grid.post {
            val pos = lastFocusPos
            val vh = grid.findViewHolderForAdapterPosition(pos)
            if (vh != null) {
                vh.itemView.requestFocus()
            } else {
                grid.scrollToPosition(pos)
                grid.post { grid.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus() }
            }
        }
    }

    // ---------------------------------------------------------------- focus & parallax

    private fun onTileFocus(pos: Int, v: View) {
        lastFocusPos = pos
        // Keep the focused row comfortably inside the shelf (about 40% from its top) so rows scroll smoothly.
        val dy = (v.top + v.height / 2) - (grid.height * 0.40f).toInt()
        if (dy != 0) grid.smoothScrollBy(0, dy)
        applyParallax(pos)
    }

    private fun applyParallax(pos: Int) {
        val n = adapter.itemCount
        if (!parallaxOn || n < 2 || lastW == 0) return
        val frac = (pos % COLS).toFloat() / (COLS - 1) - 0.5f
        val k = if (photoActive) 0.35f else 1f
        val dur = Anim.ms(this, 700)
        fun mv(v: View, f: Float) {
            v.animate()
                .translationX(-frac * f * k * lastW)
                .setDuration(dur)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        mv(sky, 0.010f)
        mv(far, 0.025f)
        mv(mid, 0.045f)
        mv(clock, 0.060f)
        mv(fg, 0.090f)
    }

    // ---------------------------------------------------------------- ambient mode

    private fun scheduleIdle() {
        handler.removeCallbacks(idleRunnable)
        val ms = Options.ambientMillis(Prefs.get(this, Options.AMBIENT))
        if (ms > 0L && !ambient) handler.postDelayed(idleRunnable, ms)
    }

    private fun enterAmbient() {
        if (ambient) return
        ambient = true
        val d = Anim.ms(this, 900)
        shelf.animate().alpha(0f).setDuration(d).withEndAction {
            if (ambient) shelf.visibility = View.INVISIBLE
        }.start()
        chip.animate().alpha(0f).setDuration(d).withEndAction {
            if (ambient) chip.visibility = View.INVISIBLE
        }.start()
        topInfo.animate().alpha(0.6f).setDuration(d).start()
        if (Prefs.get(this, Options.DRIFT) == 0 && Anim.scale(this) > 0f) startDrift()
    }

    private fun leaveAmbient(animated: Boolean) {
        if (!ambient) return
        ambient = false
        stopDrift()
        val d = if (animated) Anim.ms(this, 350) else 0L
        shelf.visibility = View.VISIBLE
        chip.visibility = View.VISIBLE
        shelf.animate().alpha(1f).setDuration(d).start()
        chip.animate().alpha(1f).setDuration(d).start()
        topInfo.animate().alpha(1f).setDuration(d).start()
        restoreFocus()
    }

    private fun startDrift() {
        stopDrift()
        val u = unit
        val specs = listOf(
            Triple(sky as View, 4f, 47000L),
            Triple(far as View, 7f, 39000L),
            Triple(mid as View, 10f, 33000L),
            Triple(clock as View, 9f, 41000L),
            Triple(fg as View, 13f, 29000L)
        )
        for ((v, amp, dur) in specs) {
            val a = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, -amp * u, amp * u)
            a.duration = dur
            a.repeatMode = ValueAnimator.REVERSE
            a.repeatCount = ValueAnimator.INFINITE
            a.interpolator = AccelerateDecelerateInterpolator()
            a.start()
            drift.add(a)
        }
    }

    private fun stopDrift() {
        if (drift.isEmpty()) return
        for (a in drift) a.cancel()
        drift.clear()
        for (v in listOf<View>(sky, far, mid, clock, fg)) {
            v.animate().translationY(0f).setDuration(600).start()
        }
    }

    // ---------------------------------------------------------------- input

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Volume keys never wake ambient mode; they go straight to the volume panel (BaseActivity).
        if (volume?.isVolumeKey(event) == true) return super.dispatchKeyEvent(event)
        if (ambient) {
            // First key press only wakes the screen; it is not passed on to the grid.
            if (event.action == KeyEvent.ACTION_DOWN) {
                leaveAmbient(true)
                scheduleIdle()
            }
            return true
        }
        scheduleIdle()
        return super.dispatchKeyEvent(event)
    }

    private fun runningAsHome(): Boolean = intent?.hasCategory(Intent.CATEGORY_HOME) == true

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (ambient) {
            leaveAmbient(true)
            scheduleIdle()
        } else if (resumed && intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            // Home pressed while already on the home screen: jump back to the first tile.
            lastFocusPos = adapter.firstAppPosition()
            grid.scrollToPosition(0)
            restoreFocus()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // As the Home screen there is nothing to go back to; as a normal app, Back leaves as usual.
        if (runningAsHome()) return
        super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            // On the user's TCL/Google TV the Settings button opens the TV's own quick-settings panel
            // before apps see the key (confirmed by photo), so this only fires on remotes that do deliver it.
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                openSettings()
                return true
            }
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST -> {
                if (Assistants.provider.onVoiceKey(this)) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
