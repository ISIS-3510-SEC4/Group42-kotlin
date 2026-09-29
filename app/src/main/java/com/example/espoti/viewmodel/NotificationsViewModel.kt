package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.AppNotification
import com.example.espoti.model.domain.FriendshipStatus
import kotlinx.coroutines.launch

/**
 * Backs the shared notifications panel (see EspotiTopBar / NotificationsPanel
 * in EspotiNavigation.kt) - one instance for the whole app so every screen's
 * bell shows the same list. Only friend invites are loaded for now; meeting
 * invites/vote reminders will be merged into [notifications] the same way
 * once those flows exist.
 *
 * This does one-shot reads, not a live Firestore listener, so [refresh] must
 * be called again whenever the list could be stale (EspotiNavHost does this
 * after login/register and every time the panel is opened) - otherwise a
 * request sent after this instance was first created would never show up.
 */
class NotificationsViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val friendshipRepository: FriendshipRepository = FriendshipRepository()
) : ViewModel() {

    private val _notifications = mutableStateOf<List<AppNotification>>(emptyList())
    val notifications: State<List<AppNotification>> = _notifications

    init {
        refresh()
    }

    fun refresh() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            friendshipRepository.getFriendships(uid).onSuccess { friendships ->
                // sendRequest stores userIds as [fromUid, toUid]: only requests
                // where I'm the recipient (toUid) belong in MY notifications.
                val incoming = friendships.filter {
                    it.status == FriendshipStatus.PENDING && it.userIds.getOrNull(1) == uid
                }
                _notifications.value = incoming.mapNotNull { friendship ->
                    val fromId = friendship.userIds.getOrNull(0) ?: return@mapNotNull null
                    val fromUser = userRepository.getUser(fromId).getOrNull() ?: return@mapNotNull null
                    AppNotification.FriendInvite(id = friendship.id, fromUserId = fromId, fromName = fromUser.username)
                }
            }
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
