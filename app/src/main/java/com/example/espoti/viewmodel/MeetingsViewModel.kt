package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.MeetingContextRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.PlaceRepository
import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingTab
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.VotingRules
import com.example.espoti.model.toUiMeeting
import kotlinx.coroutines.launch

/**
 * Meetings tabs backed by Firestore (meetings/ where the user is a
 * participant). Each [refresh] also closes VOTING meetings that are done
 * (everybody voted or the deadline passed) and stores PREVIOUS for meetings
 * that are over - there is no backend job for that, so whoever opens the app
 * first updates them.
 */
class MeetingsViewModel(
    contextRepository: MeetingContextRepository? = null,
    private val meetingRepository: MeetingEventRepository = MeetingEventRepository(),
    private val placeRepository: PlaceRepository = PlaceRepository(),
    private val currentUserId: () -> String? = { AuthRepository().currentUserId },
    private val clock: () -> Long = System::currentTimeMillis
) : MeetingContextViewModel(contextRepository) {

    private val _meetings = mutableStateOf<List<Meeting>>(emptyList())
    val meetings: State<List<Meeting>> = _meetings

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _selectedTab = mutableStateOf(MeetingTab.UPCOMING)
    val selectedTab: State<MeetingTab> = _selectedTab

    // Meeting just created/voted: the screen scrolls to it and outlines it.
    private val _highlightedMeetingId = mutableStateOf<String?>(null)
    val highlightedMeetingId: State<String?> = _highlightedMeetingId

    private val placeCache = mutableMapOf<String, Place?>()

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

    fun refresh() {
        val uid = currentUserId() ?: return
        if (_isLoading.value) return
        _isLoading.value = true

        viewModelScope.launch {
            meetingRepository.getMeetingsForUser(uid)
                .onSuccess { events ->
                    val now = clock()
                    _meetings.value = events
                        .map { event -> closeIfDone(event, now) }
                        .map { event -> meetingRepository.markPreviousIfFinished(event, now) }
                        .map { event -> event.toUiMeeting(uid, event.placeId?.let { placeFor(it) }, now) }
                    _error.value = null
                }
                .onFailure {
                    _error.value = "Couldn't load your meetings. Please try again."
                }
            _isLoading.value = false
        }
    }

    private suspend fun closeIfDone(event: MeetingEvent, now: Long): MeetingEvent {
        if (!VotingRules.isVotingDone(event, event.voteList(), now)) return event
        return meetingRepository.closeVotingIfDone(event.id, now).getOrNull() ?: event
    }

    private suspend fun placeFor(placeId: String): Place? =
        placeCache.getOrPut(placeId) { placeRepository.getPlace(placeId).getOrNull() }
}

/**
 * Splits meetings into the three tabs by their (effective) status - see
 * MeetingLifecycle, which already turns finished meetings into PREVIOUS.
 * UPCOMING: voting ones first (closing soonest first), then confirmed ones by
 * start. PREVIOUS: newest first.
 */
internal fun groupMeetings(meetings: List<Meeting>): Map<MeetingTab, List<Meeting>> {
    val upcoming = meetings
        .filter { it.status == StatusMeeting.VOTING || it.status == StatusMeeting.UPCOMING }
        .sortedWith(
            compareByDescending<Meeting> { it.isVoting }.thenBy {
                (if (it.isVoting) it.votingDeadline else it.startsAtEpochMillis) ?: Long.MAX_VALUE
            }
        )
    val previous = meetings
        .filter { it.status == StatusMeeting.PREVIOUS }
        .sortedByDescending { it.startsAtEpochMillis ?: 0L }
    val canceled = meetings.filter { it.status == StatusMeeting.CANCELED }

    return mapOf(
        MeetingTab.UPCOMING to upcoming,
        MeetingTab.PREVIOUS to previous,
        MeetingTab.CANCELED to canceled
    )
}
