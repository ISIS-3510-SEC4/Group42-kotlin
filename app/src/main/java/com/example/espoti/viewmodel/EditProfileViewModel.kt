package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.PendingAction
import com.example.espoti.data.local.PendingActionStore
import com.example.espoti.data.local.ProfileLocalStore
import com.example.espoti.data.local.withPending
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.data.sync.SyncScheduler
import com.example.espoti.di.AppContainer
import com.example.espoti.model.NoticeType
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Loads the signed-in user's profile and saves edits back to Firestore
 * (users/{uid}). Email is shown but not editable here: changing it needs
 * Firebase Auth re-authentication, which this form doesn't collect.
 *
 * Sprint 3 (eventual connectivity):
 * - Loading is "cache then network": the profile saved in SQLite first, then
 *   Firestore's when online (plus any change still waiting to be sent).
 * - Saving offline (or when Firestore doesn't answer in time) stores the
 *   description and preferences on the device, queues them in the outbox and
 *   asks WorkManager to send them once there is internet; the user is told it
 *   will sync later. A new username can't be saved offline: whether it's free
 *   can only be checked on the server.
 */
class EditProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val profileStore: ProfileLocalStore = AppContainer.profileStore,
    private val pendingActions: PendingActionStore = AppContainer.pendingActions,
    private val syncScheduler: SyncScheduler = AppContainer.syncScheduler,
    connectivity: ConnectivityObserver = AppContainer.connectivity,
    private val onProfileChanged: (String) -> Unit = { AppContainer.userCache.invalidate(it) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val saveTimeoutMillis: Long = SAVE_TIMEOUT_MILLIS
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

    val isOnline: State<Boolean> = trackConnectivity(connectivity)

    init {
        val uid = authRepository.currentUserId
        if (uid != null) {
            viewModelScope.launch {
                val pending = pendingActions.pendingFor(uid)
                profileStore.get(uid)?.let { show(it.withPending(pending)) }
                if (isOnline.value) {
                    userRepository.getUser(uid).onSuccess { user ->
                        if (user != null) {
                            profileStore.save(user)
                            show(user.withPending(pendingActions.pendingFor(uid)))
                        }
                    }
                }
            }
        }
    }

    private fun show(user: User) {
        loadedUser = user
        _username.value = user.username
        _email.value = user.email
        _preferences.value = user.preferences.joinToString(", ") { it.displayName() }
        _description.value = user.description
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

    /**
     * Saves the edited fields to Firestore and calls [onSuccess]. Offline, it
     * saves them on the device for a later sync and still calls [onSuccess]
     * (the Profile screen then shows that a sync is pending).
     */
    fun onSave(onSuccess: () -> Unit) {
        val uid = authRepository.currentUserId ?: return
        if (_isSaving.value) return

        val username = _username.value.trim()
        if (username.isBlank()) {
            _usernameError.value = true
            showNotice("Please enter a username.")
            return
        }
        val usernameChanged = !username.equals(loadedUser?.username, ignoreCase = true)

        if (!isOnline.value) {
            if (usernameChanged) {
                _usernameError.value = true
                showNotice(OfflineMessages.USERNAME_CHANGE)
                return
            }
            _isSaving.value = true
            viewModelScope.launch {
                saveOffline(uid)
                _isSaving.value = false
                onSuccess()
            }
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            // Skip the uniqueness check entirely if the username wasn't
            // touched, so keeping your own name never flags itself as taken.
            if (usernameChanged) {
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

            // A Firestore write only finishes when the server confirms it, so
            // on a network that drops mid-way it could wait forever.
            val saved = try {
                withTimeout(saveTimeoutMillis) { userRepository.saveUser(updated) }
            } catch (timeout: TimeoutCancellationException) {
                Result.failure(timeout)
            }

            saved
                .onSuccess {
                    loadedUser = updated
                    profileStore.save(updated)
                    // An older offline edit still in the outbox would overwrite
                    // this newer one when the background sync sends it.
                    pendingActions.pendingFor(uid)
                        .filter { it.action is PendingAction.UpdateProfile }
                        .forEach { pendingActions.remove(it.id) }
                    onProfileChanged(uid)
                    _isSaving.value = false
                    onSuccess()
                }
                .onFailure {
                    if (!usernameChanged) {
                        // Same fields can still be sent later: keep them as an offline change.
                        saveOffline(uid)
                        _isSaving.value = false
                        onSuccess()
                    } else {
                        _isSaving.value = false
                        showNotice("Couldn't save your profile. Please try again.")
                    }
                }
        }
    }

    /** Device first, server later: SQLite profile + outbox entry + background sync request. */
    private suspend fun saveOffline(uid: String) {
        val description = _description.value.trim()
        val preferences = parsePreferences(_preferences.value)
        val updated = (loadedUser ?: User(id = uid)).copy(
            id = uid,
            description = description,
            preferences = preferences
        )
        profileStore.save(updated)
        pendingActions.enqueue(
            PendingAction.UpdateProfile(
                ownerUid = uid,
                description = description,
                preferences = preferences,
                createdAt = clock()
            )
        )
        syncScheduler.scheduleSync()
        loadedUser = updated
        _noticeMessage.value = OfflineMessages.PROFILE_SAVED_OFFLINE
        _noticeType.value = NoticeType.INFO
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

    companion object {
        const val SAVE_TIMEOUT_MILLIS = 15_000L
    }
}

private fun ActivityType.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }
