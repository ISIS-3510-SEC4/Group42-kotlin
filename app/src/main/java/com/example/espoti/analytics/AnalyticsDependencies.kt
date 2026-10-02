package com.example.espoti.analytics

import android.os.SystemClock
import android.util.Log
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.FirestoreAnalyticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AnalyticsDependencies {

    private val authRepository by lazy {
        AuthRepository()
    }

    private val analyticsScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tracker: AnalyticsTracker by lazy {
        AnalyticsTracker(
            repository = FirestoreAnalyticsRepository(),
            scope = analyticsScope,
            currentUserId = {
                authRepository.currentUserId
            },
            monotonicNanos = {
                SystemClock.elapsedRealtimeNanos()
            },
            onStorageError = { error ->
                Log.w(
                    "EspotiAnalytics",
                    "Could not store analytics event",
                    error
                )
            }
        )
    }
}