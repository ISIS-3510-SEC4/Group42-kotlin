package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.NotificationsLocalStore
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.di.AppContainer
import com.example.espoti.model.AppNotification
import com.example.espoti.model.activityLabel
import com.example.espoti.model.domain.FriendshipStatus
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.domain.VotingRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 *
 * Sprint 3:
 * - Eventual connectivity, "network falling back to cache": online, the list
 *   comes from Firestore and is saved in SQLite; offline (or if Firestore
 *   fails) the last saved list is shown with [savedDataNotice]. Accept and
 *   decline are refused offline with [actionError], because they depend on the
 *   current state of the meeting or friendship on the server.
 * - Multithreading: friendships and meeting invites are read at the same
 *   time, and so are the senders' usernames (async/awaitAll).
 * - Caching: [userRepository] defaults to the app-wide in-memory user cache.
 */
class NotificationsViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = AppContainer.userRepository,
    private val friendshipRepository: FriendshipRepository = FriendshipRepository(),
    private val meetingRepository: MeetingEventRepository = MeetingEventRepository(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val notificationsStore: NotificationsLocalStore = AppContainer.notificationsStore,
    connectivity: ConnectivityObserver = AppContainer.connectivity
) : ViewModel() {

    private val _notifications = mutableStateOf<List<AppNotification>>(emptyList())
    val notifications: State<List<AppNotification>> = _notifications

    // Null when the list is fresh from Firestore; otherwise says it's the saved one.
    private val _savedDataNotice = mutableStateOf<String?>(null)
    val savedDataNotice: State<String?> = _savedDataNotice

    private val _actionError = mutableStateOf<String?>(null)
    val actionError: State<String?> = _actionError

    private var refreshJob: Job? = null

    val isOnline: State<Boolean> = trackConnectivity(
        connectivity,
        onDisconnected = { if (_notifications.value.isNotEmpty()) markSavedData("You're offline.", null) },
        onReconnected = { refresh() }
    )

    init {
        refresh()
    }

    fun refresh() {
        val uid = authRepository.currentUserId ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _actionError.value = null
            if (!isOnline.value) {
                showSaved(uid, reason = "You're offline.")
                return@launch
            }
            try {
                val fresh = loadFromFirestore(uid)
                _notifications.value = fresh
                _savedDataNotice.value = null
                notificationsStore.save(uid, fresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showSaved(uid, reason = "Couldn't refresh your notifications.")
            }
        }
    }

    /** Fallback step: the list saved the last time Firestore answered. */
    private suspend fun showSaved(uid: String, reason: String) {
        val saved = notificationsStore.get(uid)
        if (saved != null) _notifications.value = saved.notifications
        markSavedData(reason, saved?.savedAt)
    }

    private fun markSavedData(reason: String, savedAt: Long?) {
        val time = savedAt?.let { " (saved at ${timeLabel(it)})" }.orEmpty()
        _savedDataNotice.value = "$reason These notifications may be outdated$time."
    }

    /** Throws if Firestore can't be reached, so [refresh] falls back to the saved list. */
    private suspend fun loadFromFirestore(uid: String): List<AppNotification> = coroutineScope {
        // Independent reads: both are in flight at the same time.
        val friendshipsRequest = async { friendshipRepository.getFriendships(uid).getOrThrow() }
        val invitationsRequest = async { meetingRepository.getInvitationsFor(uid).getOrThrow() }

        val friendships = friendshipsRequest.await()
        // Nothing from a user blocked in either direction.
        val blocked = FriendshipRepository.blockedUserIds(friendships, uid)

        val friendInvites = friendships
            // sendRequest stores userIds as [fromUid, toUid]: only requests
            // where I'm the recipient (toUid) belong in MY notifications.
            .filter { it.status == FriendshipStatus.PENDING && it.userIds.getOrNull(1) == uid }
            .mapNotNull { friendship ->
                val fromId = friendship.userIds.getOrNull(0) ?: return@mapNotNull null
                // One username read per sender, all started at once.
                async {
                    userRepository.getUser(fromId).getOrNull()?.let { fromUser ->
                        AppNotification.FriendInvite(id = friendship.id, fromUserId = fromId, fromName = fromUser.username)
                    }
                }
            }

        val now = clock()
        val meetingInvites = invitationsRequest.await()
            // An invite to a meeting whose voting already ended is useless.
            .filter { VotingRules.effectiveStatus(it, now) == StatusMeeting.VOTING }
            .filterNot { it.creatorId in blocked }
            .map { meeting ->
                async {
                    AppNotification.MeetingInvite(
                        id = meeting.id,
                        fromName = userRepository.getUser(meeting.creatorId).getOrNull()?.username ?: "A friend",
                        title = meeting.activityLabel(),
                        dateLabel = meeting.date.takeIf { it > 0 }?.let(::formatMeetingDate)
                    )
                }
            }

        meetingInvites.awaitAll() + friendInvites.awaitAll().filterNotNull()
    }

    /** Joins the meeting; [onAccepted] gets its id (the panel then opens it to vote). */
    fun onAcceptMeetingInvite(notification: AppNotification.MeetingInvite, onAccepted: (String) -> Unit) {
        val uid = authRepository.currentUserId ?: return
        if (refuseOffline()) return
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
        if (refuseOffline()) return
        viewModelScope.launch {
            meetingRepository.declineInvitation(notification.id, uid)
                .onSuccess { removeNotification(notification.id) }
        }
    }

    fun onAcceptFriendInvite(notification: AppNotification.FriendInvite) {
        if (refuseOffline()) return
        viewModelScope.launch {
            friendshipRepository.updateStatus(notification.id, FriendshipStatus.ACCEPTED)
                .onSuccess { removeNotification(notification.id) }
        }
    }

    fun onDeclineFriendInvite(notification: AppNotification.FriendInvite) {
        if (refuseOffline()) return
        viewModelScope.launch {
            friendshipRepository.deleteFriendship(notification.id)
                .onSuccess { removeNotification(notification.id) }
        }
    }

    /** Answering needs the server: true (and a message) when there is no connection. */
    private fun refuseOffline(): Boolean {
        if (isOnline.value) return false
        _actionError.value = OfflineMessages.NOTIFICATION_ACTION
        return true
    }

    /** Drops an answered notification from the screen and from the saved copy. */
    private fun removeNotification(id: String) {
        _notifications.value = _notifications.value.filterNot { it.id == id }
        val uid = authRepository.currentUserId ?: return
        val remaining = _notifications.value
        viewModelScope.launch { notificationsStore.save(uid, remaining) }
    }

    /** Called on logout: this instance outlives the session, so it must not
     *  keep showing the previous account's notifications to the next one. */
    fun clear() {
        refreshJob?.cancel()
        _notifications.value = emptyList()
        _savedDataNotice.value = null
        _actionError.value = null
    }
}
