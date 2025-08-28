package com.bhashana.virtualmenu

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import kotlin.math.abs

class FloatingMenuService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isShuttingDown = false
    private var lastDismissUptime = 0L

    private val triggerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("FloatingBackService", "onReceive() ${intent.action}")
            if (intent.action == MenuContract.ACTION_SHOW_MENU) {
                if (Settings.canDrawOverlays(this@FloatingMenuService)) {
                    showFloatingMenu()
                    vibrate()
                } else {
                    // Optionally notify/toast that overlay permission is needed
                }
            }
        }
    }

    override fun onServiceConnected() {
        Log.d("FloatingBackService", "onServiceConnected()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(triggerReceiver, IntentFilter(MenuContract.ACTION_SHOW_MENU),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RECEIVER_NOT_EXPORTED  // or RECEIVER_EXPORTED if needed
                } else {
                    TODO("VERSION.SDK_INT < TIRAMISU")
                }
            )
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.e("FloatingBackService", "Overlay permission not granted")
            // Optionally: launch an activity to request it.
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // showFloatingMenu()

        vibrate()
    }

    private fun tintIcon(view: View, iconId: Int, attr: Int) {
        val iv = view.findViewById<ImageView>(iconId)
        val color = MaterialColors.getColor(iv, attr)
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(color))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingMenu() {
        if (overlayView != null || isShuttingDown) return

        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        val flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            /*x = loadInt("overlay_x", 0)          // ← optional persistence
            y = loadInt("overlay_y", 500)*/
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Use a Material3 theme for proper attribute resolution
        val baseThemed = ContextThemeWrapper(this, R.style.Theme_VirtualBack)
        val dynamicCtx = DynamicColors.wrapContextIfAvailable(baseThemed)
        val inflater = LayoutInflater.from(dynamicCtx)

        // Root acts as touch-guard
        val root = FrameLayout(dynamicCtx).apply {
            // Optional: scrim color
            setBackgroundColor(0x33000000) // light dim; or leave fully transparent
            isClickable = true // ensure it can receive clicks
            isFocusable = true
        }

        val menu = inflater.inflate(R.layout.floating_menu, root, false)

        val shapeDrawable = MaterialShapeDrawable().apply {
            initializeElevationOverlay(menu.context)
            setCornerSize(32f) // or from resources: context.resources.getDimension(R.dimen.corner_radius)
            fillColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(menu, com.google.android.material.R.attr.colorSurface),
                    (0.9f * 255).toInt()
                )
            )

            elevation = ViewCompat.getElevation(menu)
        }

        menu.background = shapeDrawable

        val suf = orientationSuffix(root.context)
        val (sw, sh) = currentScreenSize(root.context)

        // Position menu where you want (e.g., using LayoutParams margins)
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = loadInt("overlay_x_$suf", (sw - 540) / 2)
            topMargin = loadInt("overlay_y_$suf", (sh - 703) / 2)
            Log.d("FloatingBackService", "Screen Orientation: $suf, Current screen size: $sw x $sh")
        }
        root.addView(menu, lp)

        // Once menu is laid out, adjust so it's truly centered
        menu.post {
            if (!hasSavedPosition(suf)) {
                lp.leftMargin = (sw - menu.width) / 2
                lp.topMargin = (sh - menu.height) / 2
                Log.d("FloatingBackService", "Current menu size: ${menu.width} x ${menu.height}")
                root.updateViewLayout(menu, lp)
            }
        }

        // Close when tapping outside the menu
        root.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val hit = IntArray(2).also { menu.getLocationOnScreen(it) }
                    val x = ev.rawX.toInt()
                    val y = ev.rawY.toInt()
                    val inside = x in hit[0]..(hit[0] + menu.width) &&
                            y in hit[1]..(hit[1] + menu.height)
                    if (!inside) {
                        dismissOverlay(disableService = false)
                        true // consume
                    } else false
                }

                else -> false
            }
        }

        // Helper to configure an item include
        fun bindItem(rootId: Int, iconRes: Int, labelText: String, onClick: () -> Unit) {
            val itemRoot = menu.findViewById<View>(rootId)
            itemRoot.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
            itemRoot.findViewById<TextView>(R.id.label).text = labelText
            itemRoot.contentDescription = labelText
            itemRoot.setOnClickListener { onClick() }
        }

        bindItem(
            R.id.itemBack,
            R.drawable.ic_back,                // <- your drawable
            "Back"
        ) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            vibrate()
        }

        bindItem(
            R.id.itemHome,
            R.drawable.ic_home,
            "Home"
        ) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            vibrate()
            dismissOverlay(disableService = false)
        }

        bindItem(
            R.id.itemRecents,
            R.drawable.ic_notifications,
            "Panel"
        ) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            vibrate()
            dismissOverlay(disableService = false)
        }

        bindItem(
            R.id.itemLock,
            R.drawable.ic_lock,
            "Lock"
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
            vibrate()
            dismissOverlay(disableService = false)
        }

        bindItem(
            R.id.itemSS,
            R.drawable.ic_ss,
            "Capture"
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
            vibrate()
            dismissOverlay(disableService = false)
        }

        tintIcon(
            menu.findViewById(R.id.itemBack), R.id.icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        tintIcon(
            menu.findViewById(R.id.itemHome), R.id.icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        tintIcon(
            menu.findViewById(R.id.itemRecents), R.id.icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        tintIcon(
            menu.findViewById(R.id.itemLock), R.id.icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        tintIcon(
            menu.findViewById(R.id.itemSS), R.id.icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        tintIcon(menu, R.id.logo, com.google.android.material.R.attr.colorOnSurface)

        /*// Tint logo
        val logoImageView = menu.findViewById<ImageView>(R.id.logo)
        val color = MaterialColors.getColor(
            logoImageView,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        ImageViewCompat.setImageTintList(logoImageView, ColorStateList.valueOf(color))*/

        menu.enableDragWithinRoot(root, lp)
        windowManager.addView(root, params)
        overlayView = root

        // Optional: save final position when you remove the view
        overlayView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                val suf = orientationSuffix(v.context)
                saveInt("overlay_x_$suf", lp.leftMargin)
                saveInt("overlay_y_$suf", lp.topMargin)
                Log.d(
                    "FloatingBackService",
                    "Screen orientation: $suf, position: (${lp.leftMargin}, ${lp.topMargin})"
                )
            }
        })
    }

    private fun Context.hasSavedPosition(suf: String): Boolean {
        val prefs = getSharedPreferences("overlay_prefs", MODE_PRIVATE)
        return prefs.contains("overlay_x_$suf") && prefs.contains("overlay_y_$suf")
    }

    private fun currentScreenSize(ctx: Context): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = ctx.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            Point().also {
                (ctx.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(
                    it
                )
            }
        }

    private fun orientationSuffix(ctx: Context) = if (ctx.isLandscape()) "land" else "port"

    private fun Context.isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun dismissOverlay(disableService: Boolean = false, deferMs: Long = 120L) {
        overlayView?.let { v ->
            try {
                windowManager.removeViewImmediate(v)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        lastDismissUptime = SystemClock.uptimeMillis()

        if (disableService) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    disableSelf()
                } catch (_: Throwable) {
                }
            }, deferMs) // defer past the current gesture & frame(s)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun View.enableDragWithinRoot(
        root: FrameLayout,
        lp: FrameLayout.LayoutParams
    ) {
        var downX = 0f
        var downY = 0f
        var startLeft = 0
        var startTop = 0
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        // For clamping within the screen
        fun screenSize(): Point = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            Point().also {
                (context.getSystemService(WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay.getSize(it)
            }
        }

        fun clamp(v: Int, min: Int, max: Int) = v.coerceIn(min, max)

        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startLeft = lp.leftMargin
                    startTop = lp.topMargin
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()

                    val (sw, sh) = screenSize().let { it.x to it.y }
                    val vw = width.takeIf { it > 0 } ?: 1
                    val vh = height.takeIf { it > 0 } ?: 1

                    lp.leftMargin = clamp(startLeft + dx, 0, sw - vw)
                    lp.topMargin = clamp(startTop + dy, 0, sh - vh)

                    root.updateViewLayout(this, lp)   // <- update child in the root
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val moved = (abs(e.rawX - downX) > touchSlop) ||
                            (abs(e.rawY - downY) > touchSlop)
                    if (!moved) performClick()
                    moved
                }

                else -> false
            }
        }
    }

    // Tiny helpers for persistence
    private fun Context.saveInt(key: String, value: Int) {
        getSharedPreferences("overlay_prefs", MODE_PRIVATE).edit { putInt(key, value) }
    }

    private fun Context.loadInt(key: String, def: Int) =
        getSharedPreferences("overlay_prefs", MODE_PRIVATE).getInt(key, def)


    private fun removeFloatingMenu() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // ignore
        } finally {
            overlayView = null
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(triggerReceiver) }
        removeFloatingMenu()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        // idempotent: dismissOverlay() already null-checks
        dismissOverlay(disableService = false)
    }

    private fun vibrate() {
        // Get a Vibrator in the SDK-correct way
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator?.hasVibrator() != true) return

        when {
            // Predefined effects (API 29+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            }
            // One-shot effect (API 26+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
            // Legacy (pre-26)
            else -> {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50L)
            }
        }
    }
}