package com.example.espoti.data.repository

import com.example.espoti.model.MeetingInvite

// ============================================================================
// MEETING REPOSITORY (prototype sample data)
// ----------------------------------------------------------------------------
// Only Home's "upcoming invites" row still uses this in-memory data. The
// Meetings tabs and MeetingDetail read Firestore through MeetingEventRepository.
// ============================================================================
class MeetingRepository {

    private val upcomingInvites = listOf(
        MeetingInvite("Restaurant", "2:00 pm", "2 km", "Ana and two more"),
        MeetingInvite("Park", "4:00 pm", "3 km", "Juan and two more")
    )

    fun getUpcomingInvites(): List<MeetingInvite> = upcomingInvites
}
