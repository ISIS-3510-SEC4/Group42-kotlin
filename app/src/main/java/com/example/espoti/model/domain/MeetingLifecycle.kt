package com.example.espoti.model.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================================================================
// MEETING LIFECYCLE
// ----------------------------------------------------------------------------
// Pure time rules for a meeting, shared by the repository (which writes the
// status) and the UI (which shows the effective one before anybody wrote it).
// - The day is the DatePicker's UTC midnight (MeetingEvent.date); the time is
//   "HH:mm" (older meetings may hold free text like "6:00 pm").
// - A confirmed meeting is over MEETING_DURATION after it starts, or at the
//   end of its day when it has no time. Then it becomes PREVIOUS, which also
//   feeds the backend's recommendation history.
// ============================================================================
object MeetingLifecycle {

    /** Assumed length of a meeting. */
    const val MEETING_DURATION_MILLIS = 3 * 60 * 60 * 1000L

    private val TIME_PATTERNS = listOf("H:mm", "h:mm a", "h a", "ha", "h:mma")
        .map { DateTimeFormatter.ofPattern(it, Locale.US) }

    /** "18:00", "6:00 pm", "6 PM"... or null when the text isn't a time. */
    fun parseMeetingTime(text: String): LocalTime? {
        val normalized = text.trim().uppercase(Locale.US)
        if (normalized.isEmpty()) return null
        return TIME_PATTERNS.firstNotNullOfOrNull { pattern ->
            runCatching { LocalTime.parse(normalized, pattern) }.getOrNull()
        }
    }

    /** Start in epoch millis (device zone): day + time, or the day's midnight without a time. */
    fun startsAt(dateMillis: Long, time: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (dateMillis <= 0) return null
        val day = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val at = parseMeetingTime(time) ?: LocalTime.MIDNIGHT
        return day.atTime(at).atZone(zone).toInstant().toEpochMilli()
    }

    /** When the meeting is over; null without a date. */
    fun endsAt(meeting: MeetingEvent, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (meeting.date <= 0) return null
        val day = Instant.ofEpochMilli(meeting.date).atZone(ZoneOffset.UTC).toLocalDate()
        val time = parseMeetingTime(meeting.time)
            ?: return day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return day.atTime(time).atZone(zone).toInstant().toEpochMilli() + MEETING_DURATION_MILLIS
    }

    fun isFinished(meeting: MeetingEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val end = endsAt(meeting, zone) ?: return false
        return now >= end
    }

    /**
     * Status to show: an expired VOTING meeting is already decided (UPCOMING),
     * and a decided meeting that is over is PREVIOUS.
     */
    fun effectiveStatus(meeting: MeetingEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): StatusMeeting {
        val status = VotingRules.effectiveStatus(meeting, now)
        return if (status == StatusMeeting.UPCOMING && isFinished(meeting, now, zone)) {
            StatusMeeting.PREVIOUS
        } else {
            status
        }
    }
}
