package com.example.espoti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.espoti.ui.components.EspotiField
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiLogoStyle
import com.example.espoti.ui.theme.EspotiTheme

// ============================================================================
// REGISTRO (Register) SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Small logo + "Espoti" wordmark, centered
//   2. Email field
//   3. Confirm Email field
//   4. Password field
//   5. Confirm password field
//   6. "Register" button (brown pill, ~55% width, centered)
//   7. Google/Facebook icon row
//   8. "Already have an account? Login" -> goes back to Login
//
// WHERE TO CHANGE THINGS: same rules as LoginScreen.kt - text is inline
// below, colors/fonts come from ui/theme/, spacing is controlled by the
// Spacer heights and the Column's padding.
//
// NOTE: like LoginScreen, this is prototype-only. `onRegisterSuccess` fires
// immediately on button click with no validation or backend call. Add
// validation (e.g. check password == confirmPassword, fields not empty)
// before calling onRegisterSuccess() once you wire up real logic.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // ICON SPOT: same logo mark as Login/Bienvenida.
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(BrandBrown)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Espoti", style = EspotiLogoStyle, color = BrandBrown)

        Spacer(modifier = Modifier.height(24.dp))

        EspotiField(
            label = "Email",
            value = email,
            onValueChange = { email = it },
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Confirm Email",
            value = confirmEmail,
            onValueChange = { confirmEmail = it },
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            isPassword = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        EspotiField(
            label = "Confirm password",
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            isPassword = true
        )

        Spacer(modifier = Modifier.height(28.dp))

        EspotiPrimaryButton(
            text = "Register",
            onClick = onRegisterSuccess, // prototype: no real validation yet
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ICON SPOT: same social row placeholders as LoginScreen.kt.
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("G", color = BrandOrange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("f", color = BrandOrange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onNavigateToLogin, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
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
