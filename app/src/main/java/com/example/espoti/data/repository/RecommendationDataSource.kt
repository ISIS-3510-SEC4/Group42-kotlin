package com.example.espoti.data.repository

import com.example.espoti.model.domain.Recommendation

interface RecommendationDataSource {
    val source: String

    suspend fun getRecommendations(
        meetingId: String?
    ): Result<List<Recommendation>>
}