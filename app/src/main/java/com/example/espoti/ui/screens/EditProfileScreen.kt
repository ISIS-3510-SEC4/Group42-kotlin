package com.example.espoti.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.security.BiometricAuthenticator
import com.example.espoti.ui.components.EspotiField
import com.example.espoti.ui.components.findFragmentActivity
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiNoticeCard
import com.example.espoti.ui.components.EspotiNotificationsBell
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.viewmodel.BiometricSetupViewModel
import com.example.espoti.viewmodel.EditProfileViewModel

// ============================================================================
// EDIT PROFILE SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Header: small logo (left), hamburger menu icon (right)
//   2. "Edit Profile:" title
//   3. Avatar placeholder
//   4. Username / Email / Preferences / Description fields
//   5. Small "Set up biometrics" button (fingerprint login enrollment)
//   6. "Save" button + "Change Password" link
// Opened from Profile's "Edit Profile" button (see EspotiNavigation.kt).
// ============================================================================

@Composable
fun EditProfileScreen(
    onSaveClick: () -> Unit,
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    viewModel: EditProfileViewModel = viewModel(),
    biometricViewModel: BiometricSetupViewModel = viewModel()
) {
    val username by viewModel.username
    val usernameError by viewModel.usernameError
    val email by viewModel.email
    val preferences by viewModel.preferences
    val description by viewModel.description
    val isSaving by viewModel.isSaving
    val noticeMessage by viewModel.noticeMessage
    val noticeType by viewModel.noticeType

    val context = LocalContext.current
    val isBiometricEnrolled by biometricViewModel.isEnrolled
    val showPasswordDialog by biometricViewModel.showPasswordDialog
    val biometricBusy by biometricViewModel.isBusy
    val biometricNotice by biometricViewModel.noticeMessage
    val biometricNoticeType by biometricViewModel.noticeType

    if (showPasswordDialog) {
        BiometricPasswordDialog(
            isBusy = biometricBusy,
            onDismiss = biometricViewModel::onPasswordDialogDismiss,
            onConfirm = { password ->
                biometricViewModel.onPasswordConfirmed(password) { cipher ->
                    val activity = context.findFragmentActivity() ?: return@onPasswordConfirmed
                    BiometricAuthenticator.authenticate(
                        activity = activity,
                        title = "Set up biometric login",
                        subtitle = "Confirm your fingerprint to enable it",
                        cipher = cipher,
                        onSuccess = biometricViewModel::onEnrollAuthenticated,
                        onError = biometricViewModel::onPromptError
                    )
                }
            }
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            EditProfileHeader(hasUnreadNotifications = hasUnreadNotifications, onNotificationsClick = onNotificationsClick)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Edit Profile:",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfacePeach),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = BrandBrown,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                label = "Username",
                value = username,
                onValueChange = viewModel::onUsernameChange,
                isError = usernameError
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Read-only: changing the sign-in email needs Firebase Auth
            // re-authentication, which this form doesn't collect.
            Text(
                text = "Email",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                color = BrandBrown,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfacePeach, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            EspotiField(label = "Preferences", value = preferences, onValueChange = viewModel::onPreferencesChange)

            Spacer(modifier = Modifier.height(16.dp))

            EspotiField(label = "Description", value = description, onValueChange = viewModel::onDescriptionChange)

            Spacer(modifier = Modifier.height(20.dp))

            // Small button: enrolls (or removes) fingerprint login for this account.
            AnimatedVisibility(visible = biometricNotice != null) {
                biometricNotice?.let { message ->
                    EspotiNoticeCard(
                        message = message,
                        onDismiss = biometricViewModel::dismissNotice,
                        type = biometricNoticeType,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    if (isBiometricEnrolled) biometricViewModel.onDisableClick()
                    else biometricViewModel.onSetupClick()
                },
                enabled = !biometricBusy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = BrandOrange,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isBiometricEnrolled) "Disable biometrics" else "Set up biometrics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandOrange,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            EspotiPrimaryButton(
                text = if (isSaving) "Saving..." else "Save",
                onClick = { viewModel.onSave(onSaveClick) },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Change Password",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandOrange,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::onChangePasswordClick)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Asks for the account password before enrolling, so the app can store a verified login. */
@Composable
private fun BiometricPasswordDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm your password") },
        text = {
            Column {
                Text(
                    text = "Your fingerprint will unlock this login on this device only.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                EspotiField(label = "Password", value = password, onValueChange = { password = it }, isPassword = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !isBusy) {
                Text(if (isBusy) "Checking..." else "Continue", color = BrandOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = BrandBrown) }
        }
    )
}

@Composable
private fun EditProfileHeader(hasUnreadNotifications: Boolean, onNotificationsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EspotiLogo(size = 40.dp)
        EspotiNotificationsBell(hasUnreadNotifications = hasUnreadNotifications, onClick = onNotificationsClick)
    }
}

@Preview(showBackground = true)
@Composable
private fun EditProfileScreenPreview() {
    EspotiTheme {
        EditProfileScreen(onSaveClick = {})
    }
}
