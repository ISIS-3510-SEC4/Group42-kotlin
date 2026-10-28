package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.repository.AccountRepository
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.di.AppContainer
import com.example.espoti.util.authErrorMessage
import com.example.espoti.util.isWrongCredentials
import kotlinx.coroutines.launch

// ============================================================================
// DELETE ACCOUNT VIEW MODEL (Profile > Delete account)
// ----------------------------------------------------------------------------
// The user confirms in a dialog by typing their password. Then, in order:
//   1. re-check the password (Firebase only deletes accounts right after a login)
//   2. delete users/{uid} + all their friendships in Firestore (AccountRepository)
//   3. delete the Firebase Auth account
//   4. forget everything saved on this device for that user, then sign out
// Each step stops the flow on failure with a message saying what happened.
// It needs internet (offline the Firestore deletes would only be queued), so
// offline the dialog says why and the message clears itself on reconnection.
// ============================================================================
class DeleteAccountViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val accountRepository: AccountRepository = AccountRepository(),
    connectivity: ConnectivityObserver = AppContainer.connectivity,
    private val forgetLocalData: (String) -> Unit = AppContainer::forgetDeletedAccount
) : ViewModel() {

    private val _showDialog = mutableStateOf(false)
    val showDialog: State<Boolean> = _showDialog

    private val _isDeleting = mutableStateOf(false)
    val isDeleting: State<Boolean> = _isDeleting

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val isOnline: State<Boolean> = trackConnectivity(connectivity) {
        if (_error.value == OfflineMessages.DELETE_ACCOUNT) _error.value = null
    }

    fun onDeleteClick() {
        _error.value = if (isOnline.value) null else OfflineMessages.DELETE_ACCOUNT
        _showDialog.value = true
    }

    /** Closing the dialog is ignored while the deletion is running. */
    fun onDismiss() {
        if (_isDeleting.value) return
        _showDialog.value = false
        _error.value = null
    }

    /** Runs the deletion; [onDeleted] (go to Welcome) only when every step succeeded. */
    fun onConfirm(password: String, onDeleted: () -> Unit) {
        val uid = authRepository.currentUserId ?: return
        if (_isDeleting.value) return
        if (password.isBlank()) {
            _error.value = "Please enter your password."
            return
        }
        if (!isOnline.value) {
            _error.value = OfflineMessages.DELETE_ACCOUNT
            return
        }

        _isDeleting.value = true
        _error.value = null
        viewModelScope.launch {
            val error = deleteEverything(uid, password)
            _isDeleting.value = false
            if (error != null) {
                _error.value = error
                return@launch
            }
            forgetLocalData(uid)
            authRepository.logout()
            _showDialog.value = false
            onDeleted()
        }
    }

    /** Returns null on success, or the message for the step that failed. */
    private suspend fun deleteEverything(uid: String, password: String): String? {
        authRepository.reauthenticate(password).onFailure {
            return if (isWrongCredentials(it)) "Incorrect password." else authErrorMessage(it)
        }
        accountRepository.deleteUserData(uid).onFailure {
            return "Couldn't delete your data. Please try again."
        }
        authRepository.deleteCurrentUser().onFailure {
            // The data is already gone; trying again deletes nothing twice.
            return "Your data was deleted, but your account couldn't be closed. Please try again."
        }
        return null
    }
}
