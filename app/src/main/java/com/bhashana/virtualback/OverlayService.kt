package com.bhashana.virtualback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(1, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingMenu()
    }

    private fun showFloatingMenu() {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("OverlayService", "Overlay permission not granted")
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setPadding(30, 30, 30, 30)

            addView(createButton("Back", 1))
            addView(createButton("Notifications", 2))
            addView(createButton("Home", 3))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        overlayView = layout
        windowManager.addView(overlayView, params)
    }

    private fun createButton(label: String, actionId: Int): Button {
        return Button(this).apply {
            text = label
            setOnClickListener {
                val action = when (actionId) {
                    1 -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                    2 -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                    3 -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                    else -> -1
                }
                if (action > 0) {
                    val intent = Intent("ACCESSIBILITY_GLOBAL_ACTION")
                    intent.putExtra("action", action)
                    sendBroadcast(intent)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "virtual_assist_channel",
                "Virtual Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "virtual_assist_channel")
            .setContentTitle("Virtual Assistant Running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}