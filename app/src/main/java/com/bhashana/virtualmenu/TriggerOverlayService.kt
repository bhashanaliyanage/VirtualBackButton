package com.bhashana.virtualmenu

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class TriggerOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: View? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(1, overlayNotification())
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MenuContract.ACTION_STOP_OVERLAY -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bubble?.let { runCatching { wm.removeView(it) } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayNotification(): Notification {
        val channelId = "axio_overlay"
        val mgr = getSystemService(NotificationManager::class.java)
        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mgr.getNotificationChannel(channelId) == null
            } else {
                TODO("VERSION.SDK_INT < O")
            }
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Axio overlay",
                        NotificationManager.IMPORTANCE_MIN
                    )
                )
            }
        }
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Axio floating button is on")
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24; y = 200
        }

        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground_axio) // 48dp circular asset
            setOnClickListener {
                // Ask the accessibility service (if enabled) to show the menu
                Log.d("TriggerOverlayService", "show menu")
                sendBroadcast(Intent(MenuContract.ACTION_SHOW_MENU))
            }
        }

        // (Optional) minimal drag
        iv.setBackgroundColor(Color.RED)
        var lastX = 0
        var lastY = 0
        var dX = 0
        var dY = 0

        iv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = lp.x; lastY = lp.y; dX = e.rawX.toInt(); dY = e.rawY.toInt()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    lp.x = lastX + (e.rawX.toInt() - dX)
                    lp.y = lastY + (e.rawY.toInt() - dY)
                    wm.updateViewLayout(iv, lp); true
                }

                MotionEvent.ACTION_UP -> {
                    if (abs(e.rawX - dX) < 10 &&
                        abs(e.rawY - dY) < 10
                    ) {
                        Log.d("TriggerOverlayService", "show menu")
                        sendBroadcast(Intent(MenuContract.ACTION_SHOW_MENU).apply {
                            setPackage("com.bhashana.virtualmenu")
                        })
                    }
                    true
                }

                else -> false
            }
        }

        wm.addView(iv, lp)
        bubble = iv
    }
}

