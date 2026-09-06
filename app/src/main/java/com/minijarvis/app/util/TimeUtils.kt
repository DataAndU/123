package com.minijarvis.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Centralizes local-time-zone date math so every module reports "day/week/month" consistently. */
object TimeUtils {

    val zone: ZoneId = ZoneId.systemDefault()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun startOfDayMillis(atMillis: Long = nowMillis()): Long =
        Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMillis(atMillis: Long = nowMillis()): Long =
        startOfDayMillis(atMillis) + DAY_MILLIS - 1

    fun startOfWeekMillis(atMillis: Long = nowMillis()): Long {
        val date = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun endOfWeekMillis(atMillis: Long = nowMillis()): Long =
        startOfWeekMillis(atMillis) + 7 * DAY_MILLIS - 1

    fun startOfMonthMillis(atMillis: Long = nowMillis()): Long {
        val date = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().withDayOfMonth(1)
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun endOfMonthMillis(atMillis: Long = nowMillis()): Long {
        val date = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        val lastDay = date.withDayOfMonth(date.lengthOfMonth())
        return lastDay.atStartOfDay(zone).toInstant().toEpochMilli() + DAY_MILLIS - 1
    }

    fun toEpochDay(atMillis: Long = nowMillis()): Long =
        Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().toEpochDay()

    fun epochDayToMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DATE_FORMAT)

    fun formatDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DATETIME_FORMAT)

    fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(TIME_FORMAT)

    /**
     * Best-effort natural-language date phrase parser used by smart search
     * and the assistant (e.g. "today", "yesterday", "this week", "last month").
     * Returns a [Pair] of (startMillis, endMillis), or null if no phrase matched.
     */
    fun parseRelativeDatePhrase(text: String): Pair<Long, Long>? {
        val lower = text.lowercase()
        val now = nowMillis()
        return when {
            "today" in lower -> startOfDayMillis(now) to endOfDayMillis(now)
            "yesterday" in lower -> {
                val yesterday = now - DAY_MILLIS
                startOfDayMillis(yesterday) to endOfDayMillis(yesterday)
            }
            "this week" in lower -> startOfWeekMillis(now) to endOfWeekMillis(now)
            "last week" in lower -> {
                val lastWeek = now - 7 * DAY_MILLIS
                startOfWeekMillis(lastWeek) to endOfWeekMillis(lastWeek)
            }
            "this month" in lower -> startOfMonthMillis(now) to endOfMonthMillis(now)
            "last month" in lower -> {
                val lastMonthAnchor = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusMonths(1)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                startOfMonthMillis(lastMonthAnchor) to endOfMonthMillis(lastMonthAnchor)
            }
            else -> null
        }
    }

    const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
}
