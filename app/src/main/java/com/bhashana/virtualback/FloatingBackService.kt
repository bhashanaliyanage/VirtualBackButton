package com.bhashana.virtualback

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
        TODO("Not yet implemented")
    }

    override fun onInterrupt() {
        TODO("Not yet implemented")
    }
}