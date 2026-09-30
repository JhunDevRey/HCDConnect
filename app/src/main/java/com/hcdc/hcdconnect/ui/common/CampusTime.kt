package com.hcdc.hcdconnect.ui.common

import java.text.DateFormat
import java.time.ZoneId
import java.util.TimeZone

/**
 * Event times are always shown and entered in campus time (Davao, Philippines), whatever
 * time zone the phone is set to, so everyone sees the same time for an event.
 */
object CampusTime {

    val ZONE: ZoneId = ZoneId.of("Asia/Manila")

    /** A [DateFormat] of the given styles that formats in campus time. */
    fun dateTimeFormat(dateStyle: Int, timeStyle: Int): DateFormat =
        DateFormat.getDateTimeInstance(dateStyle, timeStyle).apply { timeZone = TimeZone.getTimeZone(ZONE) }

    fun timeFormat(style: Int): DateFormat =
        DateFormat.getTimeInstance(style).apply { timeZone = TimeZone.getTimeZone(ZONE) }
}
