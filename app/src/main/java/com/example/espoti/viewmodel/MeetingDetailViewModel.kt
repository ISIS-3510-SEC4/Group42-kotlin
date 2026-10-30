package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.analytics.AnalyticsTracker
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.MeetingContextRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.PlaceRepository
import com.example.espoti.model.Meeting
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Recommendation
import com.example.espoti.model.domain.VotingRules
import com.example.espoti.model.domain.toRecommendation
import com.example.espoti.model.toUiMeeting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.espoti.model.TransportFilters
import com.example.espoti.model.TransportFilterCalculator
import java.util.UUID
/**
 * One meeting from Firestore (route arg "meetingId"). While it is VOTING it
 * also exposes the candidate places with their vote counts, and lets the
 * user cast (or change) their vote. Opening it closes the voting if it is
 * already done, like MeetingsViewModel.refresh does.
 */
class MeetingDetailViewModel(
    savedStateHandle: SavedStateHandle,
    contextRepository: MeetingContextRepository? = null,
    private val meetingRepository: MeetingEventRepository = MeetingEventRepository(),
    private val placeRepository: PlaceRepository = PlaceRepository(),
    private val analyticsTracker: AnalyticsTracker = AnalyticsDependencies.tracker,
    private val currentUserId: () -> String? = { AuthRepository().currentUserId },
    private val clock: () -> Long = System::currentTimeMillis
) : MeetingContextViewModel(contextRepository) {

    private val meetingId: String? = savedStateHandle.get<String>("meetingId")

    private val _meeting = mutableStateOf<Meeting?>(null)
    val meeting: State<Meeting?> = _meeting

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    // VOTING only: the places everybody votes on, and how many votes each has.
    private val _candidates = mutableStateOf<List<Recommendation>>(emptyList())
    val candidates: State<List<Recommendation>> = _candidates

    private val _voteCounts = mutableStateOf<Map<String, Int>>(emptyMap())
    val voteCounts: State<Map<String, Int>> = _voteCounts

    // Starts as the user's current vote, if any.
    private val _selectedPlaceId = mutableStateOf<String?>(null)
    val selectedPlaceId: State<String?> = _selectedPlaceId

    private val _isSavingVote = mutableStateOf(false)
    val isSavingVote: State<Boolean> = _isSavingVote

    private val _voteError = mutableStateOf<String?>(null)
    val voteError: State<String?> = _voteError
    private val _transportFilters = mutableStateOf(TransportFilters())
    val transportFilters: State<TransportFilters> = _transportFilters

    private val _visibleCandidates =
        mutableStateOf<List<Recommendation>>(emptyList())
    val visibleCandidates: State<List<Recommendation>> = _visibleCandidates

    private val _canUseTransportFilters = mutableStateOf(false)
    val canUseTransportFilters: State<Boolean> = _canUseTransportFilters

    private val _transportFilterError = mutableStateOf<String?>(null)
    val transportFilterError: State<String?> = _transportFilterError
    init {
        load()
    }

    fun load() {
        val id = meetingId
        val uid = currentUserId()
        if (id == null || uid == null) {
            _isLoading.value = false
            _error.value = "Meeting not found."
            return
        }
        _isLoading.value = true

        viewModelScope.launch {
            val now = clock()
            meetingRepository.getMeeting(id)
                .onSuccess { found ->
                    if (found == null) {
                        _error.value = "Meeting not found."
                    } else {
                        val decided = if (VotingRules.isVotingDone(found, found.voteList(), now)) {
                            meetingRepository.closeVotingIfDone(id, now).getOrNull() ?: found
                        } else found
                        show(meetingRepository.markPreviousIfFinished(decided, now), uid, now)
                        _error.value = null
                    }
                }
                .onFailure { _error.value = "Couldn't load the meeting. Please try again." }
            _isLoading.value = false
        }
    }

    private suspend fun show(event: MeetingEvent, uid: String, now: Long) {
        val place = event.placeId?.let {
            placeRepository.getPlace(it).getOrNull()
        }

        val meeting = event.toUiMeeting(uid, place, now)
        _meeting.value = meeting

        if (meeting.isVoting) {
            val origin = event.startingLocations[uid]

            val validOrigin =
                origin != null &&
                        origin.latitude in -90.0..90.0 &&
                        origin.longitude in -180.0..180.0

            _canUseTransportFilters.value = validOrigin
            _transportFilterError.value = null

            if (!validOrigin) {
                _transportFilters.value = TransportFilters()
            }

            val loadedCandidates =
                event.candidatePlaceIds.mapNotNull { placeId ->
                    placeRepository.getPlace(placeId)
                        .getOrNull()
                        ?.toRecommendation()
                }

            _candidates.value = TransportFilterCalculator.withDistances(
                candidates = loadedCandidates,
                origin = origin.takeIf { validOrigin }
            )

            _voteCounts.value =
                event.votes.values.groupingBy { it }.eachCount()

            if (_selectedPlaceId.value == null) {
                _selectedPlaceId.value = event.votes[uid]
            }

            refreshVisibleCandidates()
        } else {
            _candidates.value = emptyList()
            _visibleCandidates.value = emptyList()
            _voteCounts.value = emptyMap()
            _selectedPlaceId.value = null
            _transportFilters.value = TransportFilters()
            _canUseTransportFilters.value = false
            _transportFilterError.value = null
        }
    }

    private fun refreshVisibleCandidates() {
        val visible = TransportFilterCalculator.apply(
            candidates = _candidates.value,
            filters = _transportFilters.value
        )

        _visibleCandidates.value = visible

        val selected = _selectedPlaceId.value

        if (
            selected != null &&
            visible.none { it.placeId == selected }
        ) {
            _selectedPlaceId.value = null
        }
    }

    fun applyTransportFilters(filters: TransportFilters) {
        if (_isSavingVote.value || _isLoading.value) return
        if (_meeting.value?.isVoting != true) return

        val id = meetingId ?: return

        if (filters.isActive && !_canUseTransportFilters.value) {
            _transportFilterError.value =
                "Your starting location is needed to use transport filters."
            return
        }

        _transportFilterError.value = null

        val previous = _transportFilters.value


        if (filters == previous) return

        _transportFilters.value = filters
        refreshVisibleCandidates()

        if (!filters.isActive) return

        val applicationId = UUID.randomUUID().toString()
        val resultCount = _visibleCandidates.value.size
        val candidateCount = _candidates.value.size

        val distance = filters.maxDistanceKm

        if (
            distance != null &&
            distance != previous.maxDistanceKm
        ) {
            analyticsTracker.transportFilterUsed(
                meetingId = id,
                applicationId = applicationId,
                filterName = "MAX_DISTANCE_KM",
                filterValue = distance,
                filterUnit = "km",
                resultCount = resultCount,
                candidateCount = candidateCount
            )
        }

        val walking = filters.maxWalkingMinutes

        if (
            walking != null &&
            walking != previous.maxWalkingMinutes
        ) {
            analyticsTracker.transportFilterUsed(
                meetingId = id,
                applicationId = applicationId,
                filterName = "MAX_WALKING_MINUTES",
                filterValue = walking,
                filterUnit = "minutes",
                resultCount = resultCount,
                candidateCount = candidateCount
            )
        }
    }

    fun clearTransportFilters() {
        applyTransportFilters(TransportFilters())
    }
    fun onPlaceSelected(placeId: String) {
        if (_isSavingVote.value) return

        if (_visibleCandidates.value.none { it.placeId == placeId }) {
            return
        }

        _selectedPlaceId.value = placeId
    }

    /**
     * Saves the vote, closes the voting if this was the last missing vote and
     * calls [onDone] with the meeting id (the screen then goes to Upcoming).
     */
    fun onVoteConfirmed(onDone: (String) -> Unit) {
        val id = meetingId ?: return
        val uid = currentUserId() ?: return
        val placeId = _selectedPlaceId.value ?: return
        if (_isSavingVote.value) return
        if (_visibleCandidates.value.none { it.placeId == placeId}) {
            _voteError.value = "Select one of the visible places."
            return
        }
        _isSavingVote.value = true
        _voteError.value = null

        viewModelScope.launch {
            meetingRepository.castVote(id, uid, placeId).fold(
                onSuccess = {
                    meetingRepository.closeVotingIfDone(id, clock())
                    _candidates.value.firstOrNull { it.placeId == placeId }?.let { place ->
                        analyticsTracker.placeSelected(place, meetingId = id, source = "MEETING_VOTE")
                    }
                    _isSavingVote.value = false
                    onDone(id)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _isSavingVote.value = false
                    _voteError.value = "Couldn't save your vote. Please try again."
                }
            )
        }
    }
}
