package com.minijarvis.app.system

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.minijarvis.app.data.AppUsageEntity
import com.minijarvis.app.util.TimeUtils

/**
 * Reads on-device app usage stats (Settings > Special access > Usage access
 * must be granted first — checked via [PermissionManager.hasUsageAccess]).
 * All aggregation happens locally; nothing is ever transmitted.
 */
class AppUsageHelper(private val context: Context) {

    fun collectForToday(): List<AppUsageEntity> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startMillis = TimeUtils.startOfDayMillis()
        val endMillis = TimeUtils.nowMillis()
        val epochDay = TimeUtils.toEpochDay()

        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startMillis, endMillis)
        val pm = context.packageManager

        return statsMap.values
            .filter { it.totalTimeInForeground > 0 }
            .mapNotNull { stat ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                AppUsageEntity(
                    packageName = stat.packageName,
                    appLabel = label,
                    totalTimeMillis = stat.totalTimeInForeground,
                    epochDay = epochDay
                )
            }
            .sortedByDescending { it.totalTimeMillis }
    }
}
