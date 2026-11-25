package com.example.espoti.model.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

// ============================================================================
// USER STATS CALCULATOR TEST (BQ type 2 of Sprint 4)
// ----------------------------------------------------------------------------
// Counts per status, friends/places of finished meetups, favorite activity,
// meetups per month and how often the user's vote decided the place.
// ============================================================================
class UserStatsCalculatorTest {

    private val me = "me"
    // 2027-01-15T08:00:00Z
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    /** UTC midnight [days] from now, like the DatePicker stores it. */
    private fun dayUtc(days: Int): Long =
        Instant.ofEpochMilli(now + days * day).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val meetings = listOf(
        // Still UPCOMING in Firestore but already over -> counts as attended.
        MeetingEvent(
            id = "m1", participantIds = listOf(me, "a", "b"), status = StatusMeeting.UPCOMING,
            placeId = "p1", activity = ActivityType.COFFEE, date = dayUtc(-10), time = "18:00",
            votes = mapOf(me to "p1", "a" to "p1")
        ),
        MeetingEvent(
            id = "m2", participantIds = listOf(me, "a"), status = StatusMeeting.PREVIOUS,
            placeId = "p2", activity = ActivityType.BAR, date = dayUtc(-40),
            votes = mapOf(me to "p1", "a" to "p2")
        ),
        MeetingEvent(
            id = "m3", participantIds = listOf(me, "a"), status = StatusMeeting.PREVIOUS,
            placeId = "p1", activity = ActivityType.COFFEE, date = dayUtc(-5)
        ),
        // Voting: upcoming, the vote counts as cast but nothing is decided yet.
        MeetingEvent(
            id = "m4", participantIds = listOf(me, "b"), status = StatusMeeting.VOTING,
            date = dayUtc(3), votingDeadline = now + day, votes = mapOf(me to "p1")
        ),
        MeetingEvent(
            id = "m5", participantIds = listOf(me), status = StatusMeeting.CANCELED,
            votes = mapOf(me to "p2")
        ),
        // Not mine: ignored.
        MeetingEvent(id = "m6", participantIds = listOf("a", "b"), status = StatusMeeting.PREVIOUS, placeId = "p9"),
        // Attended, but older than the 6 months shown in the chart.
        MeetingEvent(
            id = "m7", participantIds = listOf(me, "c"), status = StatusMeeting.PREVIOUS,
            placeId = "p3", activity = ActivityType.PARK, date = dayUtc(-300)
        )
    )

    private val counts = UserStatsCalculator.compute(me, meetings, now, ZoneOffset.UTC)

    @Test
    fun countsMeetingsByEffectiveStatus() {
        assertEquals(4, counts.attended)
        assertEquals(1, counts.upcoming)
        assertEquals(1, counts.canceled)
        assertEquals(4, counts.votesCast)
    }

    @Test
    fun voteInfluence_onlyDecidedMeetingsWhereTheUserVoted() {
        assertEquals(2, counts.decidedWithVote)
        assertEquals(1, counts.wonVotes)
        val stats = UserStats(decidedWithVote = counts.decidedWithVote, wonVotes = counts.wonVotes)
        assertEquals(50, stats.voteWinPercent)
        assertNull(UserStats().voteWinPercent)
    }

    @Test
    fun topFriendsAndPlaces_bestFirst_tiesById() {
        assertEquals(listOf("a" to 3, "b" to 1, "c" to 1), counts.topFriends)
        assertEquals(listOf("p1" to 2, "p2" to 1, "p3" to 1), counts.topPlaces)
        assertEquals("m2", counts.placeMeeting["p2"])
    }

    @Test
    fun favoriteActivity_isTheMostFrequentOne() {
        assertEquals(ActivityType.COFFEE, counts.favoriteActivity)
    }

    @Test
    fun perMonth_lastSixMonthsWithZeros() {
        assertEquals(
            listOf("2026-08", "2026-09", "2026-10", "2026-11", "2026-12", "2027-01"),
            counts.perMonth.map { it.key }
        )
        assertEquals(listOf(0, 0, 0, 0, 1, 2), counts.perMonth.map { it.count })
        assertEquals("Jan", counts.perMonth.last().label)
    }

    @Test
    fun noMeetings_givesEmptyStats() {
        val empty = UserStatsCalculator.compute(me, emptyList(), now, ZoneOffset.UTC)
        assertEquals(0, empty.attended)
        assertEquals(emptyList<Pair<String, Int>>(), empty.topFriends)
        assertNull(empty.favoriteActivity)
        assertEquals(6, empty.perMonth.size)
    }
}
