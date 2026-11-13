package com.bhashana.virtualmenu.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.bhashana.virtualmenu.BUTTON_TYPE_PREFS
import com.bhashana.virtualmenu.KEY_TRIGGER_MODE
import com.bhashana.virtualmenu.services.FloatingMenuService
import com.bhashana.virtualmenu.services.FloatingOverlayAccessibilityService

object A11yServiceHelper {

    fun isSelectedServiceEnabled(context: Context): Boolean {
        val mode = context.getSharedPreferences(BUTTON_TYPE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRIGGER_MODE, "accessibility")
        return if (mode == "overlay") {
            isEnabled(context, FloatingOverlayAccessibilityService::class.java)
        } else {
            isEnabled(context, FloatingMenuService::class.java)
        }
    }

    fun <T> isEnabled(context: Context, serviceClass: Class<T>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        // Primary: check enabled service list
        val enabled =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val si = info.resolveInfo.serviceInfo
                    si.packageName == context.packageName && si.name == serviceClass.name
                }
        if (enabled) return true

        // Fallback: Secure setting list
        val flat = ComponentName(context, serviceClass).flattenToString()
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return setting.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
