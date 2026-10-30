package com.example.espoti.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.espoti.analytics.AnalyticsTracker
import com.example.espoti.data.repository.AnalyticsRepository
import com.example.espoti.data.repository.RecommendationDataSource
import com.example.espoti.model.analytics.AnalyticsEvent
import com.example.espoti.model.analytics.AnalyticsEventType
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.Recommendation
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.User
import kotlinx.coroutines.CoroutineScope
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

// ============================================================================
// CREATE MEETING VIEW MODEL TEST
// ----------------------------------------------------------------------------
// Covers the vote guard (no place -> nothing happens) and the
// PLANNING_FLOW_ABANDONED event sent when the flow is left without a saved
// meeting. Fakes replace Firebase; the real AnalyticsTracker runs on an
// unconfined test scope so stored events can be inspected right away.
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class CreateMeetingViewModelTest {

    private class FakeAnalyticsRepository : AnalyticsRepository {
        val events = mutableListOf<AnalyticsEvent>()
        override suspend fun saveEvent(event: AnalyticsEvent): Result<Unit> {
            events += event
            return Result.success(Unit)
        }

        override suspend fun getGlobalRejectionRates(): Result<Map<String, Double>> =
            Result.success(emptyMap())
    }

    private class FakeRecommendations(
        private val places: List<Recommendation>
    ) : RecommendationDataSource {
        override val source = "FAKE"
        override suspend fun getRecommendations(meetingId: String?) = Result.success(places)
    }

    private val place = Recommendation(
        placeId = "place-1",
        name = "Cafe",
        category = "CAFFE",
        rating = 4,
        score = 0.9,
        distanceKm = null
    )

    private lateinit var analytics: FakeAnalyticsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        analytics = FakeAnalyticsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val now = 1_800_000_000_000L
    private val friends = listOf(
        User(id = "uid-2", username = "ana"),
        User(id = "uid-3", username = "juan")
    )

    private fun newViewModel(
        meetings: MeetingEventRepository? = null,
        places: List<Recommendation> = listOf(place)
    ) = CreateMeetingViewModel(
        recommendationRepository = FakeRecommendations(places),
        analyticsTracker = AnalyticsTracker(
            repository = analytics,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            currentUserId = { "uid-1" }
        ),
        meetingRepositoryProvider = { meetings ?: error("Firestore must not be used in this test") },
        currentUserId = { "uid-1" },
        friendshipRepository = FakeFriendshipRepository(friends.map { it.id }),
        userRepository = FakeUserRepository(friends),
        clock = { now }
    )

    /** Runs onCleared() the way navigation does: by clearing the owner's store. */
    private fun clear(vm: CreateMeetingViewModel) {
        val store = ViewModelStore()
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        })[CreateMeetingViewModel::class.java]
        store.clear()
    }

    private fun abandonedEvents() =
        analytics.events.filter { it.eventType == AnalyticsEventType.PLANNING_FLOW_ABANDONED }

    // ---- Vote guard ----------------------------------------------------

    @Test
    fun vote_withoutSelectedPlace_doesNothing() {
        val vm = newViewModel()
        vm.requestRecommendations()
        var done = false

        vm.onVoteConfirmed { done = true }

        assertFalse(done)
        assertFalse(vm.isSavingMeeting.value)
        assertNull(vm.saveMeetingError.value)
    }

    @Test
    fun selectingPlace_exposesItsId() {
        val vm = newViewModel()
        vm.onRestaurantSelected(place)
        assertEquals("place-1", vm.selectedPlaceId.value)
    }

    // ---- Friends --------------------------------------------------------

    @Test
    fun friends_areLoadedAndToggled() {
        val vm = newViewModel()
        assertEquals(listOf("ana", "juan"), vm.friends.value.map { it.username })

        vm.toggleFriend("uid-2")
        vm.toggleFriend("uid-3")
        vm.toggleFriend("uid-3")

        assertEquals(setOf("uid-2"), vm.selectedFriendIds.value)
    }

    // ---- Vote creates a VOTING meeting ----------------------------------

    @Test
    fun vote_withInvitedFriends_createsVotingMeeting() {
        val meetings = FakeMeetingEventRepository()
        val second = place.copy(placeId = "place-2", name = "Bar")
        val vm = newViewModel(meetings, places = listOf(place, second))
        vm.toggleFriend("uid-2")
        vm.requestRecommendations()
        vm.onRestaurantSelected(second)
        var doneId: String? = null

        vm.onVoteConfirmed { doneId = it }

        val stored = meetings.meetings.getValue(doneId!!)
        assertEquals(StatusMeeting.VOTING, stored.status)
        assertEquals(listOf("uid-1"), stored.participantIds)
        assertEquals(listOf("uid-2"), stored.invitedIds)
        assertEquals(listOf("place-1", "place-2"), stored.candidatePlaceIds)
        assertEquals(mapOf("uid-1" to "place-2"), stored.votes)
        assertNull(stored.placeId)
        // Default deadline: 24 h after creation (no meeting date chosen).
        assertEquals(now + CreateMeetingViewModel.DEFAULT_VOTING_WINDOW_MILLIS, stored.votingDeadline)

        val selected = analytics.events.single { it.eventType == AnalyticsEventType.PLACE_SELECTED }
        assertEquals(doneId, selected.meetingId)
        assertEquals("place-2", selected.placeId)

        // A saved meeting is not an abandoned flow.
        clear(vm)
        assertTrue(abandonedEvents().isEmpty())
    }

    @Test
    fun vote_withoutFriends_closesVotingRightAway() {
        val meetings = FakeMeetingEventRepository()
        val vm = newViewModel(meetings)
        vm.requestRecommendations()
        vm.onRestaurantSelected(place)
        var doneId: String? = null

        vm.onVoteConfirmed { doneId = it }

        val stored = meetings.meetings.getValue(doneId!!)
        assertEquals(StatusMeeting.UPCOMING, stored.status)
        assertEquals("place-1", stored.placeId)
    }

    // ---- Voting deadline ------------------------------------------------

    @Test
    fun deadline_inThePast_isRejected() {
        val vm = newViewModel()
        vm.onVotingDeadlineSelected(now - 1)
        assertNull(vm.votingDeadlineMillis.value)
        assertEquals("The voting deadline must be in the future.", vm.votingDeadlineError.value)
    }

    /**
     * Picks the day and time of [at] like the pickers do (local day + local
     * time in the device zone) and returns [at] at minute precision.
     */
    private fun pickDayAndTime(vm: CreateMeetingViewModel, at: Long): Long {
        val local = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        vm.onDateSelected(local.toLocalDate())
        vm.onTimeSelected(java.time.LocalTime.of(local.hour, local.minute))
        return at - at % 60_000L
    }

    @Test
    fun deadline_afterMeetingStart_isRejected_andDefaultIsCapped() {
        val vm = newViewModel()
        val start = pickDayAndTime(vm, now + 2 * 60 * 60 * 1000L)

        vm.onVotingDeadlineSelected(start + 60_000L)
        assertNull(vm.votingDeadlineMillis.value)
        assertEquals("The voting must close before the meeting starts.", vm.votingDeadlineError.value)

        // Without a chosen deadline, the default never goes past the meeting start.
        assertEquals(start, vm.votingDeadlineFor(now))
    }

    // ---- Meeting time -----------------------------------------------------

    @Test
    fun time_isStoredAsHHmm() {
        val meetings = FakeMeetingEventRepository()
        val vm = newViewModel(meetings)
        vm.onTimeSelected(java.time.LocalTime.of(18, 5))
        assertEquals(java.time.LocalTime.of(18, 5), vm.selectedTime.value)

        vm.requestRecommendations()
        vm.onRestaurantSelected(place)
        var doneId: String? = null
        vm.onVoteConfirmed { doneId = it }

        assertEquals("18:05", meetings.meetings.getValue(doneId!!).time)
    }

    @Test
    fun time_alreadyPassedOnTheChosenDay_isRejected() {
        val vm = newViewModel()
        pickDayAndTime(vm, now + 60 * 60 * 1000L)
        val chosen = vm.selectedTime.value

        // One hour before "now", same day (if "now" is past 01:00 locally).
        val past = java.time.Instant.ofEpochMilli(now - 60 * 60 * 1000L).atZone(java.time.ZoneId.systemDefault())
        vm.onTimeSelected(java.time.LocalTime.of(past.hour, past.minute))

        assertEquals(chosen, vm.selectedTime.value)
        assertEquals("Pick a time later than now.", vm.timeError.value)
    }

    @Test
    fun movingTheDateToWhenTheTimePassed_resetsTheTime() {
        val vm = newViewModel()
        pickDayAndTime(vm, now + 26 * 60 * 60 * 1000L) // tomorrow, 2 h later than now

        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        vm.onDateSelected(today.minusDays(1))

        assertNull(vm.selectedTime.value)
        assertEquals("The time was reset: it has already passed on that day.", vm.timeError.value)
    }

    // ---- Abandonment ---------------------------------------------------

    @Test
    fun leavingOnDetails_tracksAbandonedAtDetails() {
        val vm = newViewModel()
        clear(vm)

        val event = abandonedEvents().single()
        assertEquals(CreateMeetingViewModel.STEP_DETAILS, event.step)
        assertEquals(false, event.metadata["hadSelection"])
        assertEquals(false, event.metadata["hadDate"])
    }

    @Test
    fun leavingOnRecommendations_tracksStepCountAndSelection() {
        val vm = newViewModel()

        // Usamos una fecha futura válida para la validación actual.
        vm.onDateSelected(java.time.LocalDate.now().plusDays(8))

        vm.requestRecommendations()
        vm.onRestaurantSelected(place)

        // La pantalla limpia las recomendaciones al salir.
        vm.cancelRecommendations()
        clear(vm)

        val event = abandonedEvents().single()

        assertEquals(
            CreateMeetingViewModel.STEP_RECOMMENDATIONS,
            event.step
        )
        assertEquals(true, event.metadata["hadSelection"])
        assertEquals(true, event.metadata["hadDate"])
        assertEquals(1, event.metadata["recommendationCount"])
    }

    // ---- Helpers ---------------------------------------------------------

    @Test
    fun formatMeetingDate_usesUtcDay() {
        // 2026-10-14T00:00:00Z
        assertEquals("Wed, Oct 14, 2026", formatMeetingDate(1_791_936_000_000L))
    }

    @Test
    fun activityTypeFor_matchesKnownActivities() {
        assertEquals(ActivityType.COFFEE, activityTypeFor("Coffee with friends"))
        assertEquals(ActivityType.OTHER, activityTypeFor("Board games"))
        assertTrue(activityTypeFor("") == ActivityType.OTHER)
    }
}
