package com.example.espoti.data.repository

import android.content.Context
import com.example.espoti.data.location.LocationService
import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingContext
import com.example.espoti.model.MeetingContextCalculator

class MeetingContextRepository(
    context: Context,
    private val locationService: LocationService
) {

    private val arrivals =
        context.applicationContext.getSharedPreferences(
            "meeting_arrivals",
            Context.MODE_PRIVATE
        )

    suspend fun refresh(
        meetings: List<Meeting>
    ): Map<Int, MeetingContext> {
        val validMeetings = meetings.filter {
            it.latitude != null &&
                    it.longitude != null &&
                    it.startsAtEpochMillis != null
        }

        check(validMeetings.isNotEmpty()) {
            "This meeting needs a location and start time."
        }

        val fix = locationService.currentLocation()
        val now = System.currentTimeMillis()

        return validMeetings.associate { meeting ->
            meeting.id to MeetingContextCalculator.calculate(
                meeting = meeting,
                fix = fix,
                now = now,
                checkedIn = arrivals.contains(key(meeting))
            )
        }
    }

    suspend fun checkIn(meeting: Meeting): MeetingContext {
        val status = refresh(listOf(meeting)).getValue(meeting.id)

        check(status.near) {
            "Check-in requires a precise location within 100 m. " +
                    "Move closer and try again."
        }

        arrivals.edit()
            .putLong(key(meeting), System.currentTimeMillis())
            .apply()

        return status.copy(
            checkedIn = true,
            message = "Arrived (checked in)"
        )
    }

    private fun key(meeting: Meeting): String =
        "${meeting.id}_${meeting.startsAtEpochMillis}"
}