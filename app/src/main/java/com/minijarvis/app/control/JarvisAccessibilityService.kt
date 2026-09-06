package com.minijarvis.app.control

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The "whole phone" half of Mini JARVIS: with this service enabled (a
 * manual, user-granted toggle in system Settings — Android does not allow
 * an app to silently enable its own Accessibility Service), the assistant
 * can read what's on screen, tap things, and drive system-wide navigation
 * (home/back/recents/lock/screenshot), on top of the direct APIs in
 * [SystemControlManager] and [PhoneActionsManager].
 *
 * Every action here is a real Accessibility API call — there is no bundled
 * automation script, macro engine, or anything that reaches off-device.
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    // ---------------- Global navigation ----------------

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotificationShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun takeScreenshot(): Boolean = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    // ---------------- Screen reading & tapping ----------------

    /** Flattens all visible text on the current screen into one string, for "what's on my screen" style queries. */
    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        collectText(root, builder)
        return builder.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, builder: StringBuilder) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) builder.append(text).append(". ")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, builder)
        }
    }

    /** Finds the first node whose text/description contains [target] and clicks it (or its nearest clickable ancestor). */
    fun clickByText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, target) ?: return false
        return clickNodeOrAncestor(node)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val label = node.text?.toString() ?: node.contentDescription?.toString()
        if (label != null && label.contains(target, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodeByText(child, target)?.let { return it }
        }
        return null
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = current.parent
        }
        return false
    }

    /**
     * Best-effort toggle for a Switch-like control (used for the WiFi/Bluetooth
     * quick-settings panel workaround). Reliability depends on the OEM's
     * Settings UI, since Android exposes no direct silent-toggle API to
     * regular apps for these radios anymore.
     */
    fun toggleSwitchLike(labelHints: List<String>, desiredOn: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findSwitchNode(root, labelHints) ?: return false
        if (node.isChecked == desiredOn) return true
        return clickNodeOrAncestor(node)
    }

    private fun findSwitchNode(node: AccessibilityNodeInfo, labelHints: List<String>): AccessibilityNodeInfo? {
        val className = node.className?.toString().orEmpty()
        if (className.contains("Switch", ignoreCase = true) || className.contains("ToggleButton", ignoreCase = true)) {
            val label = (node.text?.toString() ?: node.contentDescription?.toString()).orEmpty()
            if (labelHints.isEmpty() || labelHints.any { label.contains(it, ignoreCase = true) }) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findSwitchNode(child, labelHints)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile private var instance: JarvisAccessibilityService? = null

        fun current(): JarvisAccessibilityService? = instance

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val expected = ComponentName(context, JarvisAccessibilityService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun openAccessibilitySettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
