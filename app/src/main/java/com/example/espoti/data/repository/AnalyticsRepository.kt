package com.example.espoti.data.repository

import com.example.espoti.model.analytics.AnalyticsEvent

interface AnalyticsRepository {
    suspend fun saveEvent(event: AnalyticsEvent): Result<Unit>
}