package com.example.espoti.model

/** A meeting shown as a small card in Home's "Next Meetings" row. */
data class MeetingInvite(
    val place: String,
    val time: String,
    val distance: String,
    val peopleLabel: String
)
