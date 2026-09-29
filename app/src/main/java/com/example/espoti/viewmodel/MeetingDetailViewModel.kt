package com.example.espoti.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.espoti.data.repository.MeetingRepository
import com.example.espoti.model.Meeting

/**
 * Loads the meeting whose id comes in the navigation route argument
 * (`meeting_detail/{meetingId}`), which arrives through [SavedStateHandle].
 */
class MeetingDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: MeetingRepository = MeetingRepository()
) : ViewModel() {

    val meeting: Meeting? = repository.getMeetingById(
        savedStateHandle.get<String>("meetingId")?.toIntOrNull()
    )
}
