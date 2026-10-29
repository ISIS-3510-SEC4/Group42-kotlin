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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
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
    onDeclineFriendInvite: (AppNotification.FriendInvite) -> Unit,
    onAcceptMeetingInvite: (AppNotification.MeetingInvite) -> Unit = {},
    onDeclineMeetingInvite: (AppNotification.MeetingInvite) -> Unit = {},
    // Eventual connectivity: says the list is the saved one, and why answering is disabled.
    savedDataNotice: String? = null,
    actionError: String? = null,
    actionsEnabled: Boolean = true
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

                    savedDataNotice?.let {
                        SavedDataCaption(text = it, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (!actionsEnabled && notifications.isNotEmpty()) {
                        Text(
                            text = "Connect to the internet to accept or decline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    actionError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

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
                                        enabled = actionsEnabled,
                                        onAccept = { onAcceptFriendInvite(notification) },
                                        onDecline = { onDeclineFriendInvite(notification) }
                                    )
                                    is AppNotification.MeetingInvite -> InviteCard(
                                        icon = Icons.Filled.Event,
                                        message = buildString {
                                            append("${notification.fromName} invited you to a ${notification.title.lowercase()}")
                                            notification.dateLabel?.let { append(" on $it") }
                                            append(". Accept to vote the place.")
                                        },
                                        enabled = actionsEnabled,
                                        onAccept = { onAcceptMeetingInvite(notification) },
                                        onDecline = { onDeclineMeetingInvite(notification) }
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
    enabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    InviteCard(
        icon = Icons.Filled.Person,
        message = "${notification.fromName} wants to be your friend",
        enabled = enabled,
        onAccept = onAccept,
        onDecline = onDecline
    )
}

/** Card with an icon, a message and Accept / Decline, shared by every invite type. */
@Composable
private fun InviteCard(
    icon: ImageVector,
    message: String,
    enabled: Boolean,
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
                Icon(imageVector = icon, contentDescription = null, tint = BrandBrown)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = message,
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
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            )
            EspotiPrimaryButton(
                text = "Decline",
                onClick = onDecline,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = BrandBrown
            )
        }
    }
}
