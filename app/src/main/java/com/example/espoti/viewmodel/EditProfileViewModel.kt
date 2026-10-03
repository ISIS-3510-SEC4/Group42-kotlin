package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.NoticeType
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User
import kotlinx.coroutines.launch

/**
 * Loads the signed-in user's profile and saves edits back to Firestore
 * (users/{uid}). Email is shown but not editable here: changing it needs
 * Firebase Auth re-authentication, which this form doesn't collect.
 */
class EditProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private var loadedUser: User? = null

    private val _username = mutableStateOf("")
    val username: State<String> = _username

    private val _usernameError = mutableStateOf(false)
    val usernameError: State<Boolean> = _usernameError

    private val _noticeMessage = mutableStateOf<String?>(null)
    val noticeMessage: State<String?> = _noticeMessage

    private val _noticeType = mutableStateOf(NoticeType.ERROR)
    val noticeType: State<NoticeType> = _noticeType

    private val _email = mutableStateOf("")
    val email: State<String> = _email

    private val _preferences = mutableStateOf("")
    val preferences: State<String> = _preferences

    private val _description = mutableStateOf("")
    val description: State<String> = _description

    private val _isSaving = mutableStateOf(false)
    val isSaving: State<Boolean> = _isSaving

    init {
        val uid = authRepository.currentUserId
        if (uid != null) {
            viewModelScope.launch {
                userRepository.getUser(uid).onSuccess { user ->
                    if (user != null) {
                        loadedUser = user
                        _username.value = user.username
                        _email.value = user.email
                        _preferences.value = user.preferences.joinToString(", ") { it.displayName() }
                        _description.value = user.description
                    }
                }
            }
        }
    }

    fun onUsernameChange(value: String) {
        _username.value = value
        _usernameError.value = false
    }
    fun onPreferencesChange(value: String) { _preferences.value = value }
    fun onDescriptionChange(value: String) { _description.value = value }

    fun dismissNotice() {
        _noticeMessage.value = null
    }

    /** Saves the edited fields to Firestore; calls [onSuccess] only once that succeeds. */
    fun onSave(onSuccess: () -> Unit) {
        val uid = authRepository.currentUserId ?: return
        if (_isSaving.value) return

        val username = _username.value.trim()
        if (username.isBlank()) {
            _usernameError.value = true
            showNotice("Please enter a username.")
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            // Skip the uniqueness check entirely if the username wasn't
            // touched, so keeping your own name never flags itself as taken.
            if (!username.equals(loadedUser?.username, ignoreCase = true)) {
                val existing = userRepository.findByUsername(username).getOrElse {
                    _isSaving.value = false
                    showNotice("Something went wrong. Please try again.")
                    return@launch
                }
                if (existing != null && existing.id != uid) {
                    _usernameError.value = true
                    _isSaving.value = false
                    showNotice("That username is already taken.")
                    return@launch
                }
            }

            // Re-read so fields changed elsewhere (e.g. biometricEnabled, set by
            // BiometricSetupViewModel) are not overwritten by this stale copy.
            val current = userRepository.getUser(uid).getOrNull() ?: loadedUser ?: User(id = uid)
            val updated = current.copy(
                id = uid,
                username = username,
                description = _description.value.trim(),
                preferences = parsePreferences(_preferences.value)
            )

            userRepository.saveUser(updated)
                .onSuccess {
                    loadedUser = updated
                    _isSaving.value = false
                    onSuccess()
                }
                .onFailure {
                    _isSaving.value = false
                    showNotice("Couldn't save your profile. Please try again.")
                }
        }
    }

    private fun showNotice(message: String) {
        _noticeMessage.value = message
        _noticeType.value = NoticeType.ERROR
    }

    fun onChangePasswordClick() {}

    /** Free-text field, so entries that don't match a known activity are dropped. */
    private fun parsePreferences(text: String): List<ActivityType> =
        text.split(",")
            .mapNotNull { part -> ActivityType.entries.firstOrNull { it.name.equals(part.trim(), ignoreCase = true) } }
}

private fun ActivityType.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }
