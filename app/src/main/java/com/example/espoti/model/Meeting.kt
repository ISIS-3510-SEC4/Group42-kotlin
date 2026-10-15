package com.example.espoti.model

import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.MeetingLifecycle
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import java.util.Locale

// ============================================================================
// MEETING (UI model)
// ----------------------------------------------------------------------------
// What the Meetings / MeetingDetail screens render. Built from a Firestore
// MeetingEvent (+ its Place once the voting decided one) by [toUiMeeting].
// ============================================================================
data class Meeting(
    val id: String,
    // Place name once decided; while voting, the activity ("Coffee meeting").
    val name: String,
    val rating: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startsAtEpochMillis: Long? = null,
    // Effective status (an expired VOTING meeting is already UPCOMING).
    val status: StatusMeeting = StatusMeeting.UPCOMING,
    val votingDeadline: Long? = null,
    val hasVoted: Boolean = false,
    val participantCount: Int = 0,
    val voteCount: Int = 0
) {
    val isVoting: Boolean get() = status == StatusMeeting.VOTING
}

/**
 * Maps a Firestore meeting to the UI model for [uid].
 * [place] is the decided place (null while voting or if it was deleted).
 * Start time and status come from [MeetingLifecycle] (an expired voting is
 * UPCOMING, a finished meeting is PREVIOUS even before anybody stored it).
 */
fun MeetingEvent.toUiMeeting(
    uid: String,
    place: Place?,
    now: Long = System.currentTimeMillis()
): Meeting {
    val status = MeetingLifecycle.effectiveStatus(this, now)
    val voting = status == StatusMeeting.VOTING
    return Meeting(
        id = id,
        name = if (!voting && place != null) place.name else activityLabel(),
        rating = if (voting) 0 else place?.rating?.stars ?: 0,
        latitude = place?.location?.latitude?.takeIf { place.location.latitude != 0.0 },
        longitude = place?.location?.longitude?.takeIf { place.location.longitude != 0.0 },
        startsAtEpochMillis = MeetingLifecycle.startsAt(date, time),
        status = status,
        votingDeadline = votingDeadline.takeIf { it > 0 },
        hasVoted = uid in votes,
        participantCount = participantIds.size,
        voteCount = votes.size
    )
}

/** "Coffee meeting", "Other meeting"... from the activity enum. */
fun MeetingEvent.activityLabel(): String =
    activity.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) } + " meeting"
