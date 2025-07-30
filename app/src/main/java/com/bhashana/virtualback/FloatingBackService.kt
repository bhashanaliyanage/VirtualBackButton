package com.bhashana.virtualback

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import androidx.annotation.RequiresPermission

class FloatingBackService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: ImageView
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isKeyboardVisible = false
    private val keyboardCheckHandler = Handler(Looper.getMainLooper())
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val keyboardBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val keyboardVisible = intent?.getBooleanExtra("keyboardVisible", false) ?: false
            layoutParams.y = if (keyboardVisible) 200 else 500
            windowManager.updateViewLayout(floatingButton, layoutParams)
        }
    }

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButton()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                keyboardBroadcastReceiver,
                IntentFilter("FLOATING_BUTTON_KEYBOARD_VISIBILITY"),
                RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun showFloatingButton() {
        val layoutParams = WindowManager.LayoutParams(
            120, 120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 500 // starting x position
            y = 500 // starting y position
        }

        floatingButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingButton, layoutParams)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        // Treat as a click if it wasn't dragged
                        if (Math.abs(event.rawX - initialTouchX) < 10 &&
                            Math.abs(event.rawY - initialTouchY) < 10) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            vibrate()
                        }
                        true
                    }

                    else -> false
                }
            }
        }

        windowManager.addView(floatingButton, layoutParams)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.EFFECT_TICK))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("FloatingBackService", "Event received: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.d("FloatingBackService", "Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(keyboardBroadcastReceiver)
    }
}