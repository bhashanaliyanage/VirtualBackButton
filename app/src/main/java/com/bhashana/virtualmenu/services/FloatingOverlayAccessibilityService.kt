package com.bhashana.virtualmenu.services

/** Independent service switch for the floating overlay, without a shortcut assignment. */
class FloatingOverlayAccessibilityService : FloatingMenuService() {
    override val usesAccessibilityButton: Boolean = false
}
