package com.example.espoti.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.espoti.analytics.AnalyticsTracker
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.InMemoryTransportFilterPreferences
import com.example.espoti.data.repository.AnalyticsRepository
import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.domain.DecisionMethod
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ============================================================================
// MEETING DETAIL – ORGANIZER CHOOSES THE PLACE DIRECTLY
// ----------------------------------------------------------------------------
// BQ "voting vs. direct choice": only the creator may close the voting with
// a place of their choice; everybody else keeps the normal vote.
// The candidate loader runs on Dispatchers.IO, so the tests wait (real time)
// until the detail finished loading.
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class MeetingDetailDirectChoiceTest {

    private class FakeAnalyticsRepository : AnalyticsRepository {
        val events = mutableListOf<AnalyticsEvent>()
        override suspend fun saveEvent(event: AnalyticsEvent): Result<Unit> {
            events += event
            return Result.success(Unit)
        }

        override suspend fun getGlobalRejectionRates(): Result<Map<String, Double>> =
            Result.success(emptyMap())
    }

    private class Connection(online: Boolean) : ConnectivityObserver {
        override val isOnline = MutableStateFlow(online)
    }

    private val now = 1_800_000_000_000L

    private val voting = MeetingEvent(
        id = "m1",
        creatorId = "organizer",
        participantIds = listOf("organizer", "friend"),
        candidatePlaceIds = listOf("p1", "p2"),
        status = StatusMeeting.VOTING,
        votingDeadline = now + 3_600_000L,
        createdAt = now - 60_000L,
        votes = mapOf("friend" to "p1")
    )

    private val places = listOf(
        Place(id = "p1", name = "Cafe"),
        Place(id = "p2", name = "Bar")
    )

    private lateinit var analytics: FakeAnalyticsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        analytics = FakeAnalyticsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        uid: String,
        meetings: FakeMeetingEventRepository,
        online: Boolean = true
    ): MeetingDetailViewModel {
        val vm = MeetingDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("meetingId" to "m1")),
            meetingRepository = meetings,
            placeRepository = FakePlaceRepository(places),
            analyticsTracker = AnalyticsTracker(
                repository = analytics,
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                currentUserId = { uid }
            ),
            currentUserId = { uid },
            clock = { now },
            connectivity = Connection(online),
            filterPreferences = InMemoryTransportFilterPreferences(),
            persistenceScope = CoroutineScope(UnconfinedTestDispatcher())
        )
        runBlocking {
            withTimeout(5_000) {
                while (vm.isLoading.value || vm.visibleCandidates.value.isEmpty()) delay(10)
            }
        }
        return vm
    }

    @Test
    fun organizerChoosesThePlaceForTheGroup() {
        val meetings = FakeMeetingEventRepository(listOf(voting))
        val vm = viewModel("organizer", meetings)
        assertTrue(vm.isCreator.value)

        vm.onPlaceSelected("p2")
        var doneId: String? = null
        vm.onChooseDirectly { doneId = it }

        assertEquals("m1", doneId)
        val stored = meetings.meetings.getValue("m1")
        assertEquals(StatusMeeting.UPCOMING, stored.status)
        assertEquals("p2", stored.placeId)
        assertEquals(DecisionMethod.DIRECT, stored.decisionMethod)
        assertNull(vm.voteError.value)
        // The chosen place still counts for the "popular areas" BQ.
        val selected = analytics.events.single { it.eventType.name == "PLACE_SELECTED" }
        assertEquals("ORGANIZER_DIRECT", selected.metadata["source"])
    }

    @Test
    fun participantCannotChooseDirectly() {
        val meetings = FakeMeetingEventRepository(listOf(voting))
        val vm = viewModel("friend", meetings)
        assertFalse(vm.isCreator.value)

        vm.onPlaceSelected("p2")
        var done = false
        vm.onChooseDirectly { done = true }

        assertFalse(done)
        assertNotNull(vm.voteError.value)
        assertEquals(StatusMeeting.VOTING, meetings.meetings.getValue("m1").status)
    }

    @Test
    fun offlineKeepsTheSelectionAndSavesNothing() {
        val meetings = FakeMeetingEventRepository(listOf(voting))
        // Online first so the detail loads, then the connection drops.
        val connection = Connection(true)
        val vm = MeetingDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("meetingId" to "m1")),
            meetingRepository = meetings,
            placeRepository = FakePlaceRepository(places),
            analyticsTracker = AnalyticsTracker(
                repository = analytics,
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                currentUserId = { "organizer" }
            ),
            currentUserId = { "organizer" },
            clock = { now },
            connectivity = connection,
            filterPreferences = InMemoryTransportFilterPreferences(),
            persistenceScope = CoroutineScope(UnconfinedTestDispatcher())
        )
        runBlocking {
            withTimeout(5_000) {
                while (vm.isLoading.value || vm.visibleCandidates.value.isEmpty()) delay(10)
            }
        }
        vm.onPlaceSelected("p2")
        connection.isOnline.value = false

        var done = false
        vm.onChooseDirectly { done = true }

        assertFalse(done)
        assertEquals("p2", vm.selectedPlaceId.value)
        assertEquals(StatusMeeting.VOTING, meetings.meetings.getValue("m1").status)
    }
}
