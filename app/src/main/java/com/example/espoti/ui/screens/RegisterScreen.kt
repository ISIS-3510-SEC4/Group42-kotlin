package com.example.espoti.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.espoti.ui.components.EspotiField
import com.example.espoti.ui.components.EspotiNoticeCard
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.components.NoticeType
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiLogoStyle
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.R
import com.example.espoti.util.EMAIL_EXTENSIONS_HINT
import com.example.espoti.util.MIN_PASSWORD_LENGTH
import com.example.espoti.util.isValidEmail

// ============================================================================
// REGISTRO (Register) SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Small logo + "Espoti" wordmark, centered
//   2. Notice card (only visible when there's a validation error, or the
//      user taps something not implemented yet - social login)
//   3. Email field
//   4. Confirm Email field
//   5. Password field (with show/hide toggle)
//   6. Confirm password field (with show/hide toggle)
//   7. "Register" button (brown pill, ~55% width, centered)
//   8. Google/Facebook icon row
//   9. "Already have an account? Login" -> goes back to Login
// ============================================================================
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var emailError by remember { mutableStateOf(false) }
    var confirmEmailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }

    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var noticeType by remember { mutableStateOf(NoticeType.ERROR) }

    fun showNotice(message: String, type: NoticeType) {
        noticeMessage = message
        noticeType = type
    }

    fun validateAndRegister() {
        emailError = false
        confirmEmailError = false
        passwordError = false
        confirmPasswordError = false

        val error = when {
            email.isBlank() -> {
                emailError = true
                "Please enter your email."
            }
            !isValidEmail(email) -> {
                emailError = true
                "Enter a valid email address (e.g. name@example.com). $EMAIL_EXTENSIONS_HINT."
            }
            confirmEmail.isBlank() -> {
                confirmEmailError = true
                "Please confirm your email."
            }
            confirmEmail != email -> {
                confirmEmailError = true
                "Emails don't match."
            }
            password.isBlank() -> {
                passwordError = true
                "Please enter a password."
            }
            password.length < MIN_PASSWORD_LENGTH -> {
                passwordError = true
                "Password must be at least $MIN_PASSWORD_LENGTH characters."
            }
            confirmPassword.isBlank() -> {
                confirmPasswordError = true
                "Please confirm your password."
            }
            confirmPassword != password -> {
                confirmPasswordError = true
                "Passwords don't match."
            }
            else -> null
        }

        if (error != null) {
            showNotice(error, NoticeType.ERROR)
        } else {
            onRegisterSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Image(
            painter = painterResource(R.drawable.logoicon),
            contentDescription = "Espoti logo",
            modifier = Modifier.size(110.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Espoti", style = EspotiLogoStyle, color = BrandBrown)

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(visible = noticeMessage != null) {
            noticeMessage?.let { message ->
                EspotiNoticeCard(
                    message = message,
                    onDismiss = { noticeMessage = null },
                    type = noticeType,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        EspotiField(
            label = "Email",
            value = email,
            onValueChange = { email = it; emailError = false },
            keyboardType = KeyboardType.Email,
            supportingText = EMAIL_EXTENSIONS_HINT,
            isError = emailError
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Confirm Email",
            value = confirmEmail,
            onValueChange = { confirmEmail = it; confirmEmailError = false },
            keyboardType = KeyboardType.Email,
            isError = confirmEmailError
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Password",
            value = password,
            onValueChange = { password = it; passwordError = false },
            isPassword = true,
            isError = passwordError
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Confirm password",
            value = confirmPassword,
            onValueChange = { confirmPassword = it; confirmPasswordError = false },
            isPassword = true,
            isError = confirmPasswordError
        )

        Spacer(modifier = Modifier.height(28.dp))

        EspotiPrimaryButton(
            text = "Register",
            onClick = { validateAndRegister() },
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ICON SPOT: same social row placeholders as LoginScreen.kt. Not
        // implemented, so they just surface the "not implemented" notice.
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                "G",
                color = BrandOrange,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    showNotice("Google sign-up isn't implemented yet.", NoticeType.INFO)
                }
            )
            Text(
                "f",
                color = BrandOrange,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    showNotice("Facebook sign-up isn't implemented yet.", NoticeType.INFO)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onNavigateToLogin, contentPadding = PaddingValues(0.dp)) {
                Text("Login", color = BrandOrange, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    EspotiTheme {
        RegisterScreen(onRegisterSuccess = {}, onNavigateToLogin = {})
    }
}
