package com.example.espoti.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.espoti.model.AppNotification
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.SurfacePeach

// ============================================================================
// NOTIFICATIONS PANEL
// ----------------------------------------------------------------------------
// Lateral (right-side) panel opened from the bell in EspotiTopBar: a dimmed
// scrim + a sliding card, matching the pill/card language used everywhere
// else (SurfacePeach cards, EspotiPrimaryButton). Only friend invites render
// today; a new AppNotification case (meeting invite, vote reminder) only
// needs a new `is ...` branch below - the panel/scrim/animation stay as-is.
// ============================================================================

@Composable
fun NotificationsPanel(
    visible: Boolean,
    notifications: List<AppNotification>,
    onDismiss: () -> Unit,
    onAcceptFriendInvite: (AppNotification.FriendInvite) -> Unit,
    onDeclineFriendInvite: (AppNotification.FriendInvite) -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
                exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.82f)
                        .background(
                            MaterialTheme.colorScheme.background,
                            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                        // No-op click: swallows taps so they don't fall through to the scrim.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandBrown
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = BrandBrown)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (notifications.isEmpty()) {
                        Text(
                            text = "No notifications yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandBrown
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            notifications.forEach { notification ->
                                when (notification) {
                                    is AppNotification.FriendInvite -> FriendInviteCard(
                                        notification = notification,
                                        onAccept = { onAcceptFriendInvite(notification) },
                                        onDecline = { onDeclineFriendInvite(notification) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendInviteCard(
    notification: AppNotification.FriendInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfacePeach)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = BrandBrown)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "${notification.fromName} wants to be your friend",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandBrown,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EspotiPrimaryButton(
                text = "Accept",
                onClick = onAccept,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            )
            EspotiPrimaryButton(
                text = "Decline",
                onClick = onDecline,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = BrandBrown
            )
        }
    }
}
