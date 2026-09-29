package com.example.espoti.viewmodel

import androidx.lifecycle.ViewModel
import com.example.espoti.data.repository.MeetingRepository
import com.example.espoti.model.MeetingInvite

class HomeViewModel(
    repository: MeetingRepository = MeetingRepository()
) : ViewModel() {

    /** Cards shown in the "Next Meetings" row. */
    val upcomingInvites: List<MeetingInvite> = repository.getUpcomingInvites()
}
