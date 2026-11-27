package com.example.espoti.model.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

    /**
     * "18:00", "6:00 pm", "6 PM"... or null when the text isn't a time.
     * Accepts exactly what the old DateTimeFormatter list accepted
     * ("H:mm", "h:mm a", "h a", "ha", "h:mma"), but parses by hand: no
     * formatter, no exception per failed pattern, no uppercase copy. It runs
     * 2-3 times per meeting on every refresh (status, start, end), so this is
     * hot on the Meetings and Home screens (Sprint 4 micro-optimization).
     */
    fun parseMeetingTime(text: String): LocalTime? {
        var start = 0
        var end = text.length
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start == end) return null

        // Hour: 1..9 digits ("H"/"h" take any width, leading zeros included).
        var i = start
        var hour = 0
        while (i < end && text[i] in '0'..'9') {
            if (i - start == MAX_HOUR_DIGITS) return null
            hour = hour * 10 + (text[i] - '0')
            i++
        }
        if (i == start) return null

        // Optional ":mm" (exactly two digits).
        var minute = 0
        val hasMinutes = i < end && text[i] == ':'
        if (hasMinutes) {
            if (end - i < 3 || text[i + 1] !in '0'..'9' || text[i + 2] !in '0'..'9') return null
            minute = (text[i + 1] - '0') * 10 + (text[i + 2] - '0')
            if (minute > 59) return null
            i += 3
        }

        // "H:mm": 24-hour clock, nothing after the minutes.
        if (i == end) {
            if (!hasMinutes) return null
            return when {
                hour < 24 -> LocalTime.of(hour, minute)
                hour == 24 && minute == 0 -> LocalTime.MIDNIGHT // SMART resolver: 24:00
                else -> null
            }
        }

        // "h:mm a" / "h:mma" / "h a" / "ha": optional single space + AM/PM.
        if (text[i] == ' ') i++
        if (end - i != 2 || !text[i + 1].equals('M', ignoreCase = true)) return null
        val pm = when {
            text[i].equals('P', ignoreCase = true) -> true
            text[i].equals('A', ignoreCase = true) -> false
            else -> return null
        }
        if (hour > 12) return null
        val hourOfAmPm = if (hour == 12) 0 else hour // 12 AM = 00, 12 PM = 12 (0 also allowed)
        return LocalTime.of(if (pm) hourOfAmPm + 12 else hourOfAmPm, minute)
    }

    private const val MAX_HOUR_DIGITS = 9
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** The DatePicker's UTC-midnight millis as a date, without a ZonedDateTime. */
    private fun utcDay(dateMillis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(dateMillis, DAY_MILLIS))

    /** Start in epoch millis (device zone): day + time, or the day's midnight without a time. */
    fun startsAt(dateMillis: Long, time: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (dateMillis <= 0) return null
        val at = parseMeetingTime(time) ?: LocalTime.MIDNIGHT
        val day = utcDay(dateMillis)
        return day.atTime(at).atZone(zone).toInstant().toEpochMilli()
    }

    /** When the meeting is over; null without a date. */
    fun endsAt(meeting: MeetingEvent, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (meeting.date <= 0) return null
        val day = utcDay(meeting.date)
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
