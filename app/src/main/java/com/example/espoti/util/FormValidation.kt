package com.example.espoti.util

// ============================================================================
// FORM VALIDATION
// ----------------------------------------------------------------------------
// Shared by LoginScreen and RegisterScreen so both screens accept/reject the
// same email format and show the same hint.
// ============================================================================

val ALLOWED_EMAIL_EXTENSIONS = listOf(".com", ".net", ".org", ".edu")

val EMAIL_EXTENSIONS_HINT =
    "Allowed extensions: ${ALLOWED_EMAIL_EXTENSIONS.joinToString(", ")}"

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

const val MIN_PASSWORD_LENGTH = 6

/** True only if [email] is shaped like an email AND ends in one of [ALLOWED_EMAIL_EXTENSIONS]. */
fun isValidEmail(email: String): Boolean {
    if (!EMAIL_REGEX.matches(email)) return false
    return ALLOWED_EMAIL_EXTENSIONS.any { email.endsWith(it, ignoreCase = true) }
}
