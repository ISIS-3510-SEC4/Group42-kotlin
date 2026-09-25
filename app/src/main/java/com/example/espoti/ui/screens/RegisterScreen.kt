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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.espoti.ui.components.EspotiField
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiNoticeCard
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.model.NoticeType
import com.example.espoti.viewmodel.RegisterViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiLogoStyle
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.util.EMAIL_EXTENSIONS_HINT

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
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel = viewModel()
) {
    val email by viewModel.email
    val confirmEmail by viewModel.confirmEmail
    val password by viewModel.password
    val confirmPassword by viewModel.confirmPassword

    val emailError by viewModel.emailError
    val confirmEmailError by viewModel.confirmEmailError
    val passwordError by viewModel.passwordError
    val confirmPasswordError by viewModel.confirmPasswordError

    val noticeMessage by viewModel.noticeMessage
    val noticeType by viewModel.noticeType
    val isLoading by viewModel.isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        EspotiLogo(size = 110.dp)

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Espoti", style = EspotiLogoStyle, color = BrandBrown)

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(visible = noticeMessage != null) {
            noticeMessage?.let { message ->
                EspotiNoticeCard(
                    message = message,
                    onDismiss = viewModel::dismissNotice,
                    type = noticeType,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        EspotiField(
            label = "Email",
            value = email,
            onValueChange = viewModel::onEmailChange,
            keyboardType = KeyboardType.Email,
            supportingText = EMAIL_EXTENSIONS_HINT,
            isError = emailError
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Confirm Email",
            value = confirmEmail,
            onValueChange = viewModel::onConfirmEmailChange,
            keyboardType = KeyboardType.Email,
            isError = confirmEmailError
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Password",
            value = password,
            onValueChange = viewModel::onPasswordChange,
            isPassword = true,
            isError = passwordError
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Confirm password",
            value = confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            isPassword = true,
            isError = confirmPasswordError
        )

        Spacer(modifier = Modifier.height(28.dp))

        EspotiPrimaryButton(
            text = if (isLoading) "Creating account..." else "Register",
            onClick = { viewModel.register(onRegisterSuccess) },
            enabled = !isLoading,
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
                    viewModel.showNotice("Google sign-up isn't implemented yet.", NoticeType.INFO)
                }
            )
            Text(
                "f",
                color = BrandOrange,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    viewModel.showNotice("Facebook sign-up isn't implemented yet.", NoticeType.INFO)
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
