package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.AppNotification
import com.example.espoti.model.activityLabel
import com.example.espoti.model.domain.FriendshipStatus
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.VotingRules
import kotlinx.coroutines.launch

/**
 * Backs the shared notifications panel (see EspotiTopBar / NotificationsPanel
 * in EspotiNavigation.kt) - one instance for the whole app so every screen's
 * bell shows the same list: friend requests and meeting invitations.
 *
 * This does one-shot reads, not a live Firestore listener, so [refresh] must
 * be called again whenever the list could be stale (EspotiNavHost does this
 * after login/register and every time the panel is opened) - otherwise a
 * request sent after this instance was first created would never show up.
 */
class NotificationsViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val friendshipRepository: FriendshipRepository = FriendshipRepository(),
    private val meetingRepository: MeetingEventRepository = MeetingEventRepository(),
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _notifications = mutableStateOf<List<AppNotification>>(emptyList())
    val notifications: State<List<AppNotification>> = _notifications

    init {
        refresh()
    }

    fun refresh() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            val friendships = friendshipRepository.getFriendships(uid).getOrNull().orEmpty()
            // Nothing from a user blocked in either direction.
            val blocked = FriendshipRepository.blockedUserIds(friendships, uid)

            val friendInvites = friendships
                // sendRequest stores userIds as [fromUid, toUid]: only requests
                // where I'm the recipient (toUid) belong in MY notifications.
                .filter { it.status == FriendshipStatus.PENDING && it.userIds.getOrNull(1) == uid }
                .mapNotNull { friendship ->
                    val fromId = friendship.userIds.getOrNull(0) ?: return@mapNotNull null
                    val fromUser = userRepository.getUser(fromId).getOrNull() ?: return@mapNotNull null
                    AppNotification.FriendInvite(id = friendship.id, fromUserId = fromId, fromName = fromUser.username)
                }

            val now = clock()
            val meetingInvites = meetingRepository.getInvitationsFor(uid).getOrNull().orEmpty()
                // An invite to a meeting whose voting already ended is useless.
                .filter { VotingRules.effectiveStatus(it, now) == StatusMeeting.VOTING }
                .filterNot { it.creatorId in blocked }
                .map { meeting ->
                    AppNotification.MeetingInvite(
                        id = meeting.id,
                        fromName = userRepository.getUser(meeting.creatorId).getOrNull()?.username ?: "A friend",
                        title = meeting.activityLabel(),
                        dateLabel = meeting.date.takeIf { it > 0 }?.let(::formatMeetingDate)
                    )
                }

            _notifications.value = meetingInvites + friendInvites
        }
    }

    /** Joins the meeting; [onAccepted] gets its id (the panel then opens it to vote). */
    fun onAcceptMeetingInvite(notification: AppNotification.MeetingInvite, onAccepted: (String) -> Unit) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            meetingRepository.acceptInvitation(notification.id, uid)
                .onSuccess {
                    removeNotification(notification.id)
                    onAccepted(notification.id)
                }
        }
    }

    fun onDeclineMeetingInvite(notification: AppNotification.MeetingInvite) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            meetingRepository.declineInvitation(notification.id, uid)
                .onSuccess { removeNotification(notification.id) }
        }
    }

    fun onAcceptFriendInvite(notification: AppNotification.FriendInvite) {
        viewModelScope.launch {
            friendshipRepository.updateStatus(notification.id, FriendshipStatus.ACCEPTED)
                .onSuccess { removeNotification(notification.id) }
        }
    }

    fun onDeclineFriendInvite(notification: AppNotification.FriendInvite) {
        viewModelScope.launch {
            friendshipRepository.deleteFriendship(notification.id)
                .onSuccess { removeNotification(notification.id) }
        }
    }

    private fun removeNotification(id: String) {
        _notifications.value = _notifications.value.filterNot { it.id == id }
    }

    /** Called on logout: this instance outlives the session, so it must not
     *  keep showing the previous account's notifications to the next one. */
    fun clear() {
        _notifications.value = emptyList()
    }
}
