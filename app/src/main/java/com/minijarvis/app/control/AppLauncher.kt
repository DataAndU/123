package com.minijarvis.app.control

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Resolves a spoken app name to an installed, launchable app and starts it.
 * Uses the `<queries>` declaration in the manifest (Android 11+ package
 * visibility) rather than the QUERY_ALL_PACKAGES permission, since this app
 * only needs to see launchable apps, not every installed package.
 */
class AppLauncher(private val context: Context) {

    data class LaunchableApp(val label: String, val packageName: String)

    fun listLaunchableApps(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved
            .map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Best-effort fuzzy match: exact label, then label-contains-spoken, then spoken-contains-label. */
    fun launchByName(spokenName: String): Boolean {
        val name = spokenName.trim()
        if (name.isEmpty()) return false
        val apps = listLaunchableApps()
        val target = apps.firstOrNull { it.label.equals(name, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.contains(name, ignoreCase = true) }
            ?: apps.firstOrNull { name.contains(it.label, ignoreCase = true) }
            ?: return false
        val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }
}
