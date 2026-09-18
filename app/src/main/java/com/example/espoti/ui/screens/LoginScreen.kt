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
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.example.espoti.util.isValidEmail

// ============================================================================
// LOGIN SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Small logo + "Espoti" wordmark, centered
//   2. Notice card (only visible when there's a validation error, or the
//      user taps something not implemented yet - social login/Forgot Password)
//   3. Email field
//   4. Password field (with show/hide toggle)
//   5. "Login" button (brown pill, NOT full width - about 55% wide, centered)
//   6. Google/Facebook icon row
//   7. "Don't have an account? Sign Up" -> goes to Registro
//   8. "Forgot Password?" link
//
// NOTE: like RegisterScreen.kt, this is prototype-only - Login just checks
// the fields LOOK right (non-empty, valid email) then calls onLoginSuccess.
// There's no backend call/real auth here yet.
// ============================================================================
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isDarkTheme = isSystemInDarkTheme()
    var iconResource = if (isDarkTheme) R.drawable.logoicon else R.drawable.logoicondark

    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var noticeType by remember { mutableStateOf(NoticeType.ERROR) }

    fun showNotice(message: String, type: NoticeType) {
        noticeMessage = message
        noticeType = type
    }

    fun validateAndLogin() {
        emailError = false
        passwordError = false

        val error = when {
            email.isBlank() -> {
                emailError = true
                "Please enter your email."
            }
            !isValidEmail(email) -> {
                emailError = true
                "Enter a valid email address (e.g. name@example.com). $EMAIL_EXTENSIONS_HINT."
            }
            password.isBlank() -> {
                passwordError = true
                "Please enter your password."
            }
            else -> null
        }

        if (error != null) {
            showNotice(error, NoticeType.ERROR)
        } else {
            onLoginSuccess()
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
            painter = painterResource(id=iconResource),
            contentDescription = "Espoti logo",
            modifier = Modifier.size(110.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Espoti", style = EspotiLogoStyle, color = BrandBrown)

        Spacer(modifier = Modifier.height(20.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

        EspotiField(
            label = "Password",
            value = password,
            onValueChange = { password = it; passwordError = false },
            isPassword = true,
            isError = passwordError
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Narrower, centered button - matches Figma (not full width here Has to change)
        EspotiPrimaryButton(
            text = "Login",
            onClick = { validateAndLogin() },
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ICON SPOT: social login row (Google "G" / Facebook "f").
        // These are plain styled letters as placeholders - swap for the
        // official Google/Facebook logo assets. Not implemented, so they
        // just surface the "not implemented" notice for now.
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                "G",
                color = BrandOrange,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    showNotice("Google login isn't implemented yet.", NoticeType.INFO)
                }
            )
            Text(
                "f",
                color = BrandOrange,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    showNotice("Facebook login isn't implemented yet.", NoticeType.INFO)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Don't have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onNavigateToRegister, contentPadding = PaddingValues(0.dp)) {
                Text("Sign Up", color = BrandOrange, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = {
            showNotice("Password recovery isn't implemented yet.", NoticeType.INFO)
        }) {
            Text("Forgot Password?", color = BrandOrange, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    EspotiTheme {
        LoginScreen(onLoginSuccess = {}, onNavigateToRegister = {})
    }
}
