package com.example.espoti.model

/**
 * One entry in the notifications panel (see NotificationsPanel /
 * NotificationsViewModel). Only [FriendInvite] is wired up for now - meeting
 * invites and vote reminders will be added later as more subclasses here,
 * each rendered as its own card in the panel.
 */
sealed class AppNotification {
    abstract val id: String

    /** A pending friendship (friendships/{id}) where the current user is the recipient. */
    data class FriendInvite(
        override val id: String,
        val fromUserId: String,
        val fromName: String
    ) : AppNotification()
}
