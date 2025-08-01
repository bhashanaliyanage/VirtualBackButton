package com.bhashana.virtualmenu

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

class FloatingMenuService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isShuttingDown = false

    override fun onServiceConnected() {
        Log.d("FloatingBackService", "onServiceConnected()")

        if (!Settings.canDrawOverlays(this)) {
            Log.e("FloatingBackService", "Overlay permission not granted")
            // Optionally: launch an activity to request it.
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // showFloatingMenuNormal()
        showFloatingMenuCompose()

        vibrate()
    }

    private fun tintIcon(view: View, iconId: Int, attr: Int) {
        val iv = view.findViewById<ImageView>(iconId)
        val color = MaterialColors.getColor(iv, attr) // resolves ?attr from current theme
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(color))
    }

    private fun showFloatingMenuCompose() {
        if (overlayView != null || isShuttingDown) return

        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        val flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            x = 0
            y = 500
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Use a Material3 theme for proper attribute resolution
        val baseThemed = ContextThemeWrapper(this, R.style.Theme_VirtualBack)
        val dynamicCtx = DynamicColors.wrapContextIfAvailable(baseThemed)
        val inflater = LayoutInflater.from(dynamicCtx)
        val view = inflater.inflate(R.layout.floating_menu, null)

        // Helper to configure an item include
        fun bindItem(rootId: Int, iconRes: Int, labelText: String, onClick: () -> Unit) {
            val root = view.findViewById<View>(rootId)
            root.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
            root.findViewById<TextView>(R.id.label).text = labelText
            root.contentDescription = labelText
            root.setOnClickListener { onClick() }
        }

        bindItem(
            R.id.itemBack,
            R.drawable.ic_back,                // <- your drawable
            "Back"
        ) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            vibrate()
            disableSelf()
        }

        bindItem(
            R.id.itemHome,
            R.drawable.ic_home,
            "Home"
        ) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            vibrate()
            disableSelf()
        }

        bindItem(
            R.id.itemRecents,
            R.drawable.ic_notifications,
            "Recents"
        ) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            vibrate()
            disableSelf()
        }

        tintIcon(
            view.findViewById(R.id.itemBack), R.id.icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        tintIcon(
            view.findViewById(R.id.itemHome), R.id.icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        tintIcon(
            view.findViewById(R.id.itemRecents), R.id.icon,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        windowManager.addView(view, params)
        overlayView = view
    }

    @Composable
    private fun FloatingMenu(
        onBack: () -> Unit,
        onHome: () -> Unit,
        onRecents: () -> Unit,
        onClose: () -> Unit,
        onDrag: (dx: Int, dy: Int) -> Unit
    ) {
        // Simple drag handler that reports pixel deltas upward to the service
        val dragModifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
            }
        }

        Surface(
            modifier = dragModifier,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack) { Text("◀") }     // Back
                IconButton(onClick = onHome) { Text("⌂") }     // Home
                IconButton(onClick = onRecents) { Text("▢") }  // Recents
                IconButton(onClick = onClose) { Text("✕") }    // Close
            }
        }
    }

    private fun runActionAndClose(globalAction: Int) {
        if (isShuttingDown) return
        isShuttingDown = true

        vibrate()

        // 1) Perform the system action
        performGlobalAction(globalAction)

        // 2) Remove overlay immediately to give visual feedback
        removeFloatingMenu()

        // 3) Disable the service after a short delay so the action completes cleanly
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                disableSelf()
            } catch (t: Throwable) {
                Log.e("FloatingBackService", "disableSelf failed", t)
                // As a fallback, let the service die naturally.
            }
        }, 150) // 100–300ms works well; adjust if needed
    }

    private fun showFloatingMenuNormal() {
        // Avoid duplicates if service reconnects
        if (overlayView != null) return

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setPadding(30, 30, 30, 30)

            addView(createButton("Back") { runActionAndClose(GLOBAL_ACTION_BACK) })
            addView(createButton("Notifications") { runActionAndClose(GLOBAL_ACTION_NOTIFICATIONS) })
            addView(createButton("Home") { runActionAndClose(GLOBAL_ACTION_HOME) })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayView = layout
        windowManager.addView(layout, params)
        makeDraggable(layout, params)
        Log.d("FloatingBackService", "Overlay added")
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var lastX = 0f
        var lastY = 0f
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX; lastY = e.rawY; true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - lastX).toInt()
                    val dy = (e.rawY - lastY).toInt()
                    params.x += dx; params.y += dy
                    windowManager.updateViewLayout(v, params)
                    lastX = e.rawX; lastY = e.rawY
                    true
                }

                else -> false
            }
        }
    }

    private fun removeFloatingMenu() {
        /*overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            overlayView = null
            Log.d("FloatingBackService", "Overlay removed")
        }*/
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // ignore
        } finally {
            overlayView = null
        }
    }

    private fun createButton(textLabel: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = textLabel
            setOnClickListener { onClick() }
        }


    override fun onUnbind(intent: Intent?): Boolean {
        removeFloatingMenu()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeFloatingMenu()
        super.onDestroy()
    }

    private fun vibrate() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            v.vibrate(50)
        }
    }
}