package com.bhashana.virtualback

import android.app.Activity
import android.graphics.Rect
import android.view.View

class KeyboardVisibilityDetector(
    private val activity: Activity,
    private val onKeyboardVisibilityChanged: (Boolean) -> Unit
) {
    private var isKeyboardVisible = false

    fun start() {
        val rootView = activity.findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            val keyboardNowVisible = keypadHeight > screenHeight * 0.15
            if (keyboardNowVisible != isKeyboardVisible) {
                isKeyboardVisible = keyboardNowVisible
                onKeyboardVisibilityChanged(isKeyboardVisible)
            }
        }
    }
}