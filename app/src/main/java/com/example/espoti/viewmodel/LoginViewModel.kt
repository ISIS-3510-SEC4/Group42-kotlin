package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.model.NoticeType
import com.example.espoti.util.EMAIL_EXTENSIONS_HINT
import com.example.espoti.util.authErrorMessage
import com.example.espoti.util.isValidEmail
import kotlinx.coroutines.launch

// ============================================================================
// LOGIN VIEW MODEL
// ----------------------------------------------------------------------------
// Owns the Login form state and its validation. LoginScreen only renders this
// state and forwards user events. After the local validation passes it signs
// in with Firebase Auth through AuthRepository.
// ============================================================================
class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _email = mutableStateOf("")
    val email: State<String> = _email

    private val _password = mutableStateOf("")
    val password: State<String> = _password

    private val _emailError = mutableStateOf(false)
    val emailError: State<Boolean> = _emailError

    private val _passwordError = mutableStateOf(false)
    val passwordError: State<Boolean> = _passwordError

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

    fun onPasswordChange(value: String) {
        _password.value = value
        _passwordError.value = false
    }

    fun showNotice(message: String, type: NoticeType) {
        _noticeMessage.value = message
        _noticeType.value = type
    }

    fun dismissNotice() {
        _noticeMessage.value = null
    }

    /** Validates the form, then signs in; calls [onSuccess] only if Firebase accepts the credentials. */
    fun login(onSuccess: () -> Unit) {
        if (_isLoading.value) return
        _emailError.value = false
        _passwordError.value = false

        val error = when {
            _email.value.isBlank() -> {
                _emailError.value = true
                "Please enter your email."
            }
            !isValidEmail(_email.value) -> {
                _emailError.value = true
                "Enter a valid email address (e.g. name@example.com). $EMAIL_EXTENSIONS_HINT."
            }
            _password.value.isBlank() -> {
                _passwordError.value = true
                "Please enter your password."
            }
            else -> null
        }

        if (error != null) {
            showNotice(error, NoticeType.ERROR)
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            authRepository.login(_email.value.trim(), _password.value)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure {
                    _isLoading.value = false
                    showNotice(authErrorMessage(it), NoticeType.ERROR)
                }
        }
    }
}
