package com.example.espoti.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
// ============================================================================

@Composable
fun ProfileScreen(
    onEditProfileClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val profile by viewModel.profile

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
                IconButton(onClick = viewModel::onShareCodeClick) {
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

            Spacer(modifier = Modifier.height(20.dp))

            ProfileCoffeeCard(onSupportClick = viewModel::onSupportClick)

            Spacer(modifier = Modifier.height(24.dp))

            ProfileMenuItem(text = "Privacy", onClick = viewModel::onPrivacyClick)
            ProfileMenuItem(text = "Location", onClick = viewModel::onLocationClick)
            ProfileMenuItem(text = "Notification", onClick = viewModel::onNotificationClick)
            ProfileMenuItem(text = "Help us grow", onClick = viewModel::onHelpUsGrowClick)
            ProfileMenuItem(
                text = "Log Out",
                onClick = {
                    viewModel.onLogout()
                    onLogoutClick()
                }
            )
            ProfileMenuItem(text = "Delete account", onClick = onDeleteAccountClick)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
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

@Composable
private fun ProfileMenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = BrandBrown,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    EspotiTheme {
        ProfileScreen(onEditProfileClick = {}, onLogoutClick = {}, onDeleteAccountClick = {})
    }
}
