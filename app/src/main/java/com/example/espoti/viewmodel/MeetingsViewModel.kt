package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.MeetingContextRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.PlaceRepository
import com.example.espoti.di.AppContainer
import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingTab
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.VotingRules
import com.example.espoti.model.toUiMeeting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Meetings tabs backed by Firestore (meetings/ where the user is a
 * participant). Online, each refresh also closes finished voting and
 * stores PREVIOUS for meetings that are over.
 *
 * Offline, saved meetings and places are read explicitly from Firestore's
 * local cache. No remote status updates are attempted.
 * The screen supports browsing Previous and Canceled while offline.
 */
class MeetingsViewModel(
    contextRepository: MeetingContextRepository? = null,
    private val meetingRepository: MeetingEventRepository =
        MeetingEventRepository(),
    private val placeRepository: PlaceRepository = PlaceRepository(),
    private val currentUserId: () -> String? = {
        AuthRepository().currentUserId
    },
    private val clock: () -> Long = System::currentTimeMillis,
    connectivity: ConnectivityObserver = AppContainer.connectivity,
    private val refreshTimeoutMillis: Long = 12_000L
) : MeetingContextViewModel(contextRepository) {

    private val _meetings = mutableStateOf<List<Meeting>>(emptyList())
    val meetings: State<List<Meeting>> = _meetings

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _isShowingSaved = mutableStateOf(false)
    val isShowingSaved: State<Boolean> = _isShowingSaved

    private val _selectedTab = mutableStateOf(MeetingTab.UPCOMING)
    val selectedTab: State<MeetingTab> = _selectedTab

    // Meeting just created/voted: the screen scrolls to it and outlines it.
    private val _highlightedMeetingId = mutableStateOf<String?>(null)
    val highlightedMeetingId: State<String?> = _highlightedMeetingId

    private val placeCache = mutableMapOf<String, Place>()

    private var refreshJob: Job? = null
    private var refreshVersion = 0L

    val isOnline: State<Boolean> = trackConnectivity(
        connectivity,
        onDisconnected = { refresh() },
        onReconnected = { refresh() }
    )

    fun onTabSelected(tab: MeetingTab) {
        _selectedTab.value = tab
    }

    /** Shows Upcoming with [meetingId] outlined (called after voting). */
    fun highlight(meetingId: String) {
        _selectedTab.value = MeetingTab.UPCOMING
        _highlightedMeetingId.value = meetingId
    }

    fun clearHighlight() {
        _highlightedMeetingId.value = null
    }

    fun meetingsFor(tab: MeetingTab): List<Meeting> =
        groupMeetings(_meetings.value)[tab].orEmpty()

    /** Reloads on entry and whenever connectivity changes. */
    fun refresh() {
        val uid = currentUserId() ?: return

        val version = ++refreshVersion
        refreshJob?.cancel()

        _isLoading.value = true
        _error.value = null

        refreshJob = viewModelScope.launch {
            try {
                if (!isOnline.value) {
                    loadCachedMeetings(uid)
                    return@launch
                }

                try {
                    withTimeout(refreshTimeoutMillis) {
                        val events = meetingRepository
                            .getMeetingsForUser(uid)
                            .getOrThrow()

                        currentCoroutineContext().ensureActive()

                        showMeetings(
                            uid = uid,
                            events = events,
                            allowRemote = true
                        )
                    }
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    loadCachedMeetings(uid)
                    _error.value =
                        "The update timed out. Showing available saved meetings."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    loadCachedMeetings(uid)
                    _error.value =
                        "Couldn't refresh your meetings. Showing available saved meetings."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _error.value =
                    "Couldn't load your meetings. Connect and try again."
            } finally {
                if (version == refreshVersion) {
                    _isLoading.value = false
                }
            }
        }
    }

    /** Cache reads never close voting or update remote meeting statuses. */
    private suspend fun loadCachedMeetings(uid: String) {
        _isShowingSaved.value = true

        val events = meetingRepository
            .getCachedMeetingsForUser(uid)
            .getOrThrow()

        currentCoroutineContext().ensureActive()

        showMeetings(
            uid = uid,
            events = events,
            allowRemote = false
        )
    }

    private suspend fun showMeetings(
        uid: String,
        events: List<MeetingEvent>,
        allowRemote: Boolean
    ) {
        val now = clock()

        val displayed = events.map { event ->
            currentCoroutineContext().ensureActive()

            val updated = if (allowRemote) {
                val closed = closeIfDone(event, now)

                currentCoroutineContext().ensureActive()

                meetingRepository.markPreviousIfFinished(closed, now)
            } else {
                event
            }

            currentCoroutineContext().ensureActive()

            val place = updated.placeId?.let {
                placeFor(it, allowRemote)
            }

            updated.toUiMeeting(uid, place, now)
        }

        currentCoroutineContext().ensureActive()

        _meetings.value = displayed
        _isShowingSaved.value = !allowRemote
        _error.value = null
    }

    private suspend fun closeIfDone(
        event: MeetingEvent,
        now: Long
    ): MeetingEvent {
        if (!VotingRules.isVotingDone(event, event.voteList(), now)) {
            return event
        }

        return meetingRepository
            .closeVotingIfDone(event.id, now)
            .getOrNull()
            ?: event
    }

    private suspend fun placeFor(
        placeId: String,
        allowRemote: Boolean
    ): Place? {
        placeCache[placeId]?.let { return it }

        val result = if (allowRemote) {
            placeRepository.getPlace(placeId)
        } else {
            placeRepository.getCachedPlace(placeId)
        }

        currentCoroutineContext().ensureActive()

        return result.getOrNull()?.also {
            placeCache[placeId] = it
        }
    }
}

/**
 * Splits meetings into the three tabs by their effective status.
 * UPCOMING: voting ones first, then confirmed ones by start.
 * PREVIOUS: newest first.
 */
internal fun groupMeetings(
    meetings: List<Meeting>
): Map<MeetingTab, List<Meeting>> {
    val upcoming = meetings
        .filter {
            it.status == StatusMeeting.VOTING ||
                    it.status == StatusMeeting.UPCOMING
        }
        .sortedWith(
            compareByDescending<Meeting> { it.isVoting }.thenBy {
                (
                        if (it.isVoting) {
                            it.votingDeadline
                        } else {
                            it.startsAtEpochMillis
                        }
                        ) ?: Long.MAX_VALUE
            }
        )

    val previous = meetings
        .filter { it.status == StatusMeeting.PREVIOUS }
        .sortedByDescending { it.startsAtEpochMillis ?: 0L }

    val canceled = meetings.filter {
        it.status == StatusMeeting.CANCELED
    }

    return mapOf(
        MeetingTab.UPCOMING to upcoming,
        MeetingTab.PREVIOUS to previous,
        MeetingTab.CANCELED to canceled
    )
}