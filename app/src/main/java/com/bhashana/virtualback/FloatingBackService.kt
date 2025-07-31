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
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri

class FloatingBackService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: ImageView
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var originalY: Int? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val keyboardBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::layoutParams.isInitialized || !::floatingButton.isInitialized) {
                Log.w(
                    "FloatingBackService",
                    "Broadcast received before layoutParams/floatingButton initialized"
                )
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

        if (Settings.canDrawOverlays(this)) {
            showFloatingButton()
        } else {
            Log.e("FloatingBackService", "SYSTEM_ALERT_WINDOW not granted!")
            Toast.makeText(
                this,
                "Overlay permission not granted. Enable it in settings.",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = "package:$packageName".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return // ✅ exit early to avoid crash
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                keyboardBroadcastReceiver,
                IntentFilter("FLOATING_BUTTON_KEYBOARD_VISIBILITY"),
                RECEIVER_NOT_EXPORTED
            )
            Log.d("FloatingBackService", "onServiceConnected() called, registering receiver.")
        }
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
        Log.d("FloatingBackService", "Dummy")
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                detectKeyboardLikeEvent()
            }
        }
    }

    private fun detectKeyboardLikeEvent() {
        val rootNode = rootInActiveWindow ?: return

        // Look for input fields in the window
        val hasInputField = hasEditableTextField(rootNode)
        Log.d("FloatingBackService", "Editable field focused: $hasInputField")

        val screenHeight = getScreenHeight()
        val fakeKeyboardHeight = (screenHeight * 0.4).toInt()
        val keyboardTopY = screenHeight - fakeKeyboardHeight
        val buttonBottomY = layoutParams.y + floatingButton.height

        val isOverlappingKeyboard = buttonBottomY >= keyboardTopY

        if (hasInputField && isOverlappingKeyboard) {
            adjustFloatingButton(visible = true, height = fakeKeyboardHeight)
        } else if (!hasInputField && originalY != null) {
            adjustFloatingButton(visible = false, height = 0)
        }
    }

    private fun adjustFloatingButton(visible: Boolean, height: Int) {
        if (!::layoutParams.isInitialized || !::floatingButton.isInitialized) return

        val displayMetrics = Resources.getSystem().displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val floatingButtonHeight = floatingButton.height

        val newY = if (visible) {
            // Save the original position before moving up
            if (originalY == null) {
                originalY = layoutParams.y
                Log.d("FloatingBackService", "Saving original Y: $originalY")
            }

            // Move ABOVE the keyboard
            (screenHeight - height) - floatingButtonHeight - 50
        } else {
            // Move back to original position if known
            val restoreY = originalY ?: (screenHeight / 2)
            Log.d("FloatingBackService", "Restoring original Y: $restoreY")
            originalY = null // clear it
            restoreY
        }

        layoutParams.y = newY
        windowManager.updateViewLayout(floatingButton, layoutParams)
        Log.d("FloatingBackService", "Floating button adjusted. Visible: $visible, Y: $newY")
    }

    private fun getScreenHeight(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        val heightPixels = displayMetrics.heightPixels
        return heightPixels
    }

    private fun hasEditableTextField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (node.className?.contains("EditText") == true ||
            node.isFocused && node.isEditable
        ) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (hasEditableTextField(node.getChild(i))) return true
        }
        return false
    }

    override fun onInterrupt() {
        Log.v("FloatingBackService", "Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(keyboardBroadcastReceiver)
    }
}