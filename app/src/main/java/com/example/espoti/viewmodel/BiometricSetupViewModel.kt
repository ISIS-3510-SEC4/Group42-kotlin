package com.example.espoti.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.NoticeType
import com.example.espoti.security.BiometricAuthenticator
import com.example.espoti.security.BiometricCredentialStore
import com.example.espoti.util.authErrorMessage
import kotlinx.coroutines.launch
import javax.crypto.Cipher

// ============================================================================
// BIOMETRIC SETUP VIEW MODEL (Edit Profile)
// ----------------------------------------------------------------------------
// Enrolls / removes biometric login for the signed-in user. Enrollment steps:
//   1. User taps "Set up biometrics"  -> [onSetupClick] opens a password dialog
//   2. [onPasswordConfirmed] re-checks the password with Firebase and creates
//      a Keystore-bound Cipher, which the screen binds to the system prompt
//   3. [onEnrollAuthenticated] encrypts + stores the credentials locally and
//      mirrors users/{uid}.biometricEnabled = true in Firestore (the backend).
// The password is only kept in memory between steps 2 and 3.
// ============================================================================
class BiometricSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val store = BiometricCredentialStore(application)
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private val uid: String? get() = authRepository.currentUserId

    private val _isEnrolled = mutableStateOf(store.isEnrolledFor(uid))
    val isEnrolled: State<Boolean> = _isEnrolled

    private val _showPasswordDialog = mutableStateOf(false)
    val showPasswordDialog: State<Boolean> = _showPasswordDialog

    private val _isBusy = mutableStateOf(false)
    val isBusy: State<Boolean> = _isBusy

    private val _noticeMessage = mutableStateOf<String?>(null)
    val noticeMessage: State<String?> = _noticeMessage

    private val _noticeType = mutableStateOf(NoticeType.INFO)
    val noticeType: State<NoticeType> = _noticeType

    private var pendingEmail: String? = null
    private var pendingPassword: String? = null

    fun dismissNotice() {
        _noticeMessage.value = null
    }

    fun onSetupClick() {
        if (!BiometricAuthenticator.isAvailable(getApplication())) {
            showNotice("No fingerprint is set up on this device. Add one in your phone's settings first.", NoticeType.ERROR)
            return
        }
        _noticeMessage.value = null
        _showPasswordDialog.value = true
    }

    fun onPasswordDialogDismiss() {
        _showPasswordDialog.value = false
    }

    /**
     * Verifies [password] against Firebase, then calls [launchPrompt] with the
     * Cipher the screen must show the biometric prompt for.
     */
    fun onPasswordConfirmed(password: String, launchPrompt: (Cipher) -> Unit) {
        val currentUid = uid ?: return
        if (_isBusy.value) return
        if (password.isBlank()) {
            showNotice("Please enter your password.", NoticeType.ERROR)
            return
        }
        _isBusy.value = true
        viewModelScope.launch {
            val email = userRepository.getUser(currentUid).getOrNull()?.email
            if (email.isNullOrBlank()) {
                _isBusy.value = false
                showNotice("Something went wrong. Please try again.", NoticeType.ERROR)
                return@launch
            }
            val verified = authRepository.login(email, password)
            verified.onFailure {
                _isBusy.value = false
                showNotice(authErrorMessage(it), NoticeType.ERROR)
                return@launch
            }
            if (verified.getOrNull() != currentUid) {
                _isBusy.value = false
                showNotice("Something went wrong. Please try again.", NoticeType.ERROR)
                return@launch
            }

            val cipher = runCatching { store.createEnrollCipher() }.getOrNull()
            _isBusy.value = false
            if (cipher == null) {
                showNotice("This device couldn't create a secure key for biometrics.", NoticeType.ERROR)
                return@launch
            }
            pendingEmail = email
            pendingPassword = password
            _showPasswordDialog.value = false
            launchPrompt(cipher)
        }
    }

    fun onPromptError(message: String) {
        clearPending()
        store.clear()
        showNotice(message, NoticeType.ERROR)
    }

    /** Prompt succeeded: store the credentials locally and flag the account on the backend. */
    fun onEnrollAuthenticated(cipher: Cipher) {
        val currentUid = uid
        val email = pendingEmail
        val password = pendingPassword
        clearPending()
        if (currentUid == null || email == null || password == null) return

        _isBusy.value = true
        viewModelScope.launch {
            val saved = runCatching { store.saveCredentials(cipher, currentUid, email, password) }
            if (saved.isFailure) {
                store.clear()
                _isBusy.value = false
                showNotice("Couldn't save your biometric login. Please try again.", NoticeType.ERROR)
                return@launch
            }
            userRepository.setBiometricEnabled(currentUid, true)
                .onSuccess {
                    _isEnrolled.value = true
                    _isBusy.value = false
                    showNotice("Biometric login enabled.", NoticeType.INFO)
                }
                .onFailure {
                    // Keep local and backend consistent: no flag, no enrollment.
                    store.clear()
                    _isBusy.value = false
                    showNotice("Couldn't update your account. Please try again.", NoticeType.ERROR)
                }
        }
    }

    fun onDisableClick() {
        val currentUid = uid ?: return
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            userRepository.setBiometricEnabled(currentUid, false)
                .onSuccess {
                    store.clear()
                    _isEnrolled.value = false
                    _isBusy.value = false
                    showNotice("Biometric login disabled.", NoticeType.INFO)
                }
                .onFailure {
                    _isBusy.value = false
                    showNotice("Couldn't update your account. Please try again.", NoticeType.ERROR)
                }
        }
    }

    private fun clearPending() {
        pendingEmail = null
        pendingPassword = null
    }

    private fun showNotice(message: String, type: NoticeType) {
        _noticeMessage.value = message
        _noticeType.value = type
    }

    override fun onCleared() {
        clearPending()
        super.onCleared()
    }
}
