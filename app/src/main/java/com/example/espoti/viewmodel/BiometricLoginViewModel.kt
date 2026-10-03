package com.example.espoti.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.security.BiometricAuthenticator
import com.example.espoti.security.BiometricCredentialStore
import com.example.espoti.util.authErrorMessage
import com.example.espoti.util.isWrongCredentials
import kotlinx.coroutines.launch
import javax.crypto.Cipher

// ============================================================================
// BIOMETRIC LOGIN VIEW MODEL
// ----------------------------------------------------------------------------
// Shared by Welcome and Login (via BiometricLoginButton). It does NOT touch the
// UI: the screen shows the system prompt with the Cipher from [prepareCipher],
// and hands the unlocked Cipher back to [onAuthenticated], which decrypts the
// saved credentials and signs in with Firebase. AndroidViewModel because the
// store and BiometricManager need a Context.
// ============================================================================
class BiometricLoginViewModel(application: Application) : AndroidViewModel(application) {

    private val store = BiometricCredentialStore(application)
    private val authRepository = AuthRepository()

    private val _isAvailable = mutableStateOf(computeAvailable())
    /** True when this device has an enrolled account AND strong biometrics set up in system settings. */
    val isAvailable: State<Boolean> = _isAvailable

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    /** Re-evaluates availability (e.g. after returning from Edit Profile or system settings). */
    fun refresh() {
        _isAvailable.value = computeAvailable()
    }

    fun dismissError() {
        _error.value = null
    }

    /** Cipher to bind to the prompt, or null (with a message) if the enrollment is no longer usable. */
    fun prepareCipher(): Cipher? {
        if (_isLoading.value) return null
        _error.value = null
        val cipher = store.createDecryptCipher()
        if (cipher == null) {
            _isAvailable.value = false
            _error.value = "Your biometrics changed. Log in with your password and set them up again in Edit Profile."
        }
        return cipher
    }

    fun onPromptError(message: String) {
        _error.value = message
    }

    /** Decrypts the saved credentials with the unlocked [cipher] and signs in; [onSuccess] only if Firebase accepts them. */
    fun onAuthenticated(cipher: Cipher, onSuccess: () -> Unit) {
        val credentials = store.readCredentials(cipher)
        if (credentials == null) {
            _error.value = "Couldn't read your saved login. Log in with your password."
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            authRepository.login(credentials.email, credentials.password)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure {
                    _isLoading.value = false
                    if (isWrongCredentials(it)) {
                        // Password was changed elsewhere: the saved one is useless.
                        store.clear()
                        _isAvailable.value = false
                        _error.value = "Your password changed. Log in with it and set up biometrics again."
                    } else {
                        _error.value = authErrorMessage(it)
                    }
                }
        }
    }

    private fun computeAvailable(): Boolean =
        store.isEnrolled() && BiometricAuthenticator.isAvailable(getApplication())
}
