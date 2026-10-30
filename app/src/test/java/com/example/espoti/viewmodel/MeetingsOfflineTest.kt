package com.example.espoti.viewmodel

import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.PlaceRepository
import com.example.espoti.model.MeetingTab
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeetingsOfflineTest {

    private val uid = "owner"
    private val now = 1_800_000_000_000L

    private val past = MeetingEvent(
        id = "past",
        participantIds = listOf(uid),
        status = StatusMeeting.UPCOMING,
        placeId = "place",
        date = now - 10L * 24 * 60 * 60 * 1000,
        time = "12:00"
    )

    private val canceled = MeetingEvent(
        id = "canceled",
        participantIds = listOf(uid),
        status = StatusMeeting.CANCELED
    )

    private val expiredVoting = MeetingEvent(
        id = "expired",
        participantIds = listOf(uid),
        status = StatusMeeting.VOTING,
        votingDeadline = now - 1
    )

    private class Connection(online: Boolean) : ConnectivityObserver {
        override val isOnline = MutableStateFlow(online)
    }

    private class Meetings(
        initial: List<MeetingEvent>
    ) : MeetingEventRepository(
        dbProvider = { error("No Firestore in unit tests") }
    ) {
        var cached = initial
        var remote = initial
        var cacheUnavailable = false
        var hangRemote = false

        var remoteReads = 0
        var cachedReads = 0
        var closeCalls = 0
        var statusWrites = 0

        override suspend fun getMeetingsForUser(
            uid: String
        ): Result<List<MeetingEvent>> {
            remoteReads++

            if (hangRemote) {
                awaitCancellation()
            }

            return Result.success(
                remote.filter { uid in it.participantIds }
            )
        }

        override suspend fun getCachedMeetingsForUser(
            uid: String
        ): Result<List<MeetingEvent>> {
            cachedReads++

            return if (cacheUnavailable) {
                Result.failure(IllegalStateException("Cache unavailable"))
            } else {
                Result.success(
                    cached.filter { uid in it.participantIds }
                )
            }
        }

        override suspend fun closeVotingIfDone(
            meetingId: String,
            now: Long
        ): Result<MeetingEvent?> {
            closeCalls++
            return Result.success(
                remote.firstOrNull { it.id == meetingId }
            )
        }

        override suspend fun updateStatus(
            id: String,
            status: StatusMeeting
        ): Result<Unit> {
            statusWrites++
            return Result.success(Unit)
        }
    }

    private class Places : PlaceRepository(
        dbProvider = { error("No Firestore in unit tests") }
    ) {
        var remoteReads = 0
        var cachedReads = 0

        override suspend fun getPlace(id: String): Result<Place?> {
            remoteReads++
            return Result.success(Place(id = id, name = "Saved place"))
        }

        override suspend fun getCachedPlace(id: String): Result<Place?> {
            cachedReads++
            return Result.success(Place(id = id, name = "Saved place"))
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: Meetings,
        places: Places,
        connection: Connection
    ) = MeetingsViewModel(
        meetingRepository = repository,
        placeRepository = places,
        currentUserId = { uid },
        clock = { now },
        connectivity = connection,
        refreshTimeoutMillis = 1_000L
    )

    @Test
    fun offline_loadsHistoryWithoutRemoteReadsOrWrites() = runTest {
        val repository = Meetings(
            listOf(past, canceled, expiredVoting)
        )
        val places = Places()
        val vm = viewModel(repository, places, Connection(false))

        vm.refresh()
        advanceUntilIdle()

        assertEquals(
            listOf("past"),
            vm.meetingsFor(MeetingTab.PREVIOUS).map { it.id }
        )
        assertTrue(
            vm.meetingsFor(MeetingTab.CANCELED).any {
                it.id == "canceled"
            }
        )
        assertEquals(0, repository.remoteReads)
        assertEquals(0, repository.closeCalls)
        assertEquals(0, repository.statusWrites)
        assertEquals(0, places.remoteReads)
        assertTrue(places.cachedReads > 0)
        assertTrue(vm.isShowingSaved.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun reconnection_refreshesHistoryAndPreservesSelectedTab() = runTest {
        val repository = Meetings(listOf(past, canceled))
        val connection = Connection(false)
        val vm = viewModel(repository, Places(), connection)

        vm.refresh()
        vm.onTabSelected(MeetingTab.PREVIOUS)
        advanceUntilIdle()

        repository.remote = listOf(
            past.copy(id = "new-past", status = StatusMeeting.PREVIOUS)
        )

        connection.isOnline.value = true
        advanceUntilIdle()

        assertEquals(
            listOf("new-past"),
            vm.meetingsFor(MeetingTab.PREVIOUS).map { it.id }
        )
        assertEquals(MeetingTab.PREVIOUS, vm.selectedTab.value)
        assertEquals(1, repository.remoteReads)
        assertFalse(vm.isShowingSaved.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun timeout_fallsBackToSavedHistory() = runTest {
        val repository = Meetings(listOf(past, canceled))
        repository.hangRemote = true

        val vm = viewModel(repository, Places(), Connection(true))

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.isShowingSaved.value)
        assertFalse(vm.isLoading.value)
        assertNotNull(vm.error.value)
        assertEquals(
            listOf("past"),
            vm.meetingsFor(MeetingTab.PREVIOUS).map { it.id }
        )
        assertEquals(0, repository.statusWrites)
    }

    @Test
    fun cacheFailure_stopsLoadingAndShowsError() = runTest {
        val repository = Meetings(emptyList())
        repository.cacheUnavailable = true

        val vm = viewModel(repository, Places(), Connection(false))

        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.isLoading.value)
        assertNotNull(vm.error.value)
        assertTrue(vm.meetings.value.isEmpty())
        assertEquals(0, repository.remoteReads)
    }
}