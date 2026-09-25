package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/**
 * State of the create-meeting flow. It is shared by CreateMeeting1 and
 * CreateMeeting2 (both get the same instance from navigation), so what the
 * user typed in step 1 is still available in step 2.
 */
class CreateMeetingViewModel : ViewModel() {

    private val _whatToDo = mutableStateOf("")
    val whatToDo: State<String> = _whatToDo

    private val _whatDay = mutableStateOf("")
    val whatDay: State<String> = _whatDay

    private val _whatTime = mutableStateOf("")
    val whatTime: State<String> = _whatTime

    private val _selectedRestaurant = mutableStateOf<String?>(null)
    val selectedRestaurant: State<String?> = _selectedRestaurant

    fun onWhatToDoChange(value: String) { _whatToDo.value = value }
    fun onWhatDayChange(value: String) { _whatDay.value = value }
    fun onWhatTimeChange(value: String) { _whatTime.value = value }
    fun onRestaurantSelected(name: String) { _selectedRestaurant.value = name }
}
