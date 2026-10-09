package com.example.espoti.data.repository

import com.example.espoti.model.domain.Recommendation
import kotlinx.coroutines.CancellationException

// =============================================================================
// Recommendations from the NestJS backend (Pinecone semantic search)
// =============================================================================
// Calls the backend through RecommendationRepository. If the backend is
// unreachable (server down, no network, Pinecone not indexed yet) it falls back
// to `fallback` so the create-meeting flow keeps working. `source` always names
// the data source that produced the last result, so analytics stay truthful.
// =============================================================================

class PineconeRecommendationDataSource(
    private val api: RecommendationRepository = RecommendationRepository(),
    private val fallback: RecommendationDataSource? = null,
    // Optional device location, used by the backend to re-rank by proximity.
    private val locationProvider: () -> Pair<Double, Double>? = { null }
) : RecommendationDataSource {

    override var source: String = "PINECONE"
        private set

    override suspend fun getRecommendations(
        meetingId: String?
    ): Result<List<Recommendation>> {
        val location = locationProvider()
        val lat = location?.first
        val lng = location?.second

        val result = if (meetingId != null) {
            api.forMeeting(meetingId, lat, lng)
        } else {
            api.forCurrentUser(lat, lng)
        }

        val failure = result.exceptionOrNull()
        if (failure is CancellationException) throw failure

        // Empty list also falls back: a brand-new index returns nothing useful.
        val usable = result.getOrNull()?.isNotEmpty() == true
        if (usable || fallback == null) {
            source = "PINECONE"
            return result
        }

        source = fallback.source
        return fallback.getRecommendations(meetingId)
    }
}
