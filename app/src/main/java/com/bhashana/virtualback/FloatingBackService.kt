package com.bhashana.virtualback

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
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
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val keyboardBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::layoutParams.isInitialized || !::floatingButton.isInitialized) {
                Log.w("FloatingBackService", "Broadcast received before layoutParams/floatingButton initialized")
                return
            } else {
                Log.w("FloatingBackService", "Broadcast received!")
            }

            val keyboardVisible = intent?.getBooleanExtra("keyboardVisible", false) ?: false
            val keyboardHeight = intent?.getIntExtra("keyboardHeight", 0) ?: 0
            val displayMetrics = Resources.getSystem().displayMetrics

            Log.d("FloatingBackService", "Keyboard Visible: $keyboardVisible")
            Log.d("FloatingBackService", "Keyboard Height: $keyboardHeight")

            layoutParams.y = if (keyboardVisible) {
                displayMetrics.heightPixels - keyboardHeight - floatingButton.height - 50
            } else {
                displayMetrics.heightPixels / 2
            }

            windowManager.updateViewLayout(floatingButton, layoutParams)
        }
    }

    override fun onServiceConnected() {
        Log.d("FloatingBackService", "onServiceConnected()")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButton()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                keyboardBroadcastReceiver,
                IntentFilter("FLOATING_BUTTON_KEYBOARD_VISIBILITY"),
                RECEIVER_NOT_EXPORTED
            )
            Log.d("FloatingBackService", "onServiceConnected() called, registering receiver.")
        }

        /*val testIntent = Intent("FLOATING_BUTTON_KEYBOARD_VISIBILITY")
        testIntent.setPackage(packageName) // 👈 this tells Android to send to your app only
        testIntent.putExtra("keyboardVisible", false)
        testIntent.putExtra("keyboardHeight", 0)
        sendBroadcast(testIntent)*/
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        layoutParams = WindowManager.LayoutParams(
            120, 120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 500
        layoutParams.y = 500

        floatingButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val wlp = layoutParams as WindowManager.LayoutParams
                        initialX = wlp.x
                        initialY = wlp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val wlp = layoutParams as WindowManager.LayoutParams
                        wlp.x = initialX + (event.rawX - initialTouchX).toInt()
                        wlp.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingButton, layoutParams)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(event.rawX - initialTouchX) < 10 &&
                            Math.abs(event.rawY - initialTouchY) < 10
                        ) {
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
        Log.v("FloatingBackService", "Event received: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.v("FloatingBackService", "Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(keyboardBroadcastReceiver)
    }
}