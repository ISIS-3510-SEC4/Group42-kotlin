package com.example.espoti.data.repository

import com.example.espoti.model.domain.Recommendation
import kotlinx.coroutines.CancellationException

class BackendRecommendationDataSource(
    private val repository: RecommendationRepository =
        RecommendationRepository()
) : RecommendationDataSource {

    override val source = "BACKEND"

    override suspend fun getRecommendations(
        meetingId: String?
    ): Result<List<Recommendation>> {
        val result = if (meetingId == null) {
            repository.forCurrentUser()
        } else {
            repository.forMeeting(meetingId)
        }

        // The existing repository uses runCatching.
        // Preserve coroutine cancellation if it returns it as a failure.
        val failure = result.exceptionOrNull()
        if (failure is CancellationException) {
            throw failure
        }

        return result
    }
}