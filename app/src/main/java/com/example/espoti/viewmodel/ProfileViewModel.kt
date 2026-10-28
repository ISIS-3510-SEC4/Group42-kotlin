package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.PendingActionStore
import com.example.espoti.data.local.ProfileLocalStore
import com.example.espoti.data.local.withPending
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.di.AppContainer
import com.example.espoti.model.domain.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Loads the signed-in user's profile from Firestore (users/{uid}) so the
 * Profile screen shows real data instead of sample data.
 *
 * Sprint 3 (eventual connectivity, "cache then network"): the profile saved
 * in SQLite is shown first and replaced by Firestore's when online. Changes
 * saved offline in Edit profile and not sent yet are applied on top, and
 * [hasPendingSync] lets the screen say they will sync later.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val profileStore: ProfileLocalStore = AppContainer.profileStore,
    private val pendingActions: PendingActionStore = AppContainer.pendingActions,
    connectivity: ConnectivityObserver = AppContainer.connectivity,
    private val clearLocalData: (String) -> Unit = AppContainer::clearUserData
) : ViewModel() {

    private val _profile = mutableStateOf<User?>(null)
    val profile: State<User?> = _profile

    private val _hasPendingSync = mutableStateOf(false)
    val hasPendingSync: State<Boolean> = _hasPendingSync

    private var loadJob: Job? = null

    val isOnline: State<Boolean> = trackConnectivity(connectivity, onReconnected = { refresh() })

    init {
        refresh()
    }

    /** Called on start and every time the Profile screen is shown again (e.g. back from Edit profile). */
    fun refresh() {
        val uid = authRepository.currentUserId ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val pending = pendingActions.pendingFor(uid)
            _hasPendingSync.value = pending.isNotEmpty()

            profileStore.get(uid)?.let { _profile.value = it.withPending(pending) }
            if (!isOnline.value) return@launch

            userRepository.getUser(uid).onSuccess { user ->
                if (user != null) {
                    profileStore.save(user)
                    // Re-read: the background sync may have sent them meanwhile.
                    val stillPending = pendingActions.pendingFor(uid)
                    _hasPendingSync.value = stillPending.isNotEmpty()
                    _profile.value = user.withPending(stillPending)
                }
            }
        }
    }

    /** Signs out of Firebase Auth and forgets this user's saved data; the caller still navigates to Welcome. */
    fun onLogout() {
        authRepository.currentUserId?.let(clearLocalData)
        authRepository.logout()
    }

    /** The share sheet is opened by the screen (util/ShareCode.kt); this only records the use. */
    fun onShareCodeClick() = AnalyticsDependencies.tracker.featureUsed(feature = "SHARE_CODE")

    // Not wired to a backend yet: these are prototype placeholders, same as
    // the "Support"/"Attend" buttons elsewhere in the app. Deleting the
    // account for real also needs a confirmation dialog first.
    fun onPrivacyClick() {}
    fun onLocationClick() {}
    fun onNotificationClick() {}
    fun onHelpUsGrowClick() {}
    fun onSupportClick() {}
}
