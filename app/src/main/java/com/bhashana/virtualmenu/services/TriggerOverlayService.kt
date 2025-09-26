package com.bhashana.virtualmenu.services

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.bhashana.virtualmenu.MenuContract
import com.bhashana.virtualmenu.R
import com.google.android.material.color.MaterialColors
import kotlin.math.abs

class TriggerOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: View? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(1, overlayNotification())

        if (!Settings.canDrawOverlays(this)) {
            Log.e("FloatingBackService", "Overlay permission not granted")
            // Optionally: launch an activity to request it.
            return
        }

        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MenuContract.ACTION_STOP_OVERLAY -> {
                stopSelf()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, overlayNotification())
        }

        // Rest of your service logic
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bubble?.let { runCatching { wm.removeView(it) } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // 1) Is our AccessibilityService enabled?
    fun <T> isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<T>
    ): Boolean {
        val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        // Quick, reliable check via the enabled service list:
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val si = info.resolveInfo.serviceInfo
                si.packageName == context.packageName && si.name == serviceClass.name
            }
        if (enabled) return true

        // Fallback: read the colon-separated list in Secure settings
        val flat = ComponentName(context, serviceClass).flattenToString()
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return setting.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    // 2) Send the user to the right Settings screen to enable it
    @SuppressLint("InlinedApi") // we’re inlining post-31 constants safely
    fun <T> openAccessibilitySettingsFor(context: Context, serviceClass: Class<T>) {
        val comp = ComponentName(context, serviceClass)

        // Try the per-service details page (Android 12+). Use literal action & extra.
        val tried = runCatching {
            val i = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.provider.extra.ACCESSIBILITY_COMPONENT_NAME", comp.flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }.isSuccess
        if (tried) return

        // Android 14+ “Restricted settings” gate (sideloaded apps)
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        }

        // Generic fallback
        runCatching {
            val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    private fun overlayNotification(): Notification {
        val channelId = "axio_overlay_v2"           // if you change importance, consider a NEW ID
        val mgr = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mgr.getNotificationChannel(channelId) == null
        ) {
            val channel = NotificationChannel(
                channelId,
                "Axio overlay",
                NotificationManager.IMPORTANCE_HIGH   // ⬅️ use LOW, not MIN
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                description = "Shows the ongoing overlay bubble status"
            }
            mgr.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground_axio)
            .setContentTitle("Axio floating button is on")
            .setContentText("Tap to open Axio")
            .setContentIntent(contentIntent)
            .setOngoing(true) // <-- makes it non-dismissible
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW) // affects pre-O only
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        fun Int.dp() = (this * resources.displayMetrics.density).toInt()

        val size = 48.dp()            // a little bigger than before

        val lp = WindowManager.LayoutParams(
            size,
            size,
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

        val themed = ContextThemeWrapper(this, R.style.Theme_VirtualBack)

        val iv = ImageView(themed).apply {
            // Keep your icon; it will be centered inside the circular background
            setImageResource(R.drawable.ic_launcher_foreground_axio)
            scaleType = ImageView.ScaleType.FIT_CENTER
            // setPadding(padding, padding, padding, padding)

            // Ask MaterialColors to resolve surface color for this view
            val surfaceColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurface,
                Color.DKGRAY // fallback
            )

            val transparentSurface = (surfaceColor and 0x00FFFFFF) or (0x99 shl 24)

            // Circular, themed background
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(transparentSurface)
            }

            elevation = 6f
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true

            setOnClickListener {
                Log.d("TriggerOverlayService", "show menu")
                sendBroadcast(Intent(MenuContract.ACTION_SHOW_MENU))
            }
        }

        // (Optional) minimal drag
        // iv.setBackgroundColor(Color.RED)
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
                        /*Log.d("TriggerOverlayService", "show menu")
                        sendBroadcast(Intent(MenuContract.ACTION_SHOW_MENU).apply {
                            setPackage("com.bhashana.virtualmenu")
                        })*/
                        if (isAccessibilityServiceEnabled(this, FloatingMenuService::class.java)) {
                            sendBroadcast(Intent(MenuContract.ACTION_SHOW_MENU).setPackage("com.bhashana.virtualmenu"))
                        } else {
                            openAccessibilitySettingsFor(this, FloatingMenuService::class.java)
                            Toast.makeText(this, "Please enable accessibility service", Toast.LENGTH_SHORT).show()
                        }
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