package com.example.espoti.model

/**
 * A meeting shown as a small card on Home ("Next Meetings" and "Meeting
 * History"). Built by HomeViewModel from a Firestore MeetingEvent; labels
 * come ready to render.
 */
data class HomeMeetingCard(
    val id: String,
    // Place name once decided; while voting, the activity ("Coffee meeting").
    val title: String,
    // "Today, 3:00 PM", or "Voting closes Tomorrow, 9:00 AM" while voting.
    val whenLabel: String,
    // "Ana and 2 more", "2 invited", "Just you".
    val peopleLabel: String,
    val avatarCount: Int,
    val isVoting: Boolean = false
)
