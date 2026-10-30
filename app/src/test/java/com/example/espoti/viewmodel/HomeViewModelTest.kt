package com.example.espoti.viewmodel

import com.example.espoti.data.connectivity.AlwaysOnlineConnectivity
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.Location
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

// ============================================================================
// HOME VIEW MODEL TEST
// ----------------------------------------------------------------------------
// Home reads the user's Firestore meetings: Next Meetings = voting/confirmed
// (same order as the Meetings tab), Meeting History = finished ones, with
// ready-to-render labels ("Tomorrow, 6:00 PM", "juan and 1 more").
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val uid = "uid-1"
    // 2027-01-15T08:00:00Z (a Friday). Labels are checked in UTC.
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    /** UTC midnight [days] from now, like the DatePicker stores it. */
    private fun dayUtc(days: Int): Long =
        Instant.ofEpochMilli(now + days * day).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val place = Place(id = "p1", name = "Cafe Central", location = Location(4.6, -74.06))

    private val users = listOf(
        User(id = uid, username = "ana"),
        User(id = "uid-2", username = "juan"),
        User(id = "uid-3", username = "maria")
    )

    private val meetings = listOf(
        MeetingEvent(
            id = "voting", creatorId = "uid-2", participantIds = listOf("uid-2", uid, "uid-3"),
            candidatePlaceIds = listOf("p1"), status = StatusMeeting.VOTING, activity = ActivityType.COFFEE,
            date = dayUtc(3), time = "10:00", votingDeadline = now + day, votes = mapOf("uid-2" to "p1")
        ),
        MeetingEvent(
            id = "confirmed", creatorId = uid, participantIds = listOf(uid),
            status = StatusMeeting.UPCOMING, placeId = "p1", date = dayUtc(1), time = "18:00"
        ),
        MeetingEvent(
            id = "past", creatorId = uid, participantIds = listOf(uid, "uid-3"),
            status = StatusMeeting.UPCOMING, placeId = "p1", date = dayUtc(-10)
        ),
        MeetingEvent(id = "canceled", creatorId = uid, participantIds = listOf(uid), status = StatusMeeting.CANCELED)
    )

    private lateinit var repository: FakeMeetingEventRepository
    private lateinit var vm: HomeViewModel

    private fun homeViewModel(repo: MeetingEventRepository) = HomeViewModel(
        meetingRepository = repo,
        placeRepository = FakePlaceRepository(listOf(place)),
        userRepository = FakeUserRepository(users),
        currentUserId = { uid },
        connectivity = AlwaysOnlineConnectivity,
        clock = { now },
        zone = ZoneOffset.UTC
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeMeetingEventRepository(meetings)
        vm = homeViewModel(repository)
        vm.refresh()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun greeting_usesUsername() {
        assertEquals("ana", vm.userName.value)
        assertTrue(vm.hasLoaded.value)
        assertNull(vm.error.value)
    }

    @Test
    fun nextMeetings_votingFirst_thenConfirmed() {
        val next = vm.nextMeetings.value
        assertEquals(listOf("voting", "confirmed"), next.map { it.id })

        val voting = next[0]
        assertTrue(voting.isVoting)
        assertEquals("Coffee meeting", voting.title)
        assertEquals("Voting closes Tomorrow, 8:00 AM", voting.whenLabel)
        assertEquals("juan and 1 more", voting.peopleLabel)
        assertEquals(3, voting.avatarCount)

        val confirmed = next[1]
        assertFalse(confirmed.isVoting)
        assertEquals("Cafe Central", confirmed.title)
        assertEquals("Tomorrow, 6:00 PM", confirmed.whenLabel)
        assertEquals("Just you", confirmed.peopleLabel)
    }

    @Test
    fun history_showsFinishedMeetings_andStoresPrevious() {
        val history = vm.history.value
        assertEquals(listOf("past"), history.map { it.id })
        assertEquals("Cafe Central", history[0].title)
        assertEquals("Tue, Jan 5", history[0].whenLabel)
        assertEquals("maria", history[0].peopleLabel)
        assertEquals(listOf("past" to StatusMeeting.PREVIOUS), repository.statusUpdates)
    }

    @Test
    fun canceledMeetings_areNotShown() {
        val shown = (vm.nextMeetings.value + vm.history.value).map { it.id }
        assertFalse("canceled" in shown)
    }

    @Test
    fun loadFailure_showsError() {
        val failing = object : MeetingEventRepository(
            dbProvider = { error("no Firestore in tests") }
        ) {
            override suspend fun getMeetingsForUser(
                uid: String
            ): Result<List<MeetingEvent>> =
                Result.failure(IllegalStateException("network unavailable"))

            override suspend fun getCachedMeetingsForUser(
                uid: String
            ): Result<List<MeetingEvent>> =
                Result.failure(IllegalStateException("cache unavailable"))
        }

        val failingVm = homeViewModel(failing)
        failingVm.refresh()

        assertEquals(
            "Couldn't load saved meetings. Connect to the internet and try again.",
            failingVm.error.value
        )
        assertTrue(failingVm.nextMeetings.value.isEmpty())
        assertTrue(failingVm.history.value.isEmpty())
        assertTrue(failingVm.hasLoaded.value)
        assertFalse(failingVm.isLoading.value)
        assertEquals("ana", failingVm.userName.value)
    }

    @Test
    fun peopleLabel_cases() {
        assertEquals("Just you", peopleLabel(null, 0, 0))
        assertEquals("2 invited", peopleLabel(null, 0, 2))
        assertEquals("juan", peopleLabel("juan", 1, 0))
        assertEquals("juan and 2 more", peopleLabel("juan", 3, 1))
        assertEquals("2 friends", peopleLabel(null, 2, 0))
    }
}
