package com.example.espoti.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

// ============================================================================
// AUTH ERROR MESSAGES
// ----------------------------------------------------------------------------
// Translates Firebase Auth exceptions into short messages for the notice card.
// Login deliberately gives one generic message for "wrong password" and "no
// such user" (newer projects report both as invalid credentials anyway).
// ============================================================================
fun authErrorMessage(error: Throwable): String = when (error) {
    is FirebaseAuthUserCollisionException -> "An account with this email already exists."
    is FirebaseAuthWeakPasswordException -> "That password is too weak. Try a longer one."
    is FirebaseAuthInvalidCredentialsException,
    is FirebaseAuthInvalidUserException -> "Incorrect email or password."
    is FirebaseNetworkException -> "No connection. Check your internet and try again."
    else -> "Something went wrong. Please try again."
}
