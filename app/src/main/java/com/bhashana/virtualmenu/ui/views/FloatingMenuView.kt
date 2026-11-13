package com.bhashana.virtualmenu.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import com.bhashana.virtualmenu.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable

class FloatingMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    enum class Action { BACK, HOME, PANEL, LOCK, CAPTURE }

    private var onAction: ((Action) -> Unit)? = null

    private val menuCornerRadiusPx: Float
        get() = MENU_CORNER_RADIUS_DP * resources.displayMetrics.density

    init {
        inflate(context, R.layout.floating_menu, this)
        val root = findViewById<View>(R.id.floatingMenuRoot)
        root?.background = null
        background = null
        isClickable = true
        isFocusable = true
        applyMaterialBackground(alpha = FALLBACK_BACKGROUND_ALPHA)
        tintAll()
        bindAll()
    }

    fun setBackgroundBlurEnabled(enabled: Boolean) {
        if (enabled) {
            background = null
        } else {
            applyMaterialBackground(alpha = FALLBACK_BACKGROUND_ALPHA)
        }
    }

    fun createBlurWindowBackground(): GradientDrawable {
        val color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
        return GradientDrawable().apply {
            setColor(ColorUtils.setAlphaComponent(color, (BLURRED_BACKGROUND_ALPHA * 255).toInt()))
            cornerRadius = menuCornerRadiusPx
        }
    }

    fun setOnActionListener(block: (Action) -> Unit) {
        onAction = block
    }

    private fun applyMaterialBackground(alpha: Float) {
        val c = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
        val shape = MaterialShapeDrawable().apply {
            initializeElevationOverlay(context)
            setCornerSize(menuCornerRadiusPx)
            fillColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(c, (alpha * 255).toInt())
            )
            elevation = ViewCompat.getElevation(this@FloatingMenuView)
        }
        background = shape
    }

    private fun tintAll() {
        tintIcon(this, R.id.logo, com.google.android.material.R.attr.colorOnSurface)

        val iconAttr = com.google.android.material.R.attr.colorOnSurfaceVariant
        val targets = intArrayOf(
            R.id.itemBack, R.id.itemHome, R.id.itemRecents, R.id.itemLock, R.id.itemSS
        )
        for (rootId in targets) tintIcon(findViewById(rootId), R.id.icon, iconAttr)
    }

    private fun bindAll() {
        bind(R.id.itemBack, Action.BACK, R.drawable.ic_back, "Back")
        bind(R.id.itemHome, Action.HOME, R.drawable.ic_home, "Home")
        bind(R.id.itemRecents, Action.PANEL, R.drawable.ic_notifications, "Panel")
        bind(R.id.itemLock, Action.LOCK, R.drawable.ic_lock, "Lock")
        bind(R.id.itemSS, Action.CAPTURE, R.drawable.ic_ss, "Capture")
    }

    private fun bind(rootId: Int, action: Action, iconRes: Int, labelText: String) {
        val root = findViewById<View>(rootId)
        root.findViewById<TextView>(R.id.label).text = labelText
        root.setOnClickListener { onAction?.invoke(action) }
        root.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
    }

    private fun tintIcon(view: View, iconId: Int, attr: Int) {
        val iv = view.findViewById<ImageView>(iconId)
        val color = MaterialColors.getColor(iv, attr)
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(color))
    }

    private companion object {
        // Shared by the native blur window and its fallback surface; measured in dp.
        const val MENU_CORNER_RADIUS_DP = 12f
        const val FALLBACK_BACKGROUND_ALPHA = 0.9f
        const val BLURRED_BACKGROUND_ALPHA = 0.62f
    }
}
