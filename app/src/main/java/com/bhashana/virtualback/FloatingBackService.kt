package com.bhashana.virtualback

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FloatingBackService : AccessibilityService() {

private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("FloatingBackService", "Broadcast Received")
        val action = intent?.getIntExtra("action", -1)
        if (action != null && action > 0) {
            performGlobalAction(action)
        }
    }
}

    override fun onServiceConnected() {
        Log.d("FloatingBackService", "onServiceConnected()")

        // Register broadcast to listen for overlay service commands
        val filter = IntentFilter("ACCESSIBILITY_GLOBAL_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        }

        vibrate()

        // Start the persistent overlay UI
        val overlayIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }

        // disableSelf() // Relay done
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun vibrate() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.EFFECT_TICK))
        } else {
            v.vibrate(50)
        }
    }
}