package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.analytics.AnalyticsTracker
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.FirestoreRecommendationDataSource
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.PineconeRecommendationDataSource
import com.example.espoti.data.repository.RecommendationDataSource
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.analytics.AnalyticsEventType
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.Recommendation
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.User
import com.example.espoti.model.domain.MeetingLifecycle.startsAt
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.LocalDate
import java.time.LocalTime
/**
 * State of the create-meeting flow. It is shared by CreateMeeting1 and
 * CreateMeeting2 (both get the same instance from navigation), so what the
 * user typed in step 1 is still available in step 2.
 */

data class RecommendationUiState(
    val requestId: String? = null,
    val isLoading: Boolean = false,
    val recommendations: List<Recommendation> = emptyList(),
    val error: String? = null
)
class CreateMeetingViewModel(
    private val recommendationRepository: RecommendationDataSource =
        PineconeRecommendationDataSource(fallback = FirestoreRecommendationDataSource()),
    private val analyticsTracker: AnalyticsTracker =
        AnalyticsDependencies.tracker,
    // Lazy so building the ViewModel does not touch Firestore until Vote.
    meetingRepositoryProvider: () -> MeetingEventRepository = { MeetingEventRepository() },
    private val currentUserId: () -> String? = { AuthRepository().currentUserId },
    private val friendshipRepository: FriendshipRepository = FriendshipRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val meetingRepository by lazy(meetingRepositoryProvider)

    // "Who will go?": the user's accepted friends and the ones invited.
    private val _friends = mutableStateOf<List<User>>(emptyList())
    val friends: State<List<User>> = _friends

    private val _selectedFriendIds = mutableStateOf<Set<String>>(emptySet())
    val selectedFriendIds: State<Set<String>> = _selectedFriendIds

    // When the voting closes (epoch millis). Null = default, see votingDeadlineFor.
    private val _votingDeadlineMillis = mutableStateOf<Long?>(null)
    val votingDeadlineMillis: State<Long?> = _votingDeadlineMillis

    private val _votingDeadlineError = mutableStateOf<String?>(null)
    val votingDeadlineError: State<String?> = _votingDeadlineError

    // True while Vote is saving the meeting; the button is disabled meanwhile.
    private val _isSavingMeeting = mutableStateOf(false)
    val isSavingMeeting: State<Boolean> = _isSavingMeeting

    private val _saveMeetingError = mutableStateOf<String?>(null)
    val saveMeetingError: State<String?> = _saveMeetingError

    private val _whatToDo = mutableStateOf("")
    val whatToDo: State<String> = _whatToDo

    private val _selectedDate = mutableStateOf<LocalDate?>(null)
    val selectedDate: State<LocalDate?> = _selectedDate

    private val _selectedTime = mutableStateOf<LocalTime?>(null)
    val selectedTime: State<LocalTime?> = _selectedTime

    private val _timeError = mutableStateOf<String?>(null)
    val timeError: State<String?> = _timeError

    // placeId of the chosen recommendation (ids are unique, names may not be).
    private val _selectedPlaceId = mutableStateOf<String?>(null)
    val selectedPlaceId: State<String?> = _selectedPlaceId

    private val _selectedLatitude = mutableStateOf<Double?>(null)
    val selectedLatitude: State<Double?> = _selectedLatitude

    private val _selectedLongitude = mutableStateOf<Double?>(null)
    val selectedLongitude: State<Double?> = _selectedLongitude
    private val _recommendationState =
        mutableStateOf(RecommendationUiState())

    val recommendationState: State<RecommendationUiState> =
        _recommendationState

    private var activeTrace: AnalyticsTracker.RecommendationTrace? = null
    private var requestJob: Job? = null
    private var activeMeetingId: String? = null

    // PLANNING_FLOW_ABANDONED bookkeeping: the step the user reached, how many
    // places they were shown, and whether the flow ended with a saved meeting.
    private var currentStep = STEP_DETAILS
    private var lastRecommendationCount = 0
    private var flowCompleted = false

    init {
        loadFriends()
    }

    private fun loadFriends() {
        val uid = currentUserId() ?: return
        viewModelScope.launch {
            friendshipRepository.getAcceptedFriendIds(uid).onSuccess { ids ->
                _friends.value = ids
                    .mapNotNull { id -> userRepository.getUser(id).getOrNull() }
                    .sortedBy { it.username.lowercase() }
            }
        }
    }

    /** Adds or removes a friend from the invite list (bottom sheet checkboxes, avatar tap). */
    fun toggleFriend(friendId: String) {
        val current = _selectedFriendIds.value
        _selectedFriendIds.value = if (friendId in current) current - friendId else current + friendId
    }

    fun onWhatToDoChange(value: String) {
        _whatToDo.value = value
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
        // The chosen time may already be over on the new day (e.g. today).
        val start = meetingStart()
        if (_selectedTime.value != null && start != null && start <= clock()) {
            _selectedTime.value = null
            _timeError.value = "The time was reset: it has already passed on that day."
        }
        resetDeadlineIfAfterStart()
    }

    /**
     * Sets the meeting time. On the chosen day it must still be ahead;
     * otherwise it is rejected and [timeError] says why.
     */
    fun onTimeSelected(time: LocalTime) {
        val start = selectedDateMillis()?.let { startsAt(it, timeText(time)) }
        if (start != null && start <= clock()) {
            _timeError.value = "Pick a time later than now."
            return
        }
        _selectedTime.value = time
        _timeError.value = null
        resetDeadlineIfAfterStart()
    }

    /** A voting deadline after the (new) meeting start is no longer valid. */
    private fun resetDeadlineIfAfterStart() {
        val deadline = _votingDeadlineMillis.value
        val start = meetingStart()
        if (deadline != null && start != null && deadline > start) {
            _votingDeadlineMillis.value = null
            _votingDeadlineError.value = "The voting deadline was reset: it was after the meeting."
        }
    }

    /**
     * Sets when the voting closes. It must be in the future and not after the
     * meeting starts; otherwise it is rejected and [votingDeadlineError] says why.
     */
    fun onVotingDeadlineSelected(millis: Long) {
        val start = meetingStart()
        _votingDeadlineError.value = when {
            millis <= clock() -> "The voting deadline must be in the future."
            start != null && millis > start -> "The voting must close before the meeting starts."
            else -> null
        }
        if (_votingDeadlineError.value == null) _votingDeadlineMillis.value = millis
    }

    /** Chosen deadline, or by default 24 h from now (earlier if the meeting starts before). */
    internal fun votingDeadlineFor(now: Long): Long {
        _votingDeadlineMillis.value?.takeIf { it > now }?.let { return it }
        val dayLater = now + DEFAULT_VOTING_WINDOW_MILLIS
        val start = meetingStart()
        return if (start != null && start > now) minOf(dayLater, start) else dayLater
    }

    private fun meetingStart(): Long? =
        selectedDateMillis()?.let { startsAt(it, _selectedTime.value?.let(::timeText).orEmpty()) }

    /** The picked day as stored in MeetingEvent.date: UTC midnight (epoch millis). */
    private fun selectedDateMillis(): Long? =
        _selectedDate.value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    private var selectedRecommendation: Recommendation? = null
    private val viewedRecommendationIds = mutableSetOf<String>()

    fun onRestaurantSelected(recommendation: Recommendation) {
        selectedRecommendation = recommendation
        _selectedPlaceId.value = recommendation.placeId
    }

    /**
     * Called when the creator taps Vote. Creates meetings/{id} in VOTING with
     * the invited friends, the candidate places (the recommendations shown,
     * max [MAX_CANDIDATES], always including the voted one) and the voting
     * deadline; then stores the creator's vote and closes the voting right
     * away if nobody else has to vote. PLACE_SELECTED is tracked with the
     * meetingId for the "most selected meeting areas" BQ.
     * [onDone] gets the meeting id once it is stored (the screen then shows it
     * in Upcoming); on failure the user stays here and [saveMeetingError] says
     * why. Without a chosen place nothing happens (Vote is disabled anyway).
     */
    fun onVoteConfirmed(onDone: (String) -> Unit) {
        if (_isSavingMeeting.value) return
        val place = selectedRecommendation ?: return
        val uid = currentUserId() ?: run {
            _saveMeetingError.value = "You need to be signed in to create a meeting."
            return
        }

        _isSavingMeeting.value = true
        _saveMeetingError.value = null

        val now = clock()
        val candidates = (
            _recommendationState.value.recommendations.take(MAX_CANDIDATES).map { it.placeId } +
                place.placeId
            ).distinct()
        val meeting = MeetingEvent(
            creatorId = uid,
            participantIds = listOf(uid),
            invitedIds = _selectedFriendIds.value.toList(),
            candidatePlaceIds = candidates,
            date = selectedDateMillis() ?: 0L,
            time = _selectedTime.value?.let(::timeText).orEmpty(),
            activity = activityTypeFor(_whatToDo.value),
            status = StatusMeeting.VOTING,
            votingDeadline = votingDeadlineFor(now),
            createdAt = now
        )

        viewModelScope.launch {
            meetingRepository.createMeeting(meeting).fold(
                onSuccess = { meetingId ->
                    activeMeetingId = meetingId
                    flowCompleted = true
                    // If the vote fails the meeting still exists: the creator
                    // sees "Tap to vote" in Upcoming and can vote from there.
                    meetingRepository.castVote(meetingId, uid, place.placeId).onSuccess {
                        meetingRepository.closeVotingIfDone(meetingId, now)
                        analyticsTracker.placeSelected(
                            place = place,
                            meetingId = meetingId,
                            source = recommendationRepository.source,
                            metadata = mapOf(
                                "requestId" to _recommendationState.value.requestId,
                                "activity" to _whatToDo.value.ifBlank { null },
                                "invitedCount" to meeting.invitedIds.size
                            )
                        )
                        trackRejectedRecommendations(place)
                    }
                    _isSavingMeeting.value = false
                    onDone(meetingId)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _isSavingMeeting.value = false
                    _saveMeetingError.value = "Couldn't save the meeting. Please try again."
                }
            )
        }
    }

    /**
     * RECOMMENDATION_REJECTED for every recommendation the user saw but did not
     * vote for. FirestoreRecommendationDataSource ranks places with a high
     * rejection rate lower (RejectionAwareStrategy).
     */
    private fun trackRejectedRecommendations(place: Recommendation) {
        val recommendationState = _recommendationState.value
        val requestId = recommendationState.requestId

        viewedRecommendationIds
            .filter { rejectedPlaceId -> rejectedPlaceId != place.placeId }
            .forEach { rejectedPlaceId ->
                val rejectedPlace = recommendationState.recommendations
                    .firstOrNull { it.placeId == rejectedPlaceId }
                    ?: return@forEach

                analyticsTracker.trackEvent(
                    type = AnalyticsEventType.RECOMMENDATION_REJECTED,
                    meetingId = activeMeetingId,
                    placeId = rejectedPlace.placeId,
                    step = "VOTE",
                    metadata = mapOf(
                        "requestId" to requestId,
                        "source" to recommendationRepository.source,
                        "placeName" to rejectedPlace.name,
                        "selectedPlaceId" to place.placeId,
                        "selectedPlaceName" to place.name,
                        "category" to rejectedPlace.category,
                        "cityName" to rejectedPlace.cityName
                    )
                )
            }
    }


    fun onLocationSelected(latitude: Double, longitude: Double) {
        _selectedLatitude.value = latitude
        _selectedLongitude.value = longitude
    }

    fun requestRecommendations(meetingId: String? = null) {
        if (_recommendationState.value.isLoading) return

        activeMeetingId = meetingId
        currentStep = STEP_RECOMMENDATIONS

        analyticsTracker.cancelRecommendation(activeTrace)
        requestJob?.cancel()

        _selectedPlaceId.value = null
        selectedRecommendation = null
        viewedRecommendationIds.clear()

        val trace = analyticsTracker.recommendationRequested(
            meetingId = meetingId,
            source = recommendationRepository.source
        )
        analyticsTracker.featureUsed(
            feature = "RECOMMENDATIONS",
            meetingId = meetingId
        )

        activeTrace = trace

        // The UI still works when there is no authenticated analytics user.
        val requestId = trace?.requestId
            ?: UUID.randomUUID().toString()

        _recommendationState.value = RecommendationUiState(
            requestId = requestId,
            isLoading = true
        )

        requestJob = viewModelScope.launch {
            try {
                val result =
                    recommendationRepository.getRecommendations(meetingId)

                if (_recommendationState.value.requestId != requestId) {
                    return@launch
                }

                result.fold(
                    onSuccess = { recommendations ->
                        if (recommendations.isEmpty()) {
                            analyticsTracker.cancelRecommendation(trace)
                        }
                        lastRecommendationCount = recommendations.size

                        _recommendationState.value =
                            RecommendationUiState(
                                requestId = requestId,
                                recommendations = recommendations
                            )
                    },
                    onFailure = { error ->
                        if (error is CancellationException) {
                            throw error
                        }

                        analyticsTracker.cancelRecommendation(trace)

                        _recommendationState.value =
                            RecommendationUiState(
                                requestId = requestId,
                                error = "Couldn't load recommendations. Please try again."
                            )
                    }
                )
            } catch (cancelled: CancellationException) {
                analyticsTracker.cancelRecommendation(trace)
                throw cancelled
            } catch (error: Exception) {
                analyticsTracker.cancelRecommendation(trace)

                if (_recommendationState.value.requestId == requestId) {
                    _recommendationState.value =
                        RecommendationUiState(
                            requestId = requestId,
                            error = "Couldn't load recommendations. Please try again."
                        )
                }
            }
        }
    }

    fun ensureRecommendations() {
        if (_recommendationState.value.requestId == null) {
            requestRecommendations(activeMeetingId)
        }
    }

    fun retryRecommendations() {
        requestRecommendations(activeMeetingId)
    }

    fun onRecommendationsDisplayed(requestId: String) {
        val state = _recommendationState.value

        if (state.requestId != requestId) return
        if (state.isLoading || state.error != null) return
        if (state.recommendations.isEmpty()) return

        activeTrace?.let { trace ->
            analyticsTracker.recommendationDisplayed(
                trace = trace,
                resultCount = state.recommendations.size
            )
        }
    }

    fun cancelRecommendations() {
        analyticsTracker.cancelRecommendation(activeTrace)
        activeTrace = null

        requestJob?.cancel()
        requestJob = null

        _recommendationState.value = RecommendationUiState()
    }

    /**
     * The ViewModel is scoped to CreateMeeting1's back-stack entry, so it is
     * cleared exactly when the user leaves the create flow (back from either
     * screen or the bottom nav), not on rotation. If no meeting was saved by
     * then, the flow was abandoned. The tracker persists on its own scope, so
     * the event is stored even though viewModelScope is already cancelled.
     */
    override fun onCleared() {
        analyticsTracker.cancelRecommendation(activeTrace)
        if (!flowCompleted) {
            analyticsTracker.trackEvent(
                type = AnalyticsEventType.PLANNING_FLOW_ABANDONED,
                step = currentStep,
                metadata = mapOf(
                    "hadSelection" to (selectedRecommendation != null),
                    "hadDate" to (_selectedDate.value != null),
                    "invitedCount" to _selectedFriendIds.value.size,
                    "recommendationCount" to lastRecommendationCount,
                    "requestId" to _recommendationState.value.requestId,
                    "source" to recommendationRepository.source
                )
            )
        }
        super.onCleared()
    }

    /** Remembers which recommendations were shown, to report the rejected ones on Vote. */
    fun onRecommendationViewed(
        requestId: String,
        recommendation: Recommendation
    ) {
        val state = _recommendationState.value

        if (state.requestId != requestId) return
        if (state.isLoading || state.error != null) return

        viewedRecommendationIds.add(recommendation.placeId)
    }

    companion object {
        const val STEP_DETAILS = "DETAILS"
        const val STEP_RECOMMENDATIONS = "RECOMMENDATIONS"

        /** How many recommended places the participants vote on. */
        const val MAX_CANDIDATES = 5
        const val DEFAULT_VOTING_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
    }
}

/** The picked time as the stored "HH:mm" (MeetingEvent.time). */
internal fun timeText(time: LocalTime): String =
    String.format(Locale.US, "%02d:%02d", time.hour, time.minute)

/** "Wed, Oct 14, 2026". The DatePicker returns UTC midnight, so format in UTC. */
internal fun formatMeetingDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US))

/** Maps the free-text "What are we going to do?" to an [ActivityType] (OTHER if nothing matches). */
internal fun activityTypeFor(text: String): ActivityType {
    val normalized = text.uppercase(Locale.US)
    return ActivityType.entries.firstOrNull {
        it != ActivityType.OTHER && normalized.contains(it.name)
    } ?: ActivityType.OTHER
}
