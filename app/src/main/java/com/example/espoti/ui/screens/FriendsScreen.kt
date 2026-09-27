package com.example.espoti.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.model.Friend
import com.example.espoti.model.domain.User
import com.example.espoti.ui.components.EspotiCodeDisplay
import com.example.espoti.ui.components.EspotiLogo
import com.example.espoti.ui.components.EspotiNoticeCard
import com.example.espoti.ui.components.EspotiNotificationsBell
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.viewmodel.FriendsViewModel

// ============================================================================
// AMIGOS (Friends) SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Header: small logo (left), hamburger menu icon (right)
//   2. Your own avatar + "{name} this is your code" + big copyable code
//   3. "Search new friends" + search field + "Contacts" button
//   4. "Your friends:" -> row of friend avatars
// Backed by FriendsViewModel (Firestore: users/{uid}, friendships/{id}).
// ============================================================================

@Composable
fun FriendsScreen(
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    viewModel: FriendsViewModel = viewModel()
) {
    val myName by viewModel.myName
    val myCode by viewModel.myCode
    val friends by viewModel.friends
    val searchQuery by viewModel.searchQuery
    val isSearching by viewModel.isSearching
    val foundUser by viewModel.foundUser
    val isSendingRequest by viewModel.isSendingRequest
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
            FriendsHeader(hasUnreadNotifications = hasUnreadNotifications, onNotificationsClick = onNotificationsClick)

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(size = 56.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "$myName this is your code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandBrown
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            EspotiCodeDisplay(code = myCode)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = viewModel::onShareCodeClick) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "Share your code", tint = BrandBrown)
                }
            }

            AnimatedVisibility(visible = noticeMessage != null) {
                noticeMessage?.let { message ->
                    EspotiNoticeCard(
                        message = message,
                        onDismiss = viewModel::dismissNotice,
                        type = noticeType,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Search new friends",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Enter the code") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(10.dp))

                IconButton(onClick = viewModel::onSearchClick, enabled = !isSearching) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search", tint = BrandBrown)
                }
            }

            AnimatedVisibility(visible = foundUser != null) {
                foundUser?.let { user ->
                    FoundUserCard(
                        user = user,
                        isSending = isSendingRequest,
                        onConfirm = viewModel::onConfirmSendRequest,
                        onCancel = viewModel::onCancelSendRequest,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = viewModel::onContactsClick,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Filled.Call, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Contacts", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Your friends:",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                friends.forEach { friend -> FriendItem(friend) }
            }
        }
    }
}

@Composable
private fun FriendsHeader(hasUnreadNotifications: Boolean, onNotificationsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EspotiLogo(size = 40.dp)
        EspotiNotificationsBell(hasUnreadNotifications = hasUnreadNotifications, onClick = onNotificationsClick)
    }
}

/**
 * Shown after a successful code search, in place of sending the request
 * right away: the user's avatar/name plus an explicit confirm/cancel step.
 * There's no profile photo upload yet (User.profileImg is always empty for
 * now), so this still uses the placeholder avatar like the rest of the app.
 */
@Composable
private fun FoundUserCard(
    user: User,
    isSending: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePeach)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = user.username,
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Send ${user.username} a friend request?",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EspotiPrimaryButton(
                text = if (isSending) "Sending..." else "Send request",
                onClick = onConfirm,
                enabled = !isSending,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            )
            EspotiPrimaryButton(
                text = "Cancel",
                onClick = onCancel,
                enabled = !isSending,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = BrandBrown
            )
        }
    }
}

@Composable
private fun FriendItem(friend: Friend) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(size = 56.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = friend.name,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Avatar(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfacePeach),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = BrandBrown,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FriendsScreenPreview() {
    EspotiTheme {
        FriendsScreen()
    }
}
