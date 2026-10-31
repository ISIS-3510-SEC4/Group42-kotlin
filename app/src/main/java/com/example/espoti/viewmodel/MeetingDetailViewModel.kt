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
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import com.example.espoti.data.local.TransportFilterPreferences
import kotlinx.coroutines.CoroutineScope
import com.example.espoti.data.repository.CandidatePlacesLoader
import kotlinx.coroutines.Dispatchers

import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.Location
import com.example.espoti.model.domain.PlaceCategory
import com.example.espoti.model.domain.Rating
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
    private val clock: () -> Long = System::currentTimeMillis,
    connectivity: ConnectivityObserver = AppContainer.connectivity,
    private val filterPreferences: TransportFilterPreferences =
        AppContainer.transportFilterPreferences,
    private val persistenceScope: CoroutineScope = AppContainer.appScope
) : MeetingContextViewModel(contextRepository) {
    private val candidatePlacesLoader = CandidatePlacesLoader(
        placeRepository = placeRepository
    )
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

    private val _isAddingManualPlace = mutableStateOf(false)
    val isAddingManualPlace: State<Boolean> = _isAddingManualPlace

    private val _manualPlaceError = mutableStateOf<String?>(null)
    val manualPlaceError: State<String?> = _manualPlaceError

    private val _manualPlaceMessage = mutableStateOf<String?>(null)
    val manualPlaceMessage: State<String?> = _manualPlaceMessage

    private val _transportFilters = mutableStateOf(TransportFilters())
    val transportFilters: State<TransportFilters> = _transportFilters

    private val _visibleCandidates =
        mutableStateOf<List<Recommendation>>(emptyList())
    val visibleCandidates: State<List<Recommendation>> = _visibleCandidates

    private val _canUseTransportFilters = mutableStateOf(false)
    val canUseTransportFilters: State<Boolean> = _canUseTransportFilters

    private val _transportFilterError = mutableStateOf<String?>(null)
    val transportFilterError: State<String?> = _transportFilterError

    private var filterWriteJob: Job? = null
    private var filterPreferenceRestoreError: String? = null
    private var loadJob: Job? = null
    private var loadVersion = 0L

    private val _savedDataNotice = mutableStateOf<String?>(null)
    val savedDataNotice: State<String?> = _savedDataNotice

    // The organizer can skip the group voting and choose the place directly.
    private val _isCreator = mutableStateOf(false)
    val isCreator: State<Boolean> = _isCreator

    val isOnline: State<Boolean> = trackConnectivity(
        connectivity,
        onDisconnected = { load() },
        onReconnected = { load() }
    )
    init {
        load()
    }

    /** Loads the meeting again on entry and when connectivity changes. */
    fun load() {
        val id = meetingId
        val uid = currentUserId()

        if (id == null || uid == null) {
            _isLoading.value = false
            _error.value = "Meeting not found."
            return
        }

        val version = ++loadVersion
        loadJob?.cancel()

        _isLoading.value = true
        _error.value = null
        if (!_isSavingVote.value) {
            _voteError.value = null
        }

// UI state remains on Main; the loader switches its own work to IO.
        loadJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                restoreTransportFilters(uid)

                if (!isOnline.value) {
                    loadCachedDetail(id, uid)
                    return@launch
                }

                try {
                    withTimeout(12_000L) {
                        val found = meetingRepository
                            .getMeeting(id)
                            .getOrThrow()

                        currentCoroutineContext().ensureActive()

                        if (found == null) {
                            _meeting.value = null
                            _candidates.value = emptyList()
                            _visibleCandidates.value = emptyList()
                            _voteCounts.value = emptyMap()
                            _selectedPlaceId.value = null
                            _transportFilters.value = TransportFilters()
                            _canUseTransportFilters.value = false
                            _transportFilterError.value = null
                            _savedDataNotice.value = null
                            _error.value = "Meeting not found."
                            return@withTimeout
                        }

                        val now = clock()

                        val decided = if (
                            VotingRules.isVotingDone(
                                found,
                                found.voteList(),
                                now
                            )
                        ) {
                            meetingRepository
                                .closeVotingIfDone(id, now)
                                .getOrNull()
                                ?: found
                        } else {
                            found
                        }

                        currentCoroutineContext().ensureActive()

                        val updated = meetingRepository
                            .markPreviousIfFinished(decided, now)

                        currentCoroutineContext().ensureActive()

                        show(
                            event = updated,
                            uid = uid,
                            now = now,
                            allowRemote = true
                        )
                    }
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    loadCachedDetail(id, uid)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    loadCachedDetail(id, uid)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()

                _error.value =
                    "Couldn't load the meeting from this device. Connect and try again."
            } finally {
                if (version == loadVersion) {
                    _isLoading.value = false
                }
            }
        }
    }

    /** Offline reads never close voting or update the meeting in Firestore. */
    private suspend fun loadCachedDetail(
        id: String,
        uid: String
    ) {
        val found = meetingRepository
            .getCachedMeeting(id)
            .getOrThrow()

        currentCoroutineContext().ensureActive()

        if (found == null) {
            _error.value =
                "This meeting isn't available on this device. Open it with internet first."
            return
        }

        show(
            event = found,
            uid = uid,
            now = clock(),
            allowRemote = false
        )
    }

    /** Builds the detail using either network reads or local Firestore reads. */
    private suspend fun show(
        event: MeetingEvent,
        uid: String,
        now: Long,
        allowRemote: Boolean = true
    ) {
        val place = event.placeId?.let {
            readPlace(event.id, it, allowRemote)
        }

        val displayedMeeting = event.toUiMeeting(uid, place, now)

        val origin = event.startingLocations[uid]

        val validOrigin =
            origin != null &&
                    origin.latitude in -90.0..90.0 &&
                    origin.longitude in -180.0..180.0

        val loadedCandidates = when {
            !displayedMeeting.isVoting -> emptyList()

            allowRemote -> candidatePlacesLoader.load(
                event.candidatePlaceIds
            )

            else -> {
                // Local cache reads preserve the existing offline behavior.
                event.candidatePlaceIds.mapNotNull { placeId ->
                    readPlace(event.id, placeId, allowRemote = false)
                        ?.toRecommendation()
                }
            }
        }

        currentCoroutineContext().ensureActive()

        _meeting.value = displayedMeeting
        _isCreator.value = event.creatorId == uid

        if (displayedMeeting.isVoting) {
            _canUseTransportFilters.value = validOrigin
            _transportFilterError.value = filterPreferenceRestoreError

            if (!validOrigin) {
                _transportFilters.value = TransportFilters()
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

        _savedDataNotice.value = if (!allowRemote) {
            if (
                displayedMeeting.isVoting &&
                loadedCandidates.size < event.candidatePlaceIds.size
            ) {
                "Showing saved meeting data. Some candidate places aren't available on this device."
            } else {
                "Showing saved meeting data. Votes and availability may have changed."
            }
        } else {
            null
        }

        _error.value = null
    }

    /** Missing cached places do not prevent the rest of the detail from loading. */
    private suspend fun readPlace(
        meetingId: String,
        placeId: String,
        allowRemote: Boolean
    ): com.example.espoti.model.domain.Place? {

        val result = if (placeId.startsWith("custom_")) {
            if (allowRemote) {
                meetingRepository.getCustomPlace(meetingId, placeId)
            } else {
                meetingRepository.getCachedCustomPlace(meetingId, placeId)
            }
        } else {
            if (allowRemote) {
                placeRepository.getPlace(placeId)
            } else {
                placeRepository.getCachedPlace(placeId)
            }
        }

        currentCoroutineContext().ensureActive()

        return result.getOrNull()
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
        saveTransportFilters(filters)
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

    /**
     * Adds a manually selected place to the current meeting's voting candidates.
     * Custom places are stored only inside the meeting's customPlaces subcollection.
     */
    fun addManualPlace(
        name: String,
        latitude: Double,
        longitude: Double,
        address: String,
        cityName: String,
        category: PlaceCategory = PlaceCategory.RESTAURANT
    ) {
        val id = meetingId
        val uid = currentUserId()

        // Basic validation
        if (name.isBlank()) {
            _manualPlaceError.value = "Enter a name for the place."
            return
        }

        if (id == null || uid == null) {
            _manualPlaceError.value = "Couldn't identify the current meeting or user."
            return
        }

        if (_meeting.value?.isVoting != true) {
            _manualPlaceError.value = "This meeting is not accepting new places."
            return
        }

        if (!isOnline.value) {
            _manualPlaceError.value = "Connect to the internet to add a place."
            return
        }

        if (_isAddingManualPlace.value) return

        if (
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            _manualPlaceError.value = "Select a valid location."
            return
        }

        _isAddingManualPlace.value = true
        _manualPlaceError.value = null
        _manualPlaceMessage.value = null

        val customPlace = Place(
            id = "custom_${UUID.randomUUID()}",
            name = name.trim(),
            rating = Rating.THREE,
            distance = 0.0,
            placeImg = "",
            category = category,
            location = Location(
                latitude = latitude,
                longitude = longitude,
                address = address,
                cityName = cityName
            )
        )

        viewModelScope.launch {
            meetingRepository.addCustomPlaceToVoting(
                meetingId = id,
                uid = uid,
                place = customPlace
            ).fold(
                onSuccess = {
                    _manualPlaceMessage.value = "Place added to the voting candidates."
                    _isAddingManualPlace.value = false

                    // Reload the meeting and its complete candidate list.
                    load()
                },
                onFailure = { error ->
                    _isAddingManualPlace.value = false
                    _manualPlaceError.value =
                        error.message ?: "Couldn't add this place. Please try again."
                }
            )
        }
    }

    fun trackFeatureUsed(feature: String) {
        analyticsTracker.featureUsed(feature = feature)
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
        // Keep the selected candidate, but require a connection to confirm the vote.
        if (!isOnline.value) {
            _voteError.value =
                "You're offline. Your selection is kept on this screen. Reconnect to confirm your vote."
            return
        }
        _isSavingVote.value = true
        _voteError.value = null

        viewModelScope.launch {
            meetingRepository.castVote(id, uid, placeId).fold(
                onSuccess = {
                    meetingRepository.closeVotingIfDone(id, clock())
                    if (!placeId.startsWith("custom_")) {
                        _candidates.value.firstOrNull { it.placeId == placeId }?.let { place ->
                            analyticsTracker.placeSelected(
                                place,
                                meetingId = id,
                                source = "MEETING_VOTE"
                            )
                        }
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

    /**
     * Organizer only: chooses the selected candidate for the whole group and
     * ends the voting (MeetingEventRepository.chooseDirectly, decisionMethod
     * DIRECT). Same guards as [onVoteConfirmed]; [onDone] gets the meeting id.
     */
    fun onChooseDirectly(onDone: (String) -> Unit) {
        val id = meetingId ?: return
        val uid = currentUserId() ?: return
        val placeId = _selectedPlaceId.value ?: return
        if (_isSavingVote.value) return
        if (!_isCreator.value) {
            _voteError.value = "Only the organizer can choose the place for the group."
            return
        }
        if (_visibleCandidates.value.none { it.placeId == placeId }) {
            _voteError.value = "Select one of the visible places."
            return
        }
        if (!isOnline.value) {
            _voteError.value =
                "You're offline. Your selection is kept on this screen. Reconnect to choose the place."
            return
        }
        _isSavingVote.value = true
        _voteError.value = null

        viewModelScope.launch {
            meetingRepository.chooseDirectly(id, uid, placeId, clock()).fold(
                onSuccess = {
                    _candidates.value.firstOrNull { it.placeId == placeId }?.let { place ->
                        analyticsTracker.placeSelected(place, meetingId = id, source = "ORGANIZER_DIRECT")
                    }
                    _isSavingVote.value = false
                    onDone(id)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _isSavingVote.value = false
                    _voteError.value = (error as? IllegalArgumentException)?.message
                        ?: "Couldn't choose the place. Please try again."
                }
            )
        }
    }
    /**
     * Restoring preferences changes the initial UI state only.
     * It does not call applyTransportFilters or emit FILTER_USED events.
     */
    private suspend fun restoreTransportFilters(uid: String) {
        try {
            // Finish any earlier save before reading the settings again.
            filterWriteJob?.join()

            val restored = filterPreferences.read(uid)

            currentCoroutineContext().ensureActive()

            if (currentUserId() == uid) {
                _transportFilters.value = restored
                filterPreferenceRestoreError = null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            filterPreferenceRestoreError =
                "Couldn't restore your saved filter settings."
        }
    }

    /**
     * Save only explicit filter changes.
     * Writes are ordered and may finish after leaving the detail screen.
     */
    private fun saveTransportFilters(filters: TransportFilters) {
        val uid = currentUserId() ?: return
        val previous = filterWriteJob

        filterWriteJob = persistenceScope.launch {
            previous?.join()

            try {
                filterPreferences.save(uid, filters)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                viewModelScope.launch {
                    if (currentUserId() == uid) {
                        _transportFilterError.value =
                            "Filters applied, but couldn't save your settings on this device."
                    }
                }
            }
        }
    }
}

