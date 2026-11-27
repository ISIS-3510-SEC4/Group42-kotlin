package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.data.cache.StatsCache
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.StatsLocalStore
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.PlaceRepository
import com.example.espoti.data.repository.StatsRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.di.AppContainer
import com.example.espoti.model.domain.RankedItem
import com.example.espoti.model.domain.UserStats
import com.example.espoti.model.domain.UserStatsCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.ZoneId

// ============================================================================
// STATS VIEW MODEL ("My stats", Sprint 4)
// ----------------------------------------------------------------------------
// BQ type 2: shows the user which friends and places they meet with / visit
// the most, their favorite activity, meetups per month and how often their
// vote decided the place. Also shows the team-wide "trending places"
// (analytics_results/popular_meeting_areas).
//
// Strategies:
// - Caching: StatsCache (memory, 5 min TTL) answers a reopened screen
//   without touching Firestore.
// - Local storage: the finished snapshot is saved in SQLite (user_stats).
// - Eventual connectivity: cache then network. The saved snapshot is shown
//   first; offline it stays with "saved at HH:mm"; the screen refreshes by
//   itself when the connection comes back.
// - Multithreading: meetings and trending places are read at the same time;
//   the aggregation runs on Dispatchers.Default (CPU work, off the main
//   thread); the names of the top friends/places are read in parallel.
// ============================================================================
class StatsViewModel(
    private val meetingRepository: MeetingEventRepository = MeetingEventRepository(),
    private val placeRepository: PlaceRepository = PlaceRepository(),
    private val userRepository: UserRepository = AppContainer.userRepository,
    private val statsRepository: StatsRepository = StatsRepository(),
    private val statsStore: StatsLocalStore = AppContainer.statsStore,
    private val statsCache: StatsCache = AppContainer.statsCache,
    private val currentUserId: () -> String? = { AuthRepository().currentUserId },
    // Lazy so unit tests don't build the Firebase-backed tracker.
    private val trackFeature: (String) -> Unit = {
        AnalyticsDependencies.tracker.featureUsed(feature = it)
    },
    connectivity: ConnectivityObserver = AppContainer.connectivity,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _stats = mutableStateOf<UserStats?>(null)
    /** Stats to show; null until something (memory, device or server) answered. */
    val stats: State<UserStats?> = _stats

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _notice = mutableStateOf<String?>(null)
    /** Caption when the stats shown are the saved ones ("saved at HH:mm"). */
    val notice: State<String?> = _notice

    private val _error = mutableStateOf<String?>(null)
    /** Message when there is nothing to show at all. */
    val error: State<String?> = _error

    private var refreshJob: Job? = null
    private var refreshVersion = 0L

    val isOnline: State<Boolean> = trackConnectivity(
        connectivity,
        onDisconnected = { _stats.value?.let { _notice.value = offlineNotice(it) } },
        onReconnected = { refresh(force = true) }
    )

    init {
        trackFeature(FEATURE)
        refresh()
    }

    /**
     * Loads the stats. Without [force], fresh ones in memory are reused;
     * [force] (refresh button, reconnection) always asks the server.
     */
    fun refresh(force: Boolean = false) {
        val uid = currentUserId() ?: return
        if (!force) {
            statsCache.get(uid)?.let {
                _stats.value = it
                _notice.value = null
                _error.value = null
                return
            }
        }

        val version = ++refreshVersion
        refreshJob?.cancel()
        _isLoading.value = true

        refreshJob = viewModelScope.launch {
            try {
                // Cache then network: show what the device has right away.
                if (_stats.value == null) {
                    statsStore.get(uid)?.let { _stats.value = it }
                }
                currentCoroutineContext().ensureActive()

                if (!isOnline.value) {
                    showSaved(offline = true)
                    return@launch
                }

                val fresh = withTimeout(TIMEOUT_MILLIS) { computeFresh(uid) }
                currentCoroutineContext().ensureActive()
                _stats.value = fresh
                _notice.value = null
                _error.value = null
                statsCache.put(uid, fresh)
                statsStore.save(uid, fresh)
            } catch (timeout: TimeoutCancellationException) {
                showSaved(offline = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                showSaved(offline = false)
            } finally {
                if (version == refreshVersion) _isLoading.value = false
            }
        }
    }

    private fun showSaved(offline: Boolean) {
        val saved = _stats.value
        if (saved == null) {
            _notice.value = null
            _error.value = if (offline) NO_DATA_OFFLINE else NO_DATA_ERROR
        } else {
            _error.value = null
            _notice.value = if (offline) offlineNotice(saved) else refreshFailedNotice(saved)
        }
    }

    private suspend fun computeFresh(uid: String): UserStats = coroutineScope {
        val meetings = async { meetingRepository.getMeetingsForUser(uid).getOrThrow() }
        // Optional section: if it fails, keep the last trending places shown.
        val trending = async { statsRepository.getTrendingPlaces().getOrNull() }

        val now = clock()
        val loaded = meetings.await()
        val counts = withContext(computeDispatcher) {
            UserStatsCalculator.compute(uid, loaded, now, zone)
        }

        // At most TOP_N + TOP_N reads, all at the same time (user reads hit
        // the shared UserCache first).
        val friends = counts.topFriends.map { (friendUid, count) ->
            async { RankedItem(friendUid, friendName(friendUid), count) }
        }
        val places = counts.topPlaces.map { (placeId, count) ->
            async { RankedItem(placeId, placeName(placeId, counts.placeMeeting[placeId]), count) }
        }

        UserStats(
            attended = counts.attended,
            upcoming = counts.upcoming,
            canceled = counts.canceled,
            votesCast = counts.votesCast,
            decidedWithVote = counts.decidedWithVote,
            wonVotes = counts.wonVotes,
            topFriends = friends.awaitAll(),
            topPlaces = places.awaitAll(),
            favoriteActivity = counts.favoriteActivity,
            perMonth = counts.perMonth,
            trendingPlaces = trending.await() ?: _stats.value?.trendingPlaces.orEmpty(),
            computedAt = now
        )
    }

    private suspend fun friendName(uid: String): String =
        userRepository.getUser(uid).getOrNull()?.username?.takeIf { it.isNotBlank() } ?: UNKNOWN_FRIEND

    /** places/{id}, or the meeting's custom place (added during voting). */
    private suspend fun placeName(placeId: String, meetingId: String?): String {
        placeRepository.getPlace(placeId).getOrNull()?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (meetingId != null) {
            meetingRepository.getCustomPlace(meetingId, placeId).getOrNull()?.name
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return UNKNOWN_PLACE
    }

    private fun offlineNotice(stats: UserStats) =
        "You're offline. Showing your stats saved at ${timeLabel(stats.computedAt)}."

    private fun refreshFailedNotice(stats: UserStats) =
        "Couldn't refresh your stats. Showing the ones saved at ${timeLabel(stats.computedAt)}."

    internal companion object {
        const val FEATURE = "STATS"
        const val TIMEOUT_MILLIS = 12_000L
        const val UNKNOWN_FRIEND = "A friend"
        const val UNKNOWN_PLACE = "A place"
        const val NO_DATA_OFFLINE =
            "You're offline. Your stats need internet the first time; they'll load by themselves when you connect."
        const val NO_DATA_ERROR = "Couldn't load your stats. Check your connection and try again."
    }
}
