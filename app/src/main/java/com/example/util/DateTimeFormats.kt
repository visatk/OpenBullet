package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe date and time formatting helpers compatible with API 24+.
 */
object DateTimeFormats {
    private val clockFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("HH:mm:ss", Locale.US)
    }

    private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    fun formatClockNow(): String = clockFormat.get()?.format(Date()) ?: "00:00:00"

    fun formatDateNow(): String = dateFormat.get()?.format(Date()) ?: "1970-01-01"
}
