package com.example.espoti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.EspotiLogoStyle
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.ui.theme.TextCream
import com.example.espoti.R

// ============================================================================
// BIENVENIDA (Welcome) SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Logo placeholder (circle) on a dark brown full-screen background
//   2. "Espoti" wordmark, cream, decorative font
//   3. "Where do we meet?" tagline, cream, bold
//   4. "Login" button (orange pill)  -> goes to Login
//   5. "Register" button (orange pill) -> goes to Registro
// ============================================================================
@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBrown) // <- the one screen that isn't white
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.9f))

        Image(
            painter = painterResource(R.drawable.logoicon),
            contentDescription = "Espoti Logo",
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Wordmark - change this text directly
        Text(
            text = "Espoti",
            style = EspotiLogoStyle,
            color = TextCream,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tagline - change this text directly
        Text(
            text = "Where do we meet?",
            style = MaterialTheme.typography.headlineMedium,
            color = TextCream,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        EspotiPrimaryButton(
            text = "Login",
            onClick = onLoginClick,
            containerColor = BrandOrange,
            contentColor = TextCream
        )

        Spacer(modifier = Modifier.height(12.dp))

        EspotiPrimaryButton(
            text = "Register",
            onClick = onRegisterClick,
            containerColor = BrandOrange,
            contentColor = TextCream
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    EspotiTheme {
        WelcomeScreen(onLoginClick = {}, onRegisterClick = {})
    }
}
