package com.bhashana.virtualmenu.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object A11yServiceHelper {

fun <T> isEnabled(context: Context, serviceClass: Class<T>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    // Primary: check enabled service list
    val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
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

    @Suppress("DEPRECATION")
    fun <T> openSettings(context: Context, serviceClass: Class<T>) {
        val comp = ComponentName(context, serviceClass)

        // Try Android 12+ details page using literal strings (works without new constants)
        val tried = runCatching {
            val i = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.provider.extra.ACCESSIBILITY_COMPONENT_NAME", comp.flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }.isSuccess
        if (tried) return

        // Android 14+ may require allowing "Restricted settings" first for sideloaded apps
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
}