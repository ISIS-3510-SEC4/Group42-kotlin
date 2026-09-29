package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.espoti.data.repository.MeetingRepository
import com.example.espoti.model.Meeting
import com.example.espoti.model.MeetingTab

class MeetingsViewModel(
    repository: MeetingRepository = MeetingRepository()
) : ViewModel() {

    val meetings: List<Meeting> = repository.getMeetings()

    private val _selectedTab = mutableStateOf(MeetingTab.UPCOMING)
    val selectedTab: State<MeetingTab> = _selectedTab

    fun onTabSelected(tab: MeetingTab) {
        _selectedTab.value = tab
    }
}
