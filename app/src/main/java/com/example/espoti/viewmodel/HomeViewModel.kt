package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.PlaceRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.di.AppContainer
import com.example.espoti.model.HomeMeetingCard
import com.example.espoti.model.MeetingTab
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.MeetingLifecycle
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.VotingRules
import com.example.espoti.model.toUiMeeting
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

// ============================================================================
// HOME VIEW MODEL
// ----------------------------------------------------------------------------
// Home's data from Firestore (same source as the Meetings tabs):
//   - greeting with the signed-in user's username (users/{uid}, cached)
//   - "Next Meetings": VOTING + UPCOMING meetings, same order as Meetings
//   - "Meeting History": PREVIOUS meetings, newest first
// Like MeetingsViewModel, each [refresh] also closes finished votings and
// stores PREVIOUS for meetings that are over (there is no backend job).
// Offline, Firestore answers from its local cache; the screen refreshes by
// itself when the connection comes back.
// ============================================================================
class HomeViewModel(
    private val meetingRepository: MeetingEventRepository = MeetingEventRepository(),
    private val placeRepository: PlaceRepository = PlaceRepository(),
    private val userRepository: UserRepository = AppContainer.userRepository,
    private val currentUserId: () -> String? = { AuthRepository().currentUserId },
    connectivity: ConnectivityObserver = AppContainer.connectivity,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _userName = mutableStateOf<String?>(null)
    /** Username for "Hello, <name>"; null until loaded. */
    val userName: State<String?> = _userName

    private val _nextMeetings = mutableStateOf<List<HomeMeetingCard>>(emptyList())
    val nextMeetings: State<List<HomeMeetingCard>> = _nextMeetings

    private val _history = mutableStateOf<List<HomeMeetingCard>>(emptyList())
    val history: State<List<HomeMeetingCard>> = _history

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _hasLoaded = mutableStateOf(false)
    /** False until the first load ends, so the screen doesn't flash "no meetings". */
    val hasLoaded: State<Boolean> = _hasLoaded

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val placeCache = mutableMapOf<String, Place?>()

    private var refreshJob: Job? = null
    private var refreshVersion = 0L

    val isOnline: State<Boolean> = trackConnectivity(
        connectivity,
        onDisconnected = { refresh() },
        onReconnected = { refresh() }
    )

    /** Called every time Home is shown (and on reconnection). */
    fun refresh() {
        val uid = currentUserId() ?: return

        val version = ++refreshVersion
        refreshJob?.cancel()
        _isLoading.value = true

        refreshJob = viewModelScope.launch {
            try {
                if (!isOnline.value) {
                    loadCachedHome(uid)
                    return@launch
                }

                try {
                    withTimeout(12_000L) {
                        val name = async {
                            userRepository.getUser(uid).getOrNull()?.username
                        }

                        val events = meetingRepository
                            .getMeetingsForUser(uid)
                            .getOrThrow()

                        currentCoroutineContext().ensureActive()
                        showMeetings(uid, events)

                        val loadedName = name.await()
                        currentCoroutineContext().ensureActive()

                        loadedName?.takeIf { it.isNotBlank() }?.let {
                            _userName.value = it
                        }
                    }
                } catch (timeout: TimeoutCancellationException) {
                    loadCachedHome(uid)
                    _error.value =
                        "The update timed out. Showing available saved meetings."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    loadCachedHome(uid)
                    _error.value =
                        "Couldn't refresh your meetings. Showing available saved meetings."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _error.value =
                    "Couldn't load saved meetings. Connect to the internet and try again."
            } finally {
                if (version == refreshVersion) {
                    _isLoading.value = false
                    _hasLoaded.value = true
                }
            }
        }
    }

    private suspend fun loadCachedHome(uid: String) {
        val name = userRepository.getCachedUser(uid)
            .getOrNull()
            ?.username

        val events = meetingRepository
            .getCachedMeetingsForUser(uid)
            .getOrThrow()

        currentCoroutineContext().ensureActive()
        showMeetings(uid, events, allowRemote = false)

        name?.takeIf { it.isNotBlank() }?.let {
            _userName.value = it
        }

        if (_nextMeetings.value.isEmpty() && _history.value.isEmpty()) {
            _error.value =
                "No saved meetings are available for Home. Connect to update them."
        }
    }

    private suspend fun showMeetings(
        uid: String,
        events: List<MeetingEvent>,
        allowRemote: Boolean = true
    ) {
        val now = clock()

        val updated = if (allowRemote) {
            events
                .map { closeIfDone(it, now) }
                .map { meetingRepository.markPreviousIfFinished(it, now) }
        } else {
            events
        }

        currentCoroutineContext().ensureActive()

        val byId = updated.associateBy { it.id }

        val grouped = groupMeetings(
            updated.map { event ->
                val place = event.placeId?.let { placeId ->
                    placeFor(placeId, allowRemote)
                }

                event.toUiMeeting(uid, place, now)
            }
        )

        val nextCards = cards(
            meetings = grouped[MeetingTab.UPCOMING]
                .orEmpty()
                .take(MAX_CARDS)
                .map { byId.getValue(it.id) to it.name },
            uid = uid,
            now = now,
            allowRemote = allowRemote
        )

        val historyCards = cards(
            meetings = grouped[MeetingTab.PREVIOUS]
                .orEmpty()
                .take(MAX_CARDS)
                .map { byId.getValue(it.id) to it.name },
            uid = uid,
            now = now,
            allowRemote = allowRemote
        )

        currentCoroutineContext().ensureActive()

        _nextMeetings.value = nextCards
        _history.value = historyCards
        _error.value = null
    }

    /** Builds the cards; each card's first friend name is read in parallel. */
    private suspend fun cards(
        meetings: List<Pair<MeetingEvent, String>>,
        uid: String,
        now: Long,
        allowRemote: Boolean = true
    ): List<HomeMeetingCard> = coroutineScope {
        meetings.map { (event, title) ->
            async {
                val others = event.participantIds.filter { it != uid }
                val firstName = others.firstOrNull()?.let { friendUid ->
                    val result = if (allowRemote) {
                        userRepository.getUser(friendUid)
                    } else {
                        userRepository.getCachedUser(friendUid)
                    }
                    result.getOrNull()?.username
                }
                currentCoroutineContext().ensureActive()
                HomeMeetingCard(
                    id = event.id,
                    title = title,
                    whenLabel = whenLabel(event, now),
                    peopleLabel = peopleLabel(
                        firstName,
                        others.size,
                        event.invitedIds.size
                    ),
                    avatarCount = event.participantIds.size
                        .coerceIn(1, MAX_AVATARS),
                    isVoting = MeetingLifecycle.effectiveStatus(
                        event,
                        now,
                        zone
                    ) == StatusMeeting.VOTING
                )
            }
        }.awaitAll()
    }

    /** "Today, 3:00 PM", "Tomorrow", "Wed, Oct 29, 3:00 PM"; while voting, when it closes. */
    private fun whenLabel(event: MeetingEvent, now: Long): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (MeetingLifecycle.effectiveStatus(event, now, zone) == StatusMeeting.VOTING &&
            event.votingDeadline > 0
        ) {
            val closes = Instant.ofEpochMilli(event.votingDeadline).atZone(zone)
            return "Voting closes " + dayLabel(closes.toLocalDate(), today) +
                ", " + closes.toLocalTime().format(TIME_FORMAT)
        }
        // MeetingEvent.date is the DatePicker's UTC midnight of the picked day.
        val day = Instant.ofEpochMilli(event.date).atZone(ZoneOffset.UTC).toLocalDate()
        val time = MeetingLifecycle.parseMeetingTime(event.time)?.format(TIME_FORMAT)
        val dayText = dayLabel(day, today)
        return if (time != null) "$dayText, $time" else dayText
    }

    private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(if (day.year == today.year) DAY_FORMAT else DAY_YEAR_FORMAT)
    }

    private suspend fun closeIfDone(event: MeetingEvent, now: Long): MeetingEvent {
        if (!VotingRules.isVotingDone(event, event.voteList(), now)) return event
        return meetingRepository.closeVotingIfDone(event.id, now).getOrNull() ?: event
    }

    private suspend fun placeFor(
        placeId: String,
        allowRemote: Boolean = true
    ): Place? {
        placeCache[placeId]?.let { return it }
        val result = if (allowRemote) { placeRepository.getPlace(placeId) }
        else { placeRepository.getCachedPlace(placeId) }
        currentCoroutineContext().ensureActive()

        return result.getOrNull()?.also { place ->
            placeCache[placeId] = place
        }
    }
    private companion object {
        const val MAX_CARDS = 5
        const val MAX_AVATARS = 3
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
        val DAY_YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    }
}

/**
 * "Ana and 2 more", "Ana", "2 invited" (nobody accepted yet) or "Just you".
 * [othersCount] = participants other than the signed-in user.
 */
internal fun peopleLabel(firstName: String?, othersCount: Int, invitedCount: Int): String = when {
    othersCount == 0 && invitedCount > 0 -> "$invitedCount invited"
    othersCount == 0 -> "Just you"
    firstName == null -> if (othersCount == 1) "1 friend" else "$othersCount friends"
    othersCount == 1 -> firstName
    else -> "$firstName and ${othersCount - 1} more"
}
