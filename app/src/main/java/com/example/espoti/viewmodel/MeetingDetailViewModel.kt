package com.example.espoti.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.espoti.data.repository.MeetingContextRepository
import com.example.espoti.data.repository.MeetingRepository
import com.example.espoti.model.Meeting

class MeetingDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: MeetingRepository = MeetingRepository(),
    contextRepository: MeetingContextRepository? = null
) : MeetingContextViewModel(contextRepository) {

    val meeting: Meeting? = repository.getMeetingById(
        savedStateHandle.get<String>("meetingId")?.toIntOrNull()
    )
}