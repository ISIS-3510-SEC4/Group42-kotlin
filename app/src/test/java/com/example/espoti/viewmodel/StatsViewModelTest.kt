package com.example.espoti.viewmodel

import com.example.espoti.data.cache.StatsCache
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.InMemoryStatsLocalStore
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.StatsRepository
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.RankedItem
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.TrendingPlace
import com.example.espoti.model.domain.User
import com.example.espoti.model.domain.UserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ============================================================================
// STATS VIEW MODEL TEST ("My stats", Sprint 4)
// ----------------------------------------------------------------------------
// Online: computes, resolves names, saves (SQLite store + memory cache).
// Memory cache: reopening within the TTL doesn't read Firestore again.
// Offline: shows the saved snapshot with "saved at", or a clear message.
// Reconnection: refreshes by itself.
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val me = "me"
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private class Connection(online: Boolean) : ConnectivityObserver {
        override val isOnline = MutableStateFlow(online)
    }

    /** Counts reads; [fail] makes the server read fail. */
    private class Meetings(private val all: List<MeetingEvent>) :
        MeetingEventRepository(dbProvider = { error("no Firestore") }) {
        var reads = 0
        var fail = false
        override suspend fun getMeetingsForUser(uid: String): Result<List<MeetingEvent>> {
            reads++
            return if (fail) Result.failure(IllegalStateException("offline"))
            else Result.success(all.filter { uid in it.participantIds })
        }
    }

    private class Trending(private val places: List<TrendingPlace>) :
        StatsRepository(dbProvider = { error("no Firestore") }) {
        override suspend fun getTrendingPlaces(limit: Int) = Result.success(places.take(limit))
    }

    private val meetings = listOf(
        MeetingEvent(
            id = "m1", participantIds = listOf(me, "uid-ana"), status = StatusMeeting.PREVIOUS,
            placeId = "p1", date = now - 3 * day, votes = mapOf(me to "p1")
        ),
        MeetingEvent(
            id = "m2", participantIds = listOf(me, "uid-ana", "uid-gone"), status = StatusMeeting.PREVIOUS,
            placeId = "custom", date = now - 2 * day, votes = mapOf(me to "p1")
        )
    )

    private lateinit var repo: Meetings
    private lateinit var store: InMemoryStatsLocalStore
    private lateinit var cache: StatsCache
    private val tracked = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = Meetings(meetings)
        store = InMemoryStatsLocalStore()
        cache = StatsCache(clock = { now })
        tracked.clear()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(connection: Connection = Connection(online = true)) = StatsViewModel(
        meetingRepository = repo,
        placeRepository = FakePlaceRepository(listOf(Place(id = "p1", name = "Cafe Central"))),
        userRepository = FakeUserRepository(listOf(User(id = "uid-ana", username = "ana"))),
        statsRepository = Trending(listOf(TrendingPlace("Parque 93", "Bogotá", 9))),
        statsStore = store,
        statsCache = cache,
        currentUserId = { me },
        trackFeature = { tracked += it },
        connectivity = connection,
        clock = { now },
        computeDispatcher = Dispatchers.Main
    )

    @Test
    fun online_computesResolvesNamesAndSaves() {
        val vm = vm()
        val stats = vm.stats.value!!

        assertEquals(2, stats.attended)
        assertEquals(listOf(RankedItem("uid-ana", "ana", 2), RankedItem("uid-gone", "A friend", 1)), stats.topFriends)
        // Tie (1 each) -> by id ("custom" < "p1"). The custom place isn't in
        // places/ nor readable here -> generic label instead of an id.
        assertEquals(listOf("A place", "Cafe Central"), stats.topPlaces.map { it.label })
        assertEquals(50, stats.voteWinPercent)
        assertEquals("Parque 93", stats.trendingPlaces.single().name)
        assertEquals(now, stats.computedAt)
        assertEquals(stats, store.saved[me])
        assertEquals(stats, cache.get(me))
        assertNull(vm.notice.value)
        assertNull(vm.error.value)
        assertEquals(listOf(StatsViewModel.FEATURE), tracked)
    }

    @Test
    fun reopenedWithinTtl_usesMemoryCache_withoutReadingFirestore() {
        vm()
        assertEquals(1, repo.reads)
        val second = vm()
        assertEquals(1, repo.reads)
        assertEquals(2, second.stats.value!!.attended)
    }

    @Test
    fun forceRefresh_skipsTheMemoryCache() {
        val vm = vm()
        vm.refresh(force = true)
        assertEquals(2, repo.reads)
    }

    @Test
    fun offline_showsSavedSnapshotWithTime() {
        val saved = UserStats(attended = 5, computedAt = now - day)
        kotlinx.coroutines.runBlocking { store.save(me, saved) }

        val vm = vm(Connection(online = false))

        assertEquals(saved, vm.stats.value)
        assertTrue(vm.notice.value!!.startsWith("You're offline. Showing your stats saved at"))
        assertEquals(0, repo.reads)
    }

    @Test
    fun offline_withoutSnapshot_showsClearMessage() {
        val vm = vm(Connection(online = false))
        assertNull(vm.stats.value)
        assertEquals(StatsViewModel.NO_DATA_OFFLINE, vm.error.value)
    }

    @Test
    fun serverFailure_keepsSavedSnapshot() {
        val saved = UserStats(attended = 5, computedAt = now - day)
        kotlinx.coroutines.runBlocking { store.save(me, saved) }
        repo.fail = true

        val vm = vm()

        assertEquals(saved, vm.stats.value)
        assertTrue(vm.notice.value!!.startsWith("Couldn't refresh your stats."))
    }

    @Test
    fun reconnection_refreshesByItself() {
        val connection = Connection(online = false)
        val vm = vm(connection)
        assertNull(vm.stats.value)

        connection.isOnline.value = true

        assertNotNull(vm.stats.value)
        assertEquals(2, vm.stats.value!!.attended)
        assertNull(vm.error.value)
        assertEquals(1, repo.reads)
    }
}
