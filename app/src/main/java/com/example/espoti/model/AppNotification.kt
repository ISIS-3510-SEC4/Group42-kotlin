package com.example.espoti.model

/**
 * One entry in the notifications panel (see NotificationsPanel /
 * NotificationsViewModel). Each subclass is rendered as its own card in the
 * panel; vote reminders could be added later the same way.
 */
sealed class AppNotification {
    abstract val id: String

    /** A pending friendship (friendships/{id}) where the current user is the recipient. */
    data class FriendInvite(
        override val id: String,
        val fromUserId: String,
        val fromName: String
    ) : AppNotification()

    /** A meeting (meetings/{id}, id = meeting id) still voting, with the user in invitedIds. */
    data class MeetingInvite(
        override val id: String,
        val fromName: String,
        val title: String,
        val dateLabel: String?
    ) : AppNotification()

    data class LocationPending(
        override val id: String,
        val fromName: String,
        val title: String,
        val dateLabel: String?
    ) : AppNotification()
}


