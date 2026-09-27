package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.domain.User
import kotlinx.coroutines.launch

/**
 * Loads the signed-in user's profile from Firestore (users/{uid}) so the
 * Profile screen shows real data instead of sample data.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _profile = mutableStateOf<User?>(null)
    val profile: State<User?> = _profile

    init {
        val uid = authRepository.currentUserId
        if (uid != null) {
            viewModelScope.launch {
                userRepository.getUser(uid).onSuccess { _profile.value = it }
            }
        }
    }

    /** Signs out of Firebase Auth; the caller still navigates to Welcome. */
    fun onLogout() {
        authRepository.logout()
    }

    // Not wired to a backend yet: these are prototype placeholders, same as
    // the "Support"/"Attend" buttons elsewhere in the app. Deleting the
    // account for real also needs a confirmation dialog first.
    fun onShareCodeClick() {}
    fun onPrivacyClick() {}
    fun onLocationClick() {}
    fun onNotificationClick() {}
    fun onHelpUsGrowClick() {}
    fun onSupportClick() {}
}
