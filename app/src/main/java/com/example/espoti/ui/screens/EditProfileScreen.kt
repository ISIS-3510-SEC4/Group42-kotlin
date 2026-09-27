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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.ui.components.EspotiField
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiNoticeCard
import com.example.espoti.ui.components.EspotiNotificationsBell
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.viewmodel.EditProfileViewModel

// ============================================================================
// EDIT PROFILE SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Header: small logo (left), hamburger menu icon (right)
//   2. "Edit Profile:" title
//   3. Avatar placeholder
//   4. Username / Email / Preferences / Description fields
//   5. "Save" button + "Change Password" link
// Opened from Profile's "Edit Profile" button (see EspotiNavigation.kt).
// ============================================================================

@Composable
fun EditProfileScreen(
    onSaveClick: () -> Unit,
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    viewModel: EditProfileViewModel = viewModel()
) {
    val username by viewModel.username
    val usernameError by viewModel.usernameError
    val email by viewModel.email
    val preferences by viewModel.preferences
    val description by viewModel.description
    val isSaving by viewModel.isSaving
    val noticeMessage by viewModel.noticeMessage
    val noticeType by viewModel.noticeType

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
