package com.example.espoti.data.repository

import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingInvite

// ============================================================================
// MEETING REPOSITORY
// ----------------------------------------------------------------------------
// Single source of meeting data for the ViewModels. For now it serves
// in-memory sample data (prototype); when a backend exists, only this class
// should change - ViewModels and screens keep working as they are.
// ============================================================================
class MeetingRepository {

    private val meetings = listOf(
        Meeting(
            id = 1,
            name = "Restaurant Los Andes",
            rating = 4,
            travelTime = "",
            distance = "",
            latitude = 4.6015,
            longitude = -74.0662,
            startsAtEpochMillis = java.time.ZonedDateTime.of(
                2026, 10, 20,
                14, 0, 0, 0,
                java.time.ZoneId.of("America/Bogota")
            ).toInstant().toEpochMilli()
        )
    )

    private val upcomingInvites = listOf(
        MeetingInvite("Restaurant", "2:00 pm", "2 km", "Ana and two more"),
        MeetingInvite("Park", "4:00 pm", "3 km", "Juan and two more")
    )

    fun getMeetings(): List<Meeting> = meetings

    fun getMeetingById(id: Int?): Meeting? = meetings.find { it.id == id }

    fun getUpcomingInvites(): List<MeetingInvite> = upcomingInvites
}
