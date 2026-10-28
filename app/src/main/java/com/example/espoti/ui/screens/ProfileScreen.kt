package com.example.espoti.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.ui.components.EspotiField
import com.example.espoti.ui.components.findFragmentActivity
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.util.DeviceLocationStatus
import com.example.espoti.util.LOCATION_PERMISSIONS
import com.example.espoti.util.deviceLocationStatus
import com.example.espoti.util.openAppSettings
import com.example.espoti.util.openDeviceLocationSettings
import com.example.espoti.viewmodel.DeleteAccountViewModel
import androidx.compose.ui.platform.LocalContext
import com.example.espoti.util.shareFriendCode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User
import com.example.espoti.ui.components.EspotiCodeDisplay
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiNotificationsBell
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.components.SavedDataCaption
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.viewmodel.ProfileViewModel

// ============================================================================
// PERFIL (Profile) SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Header: small logo (left), hamburger menu icon (right)
//   2. "Hello {name}" + "Edit Profile" button
//   3. Avatar + big copyable code + share icon
//   4. Preferences / Maximum radio
//   5. "Invite us a coffee" donation card
//   6. Settings list: Privacy, Location, Notification, Help us grow,
//      Log Out, Delete account
// Backed by ProfileViewModel (Firestore: users/{uid}).
//   - Location: asks for the location permission and/or opens the phone's
//     location settings (util/DeviceLocation.kt); shows whether it is on.
//   - Notification: opens the notifications side panel (same as the bell).
//   - Delete account: password confirmation dialog -> DeleteAccountViewModel.
// ============================================================================

@Composable
fun ProfileScreen(
    onEditProfileClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onAccountDeleted: () -> Unit,
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(),
    deleteAccountViewModel: DeleteAccountViewModel = viewModel()
) {
    val profile by viewModel.profile
    val hasPendingSync by viewModel.hasPendingSync
    val showDeleteDialog by deleteAccountViewModel.showDialog
    val isDeleting by deleteAccountViewModel.isDeleting
    val deleteError by deleteAccountViewModel.error

    // The ViewModel outlives a trip to Edit profile, so reload every time this
    // screen is shown (picks up edits, including ones saved offline).
    LaunchedEffect(Unit) { viewModel.refresh() }
    val context = LocalContext.current
    val locationStatus = rememberDeviceLocationStatus()
    val onLocationClick = rememberLocationAction(locationStatus)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            ProfileHeader(hasUnreadNotifications = hasUnreadNotifications, onNotificationsClick = onNotificationsClick)

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hello ${profile?.username.orEmpty()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                EspotiPrimaryButton(
                    text = "Edit Profile",
                    onClick = onEditProfileClick,
                    modifier = Modifier.height(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SurfacePeach),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = BrandBrown,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your code",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            EspotiCodeDisplay(code = profile?.code.orEmpty())

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = {
                    viewModel.onShareCodeClick()
                    shareFriendCode(context, profile?.code.orEmpty())
                }) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "Share your code", tint = BrandBrown)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                text = buildAnnotatedStringBold("Preferences: ", formatPreferences(profile?.preferences))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                text = buildAnnotatedStringBold("Maximum radio: ", "${profile?.maxRadiusKm ?: User().maxRadiusKm} km")
            )

            if (hasPendingSync) {
                SavedDataCaption(
                    text = "Some changes are saved on this device and will sync when you're back online.",
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            ProfileCoffeeCard(onSupportClick = viewModel::onSupportClick)

            Spacer(modifier = Modifier.height(24.dp))

            ProfileMenuItem(text = "Privacy", onClick = viewModel::onPrivacyClick)
            ProfileMenuItem(
                text = "Location",
                detail = when (locationStatus.value) {
                    DeviceLocationStatus.ON -> "On"
                    DeviceLocationStatus.TURNED_OFF -> "Off · Tap to turn on"
                    DeviceLocationStatus.PERMISSION_NEEDED -> "Tap to allow"
                },
                onClick = onLocationClick
            )
            ProfileMenuItem(text = "Notification", onClick = onNotificationsClick)
            ProfileMenuItem(text = "Help us grow", onClick = viewModel::onHelpUsGrowClick)
            ProfileMenuItem(
                text = "Log Out",
                onClick = {
                    viewModel.onLogout()
                    onLogoutClick()
                }
            )
            ProfileMenuItem(
                text = "Delete account",
                color = MaterialTheme.colorScheme.error,
                onClick = deleteAccountViewModel::onDeleteClick
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            isDeleting = isDeleting,
            error = deleteError,
            onDismiss = deleteAccountViewModel::onDismiss,
            onConfirm = { password -> deleteAccountViewModel.onConfirm(password, onAccountDeleted) }
        )
    }
}

