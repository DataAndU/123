package com.gemmaassistant.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Centralizes local-time-zone timestamp formatting for chat and activity log display. */
object TimeUtils {

    val zone: ZoneId = ZoneId.systemDefault()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun formatDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DATETIME_FORMAT)

    fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(TIME_FORMAT)

    const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    private val DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
}
