package com.example.espoti.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.security.BiometricAuthenticator
import com.example.espoti.viewmodel.BiometricLoginViewModel

// ============================================================================
// BIOMETRIC UI PIECES
// ----------------------------------------------------------------------------
// BiometricLoginButton is used by BOTH Welcome and Login. It only appears when
// an account is enrolled on this device and the phone has biometrics set up.
// ============================================================================

/** Finds the hosting FragmentActivity (BiometricPrompt needs it) from any Compose context. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * "Login with fingerprint" button + inline error text. Tapping it shows the
 * system biometric prompt; on success the user is signed in and [onSuccess] runs.
 * [contentColor] lets Welcome (dark background) use a light tint.
 */
@Composable
fun BiometricLoginButton(
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    viewModel: BiometricLoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val isAvailable by viewModel.isAvailable
    val isLoading by viewModel.isLoading
    val error by viewModel.error

    // Biometrics may be (re)configured while the app is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isAvailable) return

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            enabled = !isLoading,
            onClick = {
                val activity = context.findFragmentActivity() ?: return@OutlinedButton
                val cipher = viewModel.prepareCipher() ?: return@OutlinedButton
                BiometricAuthenticator.authenticate(
                    activity = activity,
                    title = "Log in to Espoti",
                    subtitle = "Use your fingerprint to continue",
                    cipher = cipher,
                    onSuccess = { unlocked -> viewModel.onAuthenticated(unlocked, onSuccess) },
                    onError = viewModel::onPromptError
                )
            }
        ) {
            Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = null, tint = contentColor)
            Text(
                text = if (isLoading) "Logging in..." else "Login with fingerprint",
                color = contentColor,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        error?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