/**
 * Current [DeviceLocationStatus], checked again every time the screen comes
 * back to the front (e.g. after the user changed it in system settings).
 */
@Composable
private fun rememberDeviceLocationStatus(): MutableState<DeviceLocationStatus> {
    val context = LocalContext.current
    val status = remember { mutableStateOf(context.deviceLocationStatus()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status.value = context.deviceLocationStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return status
}

/**
 * What tapping "Location" does:
 *   - no permission -> system permission dialog; if it is then granted but the
 *     phone's location is off, opens the location settings right away. If the
 *     user denied it for good (Android no longer shows the dialog), opens the
 *     app's settings so they can allow it there.
 *   - permission granted -> phone's location settings (to turn it on or off).
 */
@Composable
private fun rememberLocationAction(status: MutableState<DeviceLocationStatus>): () -> Unit {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        status.value = context.deviceLocationStatus()
        val activity = context.findFragmentActivity()
        val deniedForGood = activity != null && LOCATION_PERMISSIONS.none {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        when {
            status.value == DeviceLocationStatus.TURNED_OFF -> context.openDeviceLocationSettings()
            status.value == DeviceLocationStatus.PERMISSION_NEEDED && deniedForGood -> context.openAppSettings()
        }
    }
    return {
        AnalyticsDependencies.tracker.featureUsed(feature = "LOCATION_SETTINGS")
        if (status.value == DeviceLocationStatus.PERMISSION_NEEDED) {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        } else {
            context.openDeviceLocationSettings()
        }
    }
}

/** Last step before deleting: explains what is removed and asks for the password. */
@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text(
                    text = "Your profile and all your friendships (friends, requests and blocks) " +
                        "will be deleted for good. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                EspotiField(
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    isPassword = true
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !isDeleting) {
                Text(
                    text = if (isDeleting) "Deleting..." else "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Cancel", color = BrandBrown)
            }
        }
    )
}

private fun formatPreferences(preferences: List<ActivityType>?): String =
    preferences?.takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        ?: "None yet"

private fun buildAnnotatedStringBold(label: String, value: String) =
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
        append(value)
    }

@Composable
private fun ProfileHeader(hasUnreadNotifications: Boolean, onNotificationsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EspotiLogo(size = 40.dp)
        EspotiNotificationsBell(hasUnreadNotifications = hasUnreadNotifications, onClick = onNotificationsClick)
    }
}

@Composable
private fun ProfileCoffeeCard(onSupportClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePeach)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Invite us a coffee",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )
            // ICON SPOT: coffee cup icon.
            Text("☕", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Help us grow, make a contribution.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            EspotiPrimaryButton(
                text = "Support",
                onClick = onSupportClick,
                modifier = Modifier
                    .width(110.dp)
                    .height(40.dp)
            )
        }
    }
}

/** One settings row; [detail] is an optional status on the right (e.g. "On"). */
@Composable
private fun ProfileMenuItem(
    text: String,
    onClick: () -> Unit,
    detail: String? = null,
    color: Color = BrandBrown
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color)
        detail?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = BrandOrange)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    EspotiTheme {
        ProfileScreen(onEditProfileClick = {}, onLogoutClick = {}, onAccountDeleted = {})
    }
}
