package com.bhashana.virtualback

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
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

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButton()
    }

    private fun showFloatingButton() {
        floatingButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    vibrate()
                }
                true
            }
        }

        val params = WindowManager.LayoutParams(
            120, 120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        windowManager.addView(floatingButton, params)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(50)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("FloatingBackService", "Event received: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.d("FloatingBackService", "Service interrupted.")
    }
}