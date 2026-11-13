package com.bhashana.virtualmenu.services

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Build.VERSION_CODES.P
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.core.content.edit
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.bhashana.virtualmenu.BUTTON_TYPE_PREFS
import com.bhashana.virtualmenu.KEY_TRIGGER_MODE
import com.bhashana.virtualmenu.MenuContract
import com.bhashana.virtualmenu.OVERLAY_MENU_PREFS
import com.bhashana.virtualmenu.R
import com.bhashana.virtualmenu.ui.views.FloatingMenuView
import com.google.android.material.color.DynamicColors
import kotlin.math.abs
import java.util.function.Consumer

class FloatingMenuService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var menuDialog: Dialog? = null
    private var menuContentView: FloatingMenuView? = null
    private var menuWindowParams: WindowManager.LayoutParams? = null
    private var blurEnabledListener: Consumer<Boolean>? = null
    private var isShuttingDown = false
    private var lastDismissUptime = 0L
    private var triggerMode: String? = "accessibility"

    private val triggerReceiver =
        object : BroadcastReceiver() {
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
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        Log.d("FloatingBackService", "onServiceConnected()")

        /*
        // TODO: Check when enabling the overlay button
        if (!Settings.canDrawOverlays(this)) {
            Log.e("FloatingBackService", "Overlay permission not granted")
            // Optionally: launch an activity to request it.
            return
        }*/

        val prefs = getSharedPreferences(BUTTON_TYPE_PREFS, MODE_PRIVATE)
        triggerMode = prefs.getString(KEY_TRIGGER_MODE, "accessibility")

        if (triggerMode == "accessibility") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val buttonController = accessibilityButtonController
                buttonController.registerAccessibilityButtonCallback(
                    object : AccessibilityButtonController.AccessibilityButtonCallback() {
                        override fun onClicked(controller: AccessibilityButtonController) {
                            // Toggle or show the menu instantly
                            if (overlayView == null) {
                                showFloatingMenu()
                                vibrate()
                            } else {
                                dismissOverlay(disableService = false)
                            }
                        }
                    }
                )
            } else {
                // API 24-25: AccessibilityButtonController unavailable, show menu directly
                showFloatingMenu()
            }
        } else {
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RECEIVER_NOT_EXPORTED
                } else {
                    0
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(
                    triggerReceiver,
                    IntentFilter(MenuContract.ACTION_SHOW_MENU),
                    flags,
                )
            }
        }

        vibrate()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingMenu() {
        if (overlayView != null || isShuttingDown) return

        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val baseThemed = ContextThemeWrapper(this, R.style.Theme_VirtualBack)
        val dynamicCtx = DynamicColors.wrapContextIfAvailable(baseThemed)
        val root = FrameLayout(dynamicCtx).apply {
            setBackgroundColor(0x33000000)
            isClickable = true
        }
        val guardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type, flags, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val menuView = FloatingMenuView(dynamicCtx).apply {
            setOnActionListener { action ->
                when (action) {
                    FloatingMenuView.Action.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                    FloatingMenuView.Action.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                    FloatingMenuView.Action.PANEL -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    FloatingMenuView.Action.LOCK -> if (Build.VERSION.SDK_INT >= P)
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    FloatingMenuView.Action.CAPTURE -> if (Build.VERSION.SDK_INT >= P)
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                }
                vibrate()
                if (action != FloatingMenuView.Action.BACK) dismissOverlay(disableService = false)
            }
        }
        val suffix = orientationSuffix(dynamicCtx)
        val (screenWidth, screenHeight) = currentScreenSize(dynamicCtx)
        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // Background blur is rendered by DecorView's hardware renderer. Replacing
            // the dialog attributes must preserve acceleration for this service window.
            type, flags or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = loadInt("overlay_x_$suffix", (screenWidth - 540) / 2)
            y = loadInt("overlay_y_$suffix", (screenHeight - 703) / 2)
            if (Build.VERSION.SDK_INT >= P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                dismissOverlay(disableService = false)
                true
            } else false
        }

        windowManager.addView(root, guardParams)
        overlayView = root
        val dialog = Dialog(dynamicCtx, R.style.Theme_VirtualBack_FloatingMenu)
        dialog.setContentView(menuView)
        dialog.setCanceledOnTouchOutside(false)
        val menuWindow = dialog.window ?: error("Menu dialog has no window")
        menuWindow.setType(type)
        menuWindow.setGravity(Gravity.TOP or Gravity.START)
        menuWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        menuWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        menuWindow.setFlags(flags, flags)
        menuWindow.attributes = menuParams
        menuDialog = dialog
        menuContentView = menuView
        menuWindowParams = menuParams
        applyBlurState(isCrossWindowBlurEnabled())
        installBlurAvailabilityListener()
        try {
            dialog.show()
            menuWindow.attributes = menuParams
            menuView.enableDragWithinWindow(dialog, menuParams)
        } catch (error: RuntimeException) {
            removeBlurAvailabilityListener()
            dialog.dismiss()
            windowManager.removeViewImmediate(root)
            overlayView = null
            menuDialog = null
            menuContentView = null
            menuWindowParams = null
            throw error
        }

        menuView.post {
            if (!hasSavedPosition(suffix) && menuDialog === dialog) {
                val (width, height) = currentScreenSize(menuView.context)
                menuParams.x = (width - menuView.width) / 2
                menuParams.y = (height - menuView.height) / 2
                dialog.window?.attributes = menuParams
            }
        }
        dialog.window?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                val currentSuffix = orientationSuffix(v.context)
                saveInt("overlay_x_$currentSuffix", menuParams.x)
                saveInt("overlay_y_$currentSuffix", menuParams.y)
                Log.d("FloatingBackService", "Screen orientation: $currentSuffix, position: (${menuParams.x}, ${menuParams.y})")
            }
        })
    }

    private fun Context.hasSavedPosition(suf: String): Boolean {
        val prefs = getSharedPreferences(OVERLAY_MENU_PREFS, MODE_PRIVATE)
        return prefs.contains("overlay_x_$suf") && prefs.contains("overlay_y_$suf")
    }

    private fun currentScreenSize(ctx: Context): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = ctx.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            Point().also {
                (ctx.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(it)
            }
        }

    private fun orientationSuffix(ctx: Context) = if (ctx.isLandscape()) "land" else "port"

    private fun Context.isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun dismissOverlay(
        disableService: Boolean = false,
        deferMs: Long = 120L,
    ) {
        removeBlurAvailabilityListener()
        menuDialog?.let { dialog ->
            try {
                dialog.dismiss()
            } catch (_: Exception) {}
        }
        menuDialog = null
        menuContentView = null
        menuWindowParams = null
        overlayView?.let { v ->
            try {
                windowManager.removeViewImmediate(v)
            } catch (_: Exception) {}
        }
        overlayView = null
        lastDismissUptime = SystemClock.uptimeMillis()

        if (disableService) {
            Handler(Looper.getMainLooper())
                .postDelayed(
                    {
                        try {
                            disableSelf()
                        } catch (_: Throwable) {}
                    },
                    deferMs,
                ) // defer past the current gesture & frame(s)
        }
    }

    private fun isCrossWindowBlurEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled

    private fun installBlurAvailabilityListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val listener = Consumer<Boolean> { enabled ->
            if (menuDialog == null) return@Consumer
            val params = menuWindowParams ?: return@Consumer
            applyBlurState(enabled)
            runCatching { menuDialog?.window?.attributes = params }
        }
        blurEnabledListener = listener
        windowManager.addCrossWindowBlurEnabledListener(listener)
    }

    private fun applyBlurState(enabled: Boolean) {
        val dialog = menuDialog ?: return
        val view = menuContentView ?: return
        view.setBackgroundBlurEnabled(enabled)
        dialog.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(if (enabled) MENU_BACKGROUND_BLUR_RADIUS else 0)
            }
            window.setBackgroundDrawable(
                if (enabled) view.createBlurWindowBackground() else ColorDrawable(Color.TRANSPARENT)
            )
        }
    }

    private fun removeBlurAvailabilityListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurEnabledListener?.let { listener ->
                runCatching { windowManager.removeCrossWindowBlurEnabledListener(listener) }
            }
        }
        blurEnabledListener = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun View.enableDragWithinWindow(dialog: Dialog, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startLeft = 0
        var startTop = 0
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        // For clamping within the screen
        fun screenSize(): Point =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = context.getSystemService(WindowManager::class.java)
                val b = wm.currentWindowMetrics.bounds
                Point(b.width(), b.height())
            } else {
                @Suppress("DEPRECATION")
                Point().also {
                    (context.getSystemService(WINDOW_SERVICE) as WindowManager)
                        .defaultDisplay
                        .getSize(it)
                }
            }

        fun clamp(v: Int, min: Int, max: Int) = v.coerceIn(min, max)

        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startLeft = lp.x
                    startTop = lp.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()

                    val (sw, sh) = screenSize().let { it.x to it.y }
                    val vw = width.takeIf { it > 0 } ?: 1
                    val vh = height.takeIf { it > 0 } ?: 1

                    lp.x = clamp(startLeft + dx, 0, (sw - vw).coerceAtLeast(0))
                    lp.y = clamp(startTop + dy, 0, (sh - vh).coerceAtLeast(0))

                    dialog.window?.attributes = lp
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val moved =
                        (abs(e.rawX - downX) > touchSlop) || (abs(e.rawY - downY) > touchSlop)
                    if (!moved) performClick()
                    moved
                }

                else -> false
            }
        }
    }

    // Tiny helpers for persistence
    private fun Context.saveInt(key: String, value: Int) {
        getSharedPreferences(OVERLAY_MENU_PREFS, MODE_PRIVATE).edit { putInt(key, value) }
    }

    private fun Context.loadInt(key: String, def: Int) =
        getSharedPreferences(OVERLAY_MENU_PREFS, MODE_PRIVATE).getInt(key, def)

    private fun removeFloatingMenu() {
        dismissOverlay(disableService = false)
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
        val vibrator: Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
            // One-shot effect (API 26+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
            // Legacy (pre-26)
            else -> {
                @Suppress("DEPRECATION") vibrator.vibrate(50L)
            }
        }
    }

    private companion object {
        const val MENU_BACKGROUND_BLUR_RADIUS = 48
    }
}
