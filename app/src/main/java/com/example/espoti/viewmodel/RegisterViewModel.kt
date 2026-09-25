package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.NoticeType
import com.example.espoti.model.domain.User
import com.example.espoti.util.EMAIL_EXTENSIONS_HINT
import com.example.espoti.util.MIN_PASSWORD_LENGTH
import com.example.espoti.util.authErrorMessage
import com.example.espoti.util.isValidEmail
import kotlinx.coroutines.launch

// ============================================================================
// REGISTER VIEW MODEL
// ----------------------------------------------------------------------------
// Owns the Register form state and its validation (same idea as
// LoginViewModel, with the "confirm" fields). After validation it creates the
// Firebase Auth account and then the users/{uid} profile document.
// ============================================================================
class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _email = mutableStateOf("")
    val email: State<String> = _email

    private val _confirmEmail = mutableStateOf("")
    val confirmEmail: State<String> = _confirmEmail

    private val _password = mutableStateOf("")
    val password: State<String> = _password

    private val _confirmPassword = mutableStateOf("")
    val confirmPassword: State<String> = _confirmPassword

    private val _emailError = mutableStateOf(false)
    val emailError: State<Boolean> = _emailError

    private val _confirmEmailError = mutableStateOf(false)
    val confirmEmailError: State<Boolean> = _confirmEmailError

    private val _passwordError = mutableStateOf(false)
    val passwordError: State<Boolean> = _passwordError

    private val _confirmPasswordError = mutableStateOf(false)
    val confirmPasswordError: State<Boolean> = _confirmPasswordError

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _noticeMessage = mutableStateOf<String?>(null)
    val noticeMessage: State<String?> = _noticeMessage

    private val _noticeType = mutableStateOf(NoticeType.ERROR)
    val noticeType: State<NoticeType> = _noticeType

    fun onEmailChange(value: String) {
        _email.value = value
        _emailError.value = false
    }

    fun onConfirmEmailChange(value: String) {
        _confirmEmail.value = value
        _confirmEmailError.value = false
    }

    fun onPasswordChange(value: String) {
        _password.value = value
        _passwordError.value = false
    }

    fun onConfirmPasswordChange(value: String) {
        _confirmPassword.value = value
        _confirmPasswordError.value = false
    }

    fun showNotice(message: String, type: NoticeType) {
        _noticeMessage.value = message
        _noticeType.value = type
    }

    fun dismissNotice() {
        _noticeMessage.value = null
    }

    /** Validates the form, then creates account + profile; calls [onSuccess] only if both succeed. */
    fun register(onSuccess: () -> Unit) {
        if (_isLoading.value) return
        _emailError.value = false
        _confirmEmailError.value = false
        _passwordError.value = false
        _confirmPasswordError.value = false

        val error = when {
            _email.value.isBlank() -> {
                _emailError.value = true
                "Please enter your email."
            }
            !isValidEmail(_email.value) -> {
                _emailError.value = true
                "Enter a valid email address (e.g. name@example.com). $EMAIL_EXTENSIONS_HINT."
            }
            _confirmEmail.value.isBlank() -> {
                _confirmEmailError.value = true
                "Please confirm your email."
            }
            _confirmEmail.value != _email.value -> {
                _confirmEmailError.value = true
                "Emails don't match."
            }
            _password.value.isBlank() -> {
                _passwordError.value = true
                "Please enter a password."
            }
            _password.value.length < MIN_PASSWORD_LENGTH -> {
                _passwordError.value = true
                "Password must be at least $MIN_PASSWORD_LENGTH characters."
            }
            _confirmPassword.value.isBlank() -> {
                _confirmPasswordError.value = true
                "Please confirm your password."
            }
            _confirmPassword.value != _password.value -> {
                _confirmPasswordError.value = true
                "Passwords don't match."
            }
            else -> null
        }

        if (error != null) {
            showNotice(error, NoticeType.ERROR)
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            val email = _email.value.trim()
            val uid = authRepository.register(email, _password.value).getOrElse {
                _isLoading.value = false
                showNotice(authErrorMessage(it), NoticeType.ERROR)
                return@launch
            }

            // The form has no username field yet, so derive one from the email.
            val profile = User(
                id = uid,
                username = email.substringBefore("@"),
                email = email,
                code = generateFriendCode()
            )
            userRepository.saveUser(profile)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure {
                    // Roll the account back so the user can simply retry the registration.
                    authRepository.deleteCurrentUser()
                    _isLoading.value = false
                    showNotice("Couldn't save your profile. Please try again.", NoticeType.ERROR)
                }
        }
    }

    /** 6-char code other users type to add this one as a friend (no ambiguous 0/O/1/I). */
    private fun generateFriendCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}
