package com.bhashana.virtualback

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout

class FloatingBackService : AccessibilityService() {

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
        showFloatingMenu()

        vibrate()
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

    private fun showFloatingMenu() {
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
        var lastX = 0f;
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
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            overlayView = null
            Log.d("FloatingBackService", "Overlay removed")
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

    private fun OverlayService.sendGlobalActionImplicit(a: Int) {
        val i =
            Intent("ACCESSIBILITY_GLOBAL_ACTION")
                .setPackage(packageName)
                .putExtra("action", a)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        applicationContext.sendBroadcast(i)
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