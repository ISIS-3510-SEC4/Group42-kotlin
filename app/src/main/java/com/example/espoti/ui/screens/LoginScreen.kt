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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.espoti.ui.components.EspotiField
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiLogoStyle
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.R

// ============================================================================
// LOGIN SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Small logo + "Espoti" wordmark, centered
//   2. Email field
//   3. Password field
//   4. "Login" button (brown pill, NOT full width - about 55% wide, centered)
//   5. Google/Facebook icon row
//   6. "Don't have an account? Sign Up" -> goes to Registro
//   7. "Forgot Password?" link (not wired to anything in this prototype)
// ============================================================================
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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

        Spacer(modifier = Modifier.height(32.dp))

        EspotiField(
            label = "Email",
            value = email,
            onValueChange = { email = it },
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(20.dp))

        EspotiField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            isPassword = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Narrower, centered button - matches Figma (not full width here)
        EspotiPrimaryButton(
            text = "Login",
            onClick = onLoginSuccess, // prototype: no real validation yet
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ICON SPOT: social login row (Google "G" / Facebook "f").
        // These are plain styled letters as placeholders - swap for the
        // official Google/Facebook logo assets
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("G", color = BrandOrange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("f", color = BrandOrange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Don't have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onNavigateToRegister, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Sign Up", color = BrandOrange, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = { /* not wired up in this prototype */ }) {
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
