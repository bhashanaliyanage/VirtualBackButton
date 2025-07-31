package com.bhashana.virtualback

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresPermission

class FloatingBackService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.d("FloatingBackService", "onServiceConnected()")
        performGlobalAction(GLOBAL_ACTION_BACK)
        vibrate()
        disableSelf()
    }


    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            // fallback for older devices
            v.vibrate(50)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("FloatingBackService", "Accessibility event received.")
    }

    override fun onInterrupt() {
        Log.v("FloatingBackService", "Service interrupted.")
    }

}