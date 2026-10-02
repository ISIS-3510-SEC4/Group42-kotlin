package com.example.espoti.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.espoti.data.repository.MeetingContextRepository
import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingContext
import kotlinx.coroutines.CancellationException

open class MeetingContextViewModel(
    private val contextRepository: MeetingContextRepository?
) : ViewModel() {

    var contexts by mutableStateOf<Map<Int, MeetingContext>>(emptyMap())
        private set

    var locationMessage by mutableStateOf(
        "Enable location for travel status and check-in."
    )
        private set

    var loading by mutableStateOf(false)
        private set

    suspend fun refreshContext(
        meetings: List<Meeting>,
        checkIn: Meeting? = null
    ) {
        if (loading) return

        val repository = contextRepository ?: return
        val previous = contexts

        loading = true
        contexts = emptyMap()

        try {
            contexts = if (checkIn == null) {
                repository.refresh(meetings)
            } else {
                val arrival = repository.checkIn(checkIn)
                previous + (checkIn.id to arrival)
            }

            locationMessage =
                "Approximate walking estimate; no routes or traffic. " +
                        "Check-in needs precise location."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            locationMessage =
                error.message ?: "Location unavailable. Try again."
        } finally {
            loading = false
        }
    }
}