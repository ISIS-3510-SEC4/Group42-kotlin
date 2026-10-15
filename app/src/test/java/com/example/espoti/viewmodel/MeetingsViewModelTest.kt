package com.example.espoti.viewmodel

import com.example.espoti.model.MeetingTab
import com.example.espoti.model.domain.Location
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

// ============================================================================
// MEETINGS VIEW MODEL TEST
// ----------------------------------------------------------------------------
// Firestore meetings split into tabs: VOTING first in Upcoming, expired
// voting closed on refresh, finished confirmed meetings stored as PREVIOUS.
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class MeetingsViewModelTest {

    private val uid = "uid-1"
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    /** UTC midnight [days] from now, like the DatePicker stores it. */
    private fun dayUtc(days: Int): Long =
        Instant.ofEpochMilli(now + days * day).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val place = Place(id = "p1", name = "Cafe Central", location = Location(4.6, -74.06))

    private val meetings = listOf(
        // Still voting, user hasn't voted.
        MeetingEvent(
            id = "voting", creatorId = "uid-2", participantIds = listOf("uid-2", uid),
            candidatePlaceIds = listOf("p1"), status = StatusMeeting.VOTING,
            votingDeadline = now + day, votes = mapOf("uid-2" to "p1")
        ),
        // Confirmed, tomorrow.
        MeetingEvent(
            id = "confirmed", creatorId = uid, participantIds = listOf(uid),
            status = StatusMeeting.UPCOMING, placeId = "p1", date = dayUtc(1), time = "18:00"
        ),
        // Voting expired: refresh must close it (winner p1).
        MeetingEvent(
            id = "expired", creatorId = uid, participantIds = listOf(uid, "uid-2"),
            candidatePlaceIds = listOf("p1"), status = StatusMeeting.VOTING,
            votingDeadline = now - 1, votes = mapOf(uid to "p1")
        ),
        // Confirmed, 10 days ago -> Previous.
        MeetingEvent(
            id = "past", creatorId = uid, participantIds = listOf(uid),
            status = StatusMeeting.UPCOMING, placeId = "p1", date = dayUtc(-10)
        ),
        MeetingEvent(id = "canceled", creatorId = uid, participantIds = listOf(uid), status = StatusMeeting.CANCELED)
    )

    private lateinit var repository: FakeMeetingEventRepository
    private lateinit var vm: MeetingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeMeetingEventRepository(meetings)
        vm = MeetingsViewModel(
            meetingRepository = repository,
            placeRepository = FakePlaceRepository(listOf(place)),
            currentUserId = { uid },
            clock = { now }
        )
        vm.refresh()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun upcoming_listsVotingFirst_thenConfirmedByDate() {
        val upcoming = vm.meetingsFor(MeetingTab.UPCOMING)
        assertEquals(listOf("voting", "confirmed", "expired"), upcoming.map { it.id })

        val voting = upcoming.first()
        assertTrue(voting.isVoting)
        assertFalse(voting.hasVoted)
        assertEquals("Other meeting", voting.name)
        assertEquals(1, voting.voteCount)
        assertEquals("Cafe Central", upcoming[1].name)
    }

    @Test
    fun refresh_closesExpiredVoting() {
        assertTrue("expired" in repository.closeCalls)
        assertEquals(StatusMeeting.UPCOMING, repository.meetings.getValue("expired").status)
        assertEquals("p1", repository.meetings.getValue("expired").placeId)
        assertFalse("voting" in repository.closeCalls)
    }

    @Test
    fun previousAndCanceled_tabs() {
        assertEquals(listOf("past"), vm.meetingsFor(MeetingTab.PREVIOUS).map { it.id })
        assertEquals(listOf("canceled"), vm.meetingsFor(MeetingTab.CANCELED).map { it.id })
    }

    @Test
    fun refresh_storesPreviousForFinishedMeetings() {
        // Only the finished confirmed meeting is written; the others keep their status.
        assertEquals(listOf("past" to StatusMeeting.PREVIOUS), repository.statusUpdates)
        assertEquals(StatusMeeting.PREVIOUS, repository.meetings.getValue("past").status)
    }

    @Test
    fun highlight_opensUpcomingWithThatMeeting() {
        vm.onTabSelected(MeetingTab.PREVIOUS)
        vm.highlight("voting")
        assertEquals(MeetingTab.UPCOMING, vm.selectedTab.value)
        assertEquals("voting", vm.highlightedMeetingId.value)
    }
}
