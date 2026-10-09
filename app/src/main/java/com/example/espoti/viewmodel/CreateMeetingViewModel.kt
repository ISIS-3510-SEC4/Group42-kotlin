package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.analytics.AnalyticsTracker
import com.example.espoti.data.repository.FirestoreRecommendationDataSource
import com.example.espoti.data.repository.PineconeRecommendationDataSource
import com.example.espoti.data.repository.RecommendationDataSource
import com.example.espoti.model.analytics.AnalyticsEventType
import com.example.espoti.model.domain.Recommendation
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
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
        AnalyticsDependencies.tracker
) : ViewModel() {

    private val _whatToDo = mutableStateOf("")
    val whatToDo: State<String> = _whatToDo

    private val _whatDay = mutableStateOf("")
    val whatDay: State<String> = _whatDay

    private val _whatTime = mutableStateOf("")
    val whatTime: State<String> = _whatTime


    private val _selectedRestaurant = mutableStateOf<String?>(null)
    val selectedRestaurant: State<String?> = _selectedRestaurant

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

    fun onWhatToDoChange(value: String) {
        _whatToDo.value = value
    }

    fun onWhatDayChange(value: String) {
        _whatDay.value = value
    }

    fun onWhatTimeChange(value: String) {
        _whatTime.value = value
    }

    private var selectedRecommendation: Recommendation? = null

    fun onRestaurantSelected(recommendation: Recommendation) {
        selectedRecommendation = recommendation
        _selectedRestaurant.value = recommendation.name
    }

    /**
     * BQ "most selected meeting areas/locations": records the place the user
     * finally chose (PLACE_SELECTED) with its area, so analytics_results can
     * rank areas and places. Called once, when the user taps Vote.
     */
    fun onVoteConfirmed() {
        val place = selectedRecommendation ?: return
        val lat = place.latitude
        val lng = place.longitude
        analyticsTracker.trackEvent(
            type = AnalyticsEventType.PLACE_SELECTED,
            meetingId = activeMeetingId,
            placeId = place.placeId,
            step = "VOTE",
            metadata = mapOf(
                "requestId" to _recommendationState.value.requestId,
                "source" to recommendationRepository.source,
                "placeName" to place.name,
                "category" to place.category,
                "cityName" to place.cityName,
                "address" to place.address,
                "latitude" to lat,
                "longitude" to lng,
                // ~1.1 km grid cell (2 decimals): the "area" when a city is too coarse.
                "areaCell" to if (lat != null && lng != null) areaCell(lat, lng) else null,
                "activity" to _whatToDo.value.ifBlank { null }
            )
        )
    }

    fun onLocationSelected(latitude: Double, longitude: Double) {
        _selectedLatitude.value = latitude
        _selectedLongitude.value = longitude
    }

    fun requestRecommendations(meetingId: String? = null) {
        if (_recommendationState.value.isLoading) return

        activeMeetingId = meetingId

        analyticsTracker.cancelRecommendation(activeTrace)
        requestJob?.cancel()

        _selectedRestaurant.value = null
        selectedRecommendation = null

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

    override fun onCleared() {
        analyticsTracker.cancelRecommendation(activeTrace)
        super.onCleared()
    }
}

/** Rounds coordinates to 2 decimals ("4.65,-74.06"), a ~1.1 km cell used to group nearby places into one area. */
internal fun areaCell(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.2f,%.2f", latitude, longitude)
