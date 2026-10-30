package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.FriendsLocalStore
import com.example.espoti.data.local.ProfileLocalStore
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.di.AppContainer
import com.example.espoti.model.Friend
import com.example.espoti.model.NoticeType
import com.example.espoti.model.domain.Friendship
import com.example.espoti.model.domain.FriendshipStatus
import com.example.espoti.model.domain.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.example.espoti.analytics.AnalyticsDependencies
import com.example.espoti.data.local.FriendRequestState
import com.example.espoti.data.local.PendingAction
import com.example.espoti.data.local.PendingActionStore
import com.example.espoti.data.local.StoredAction
import com.example.espoti.data.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Loads the signed-in user's own code and accepted friends from Firestore
 * (users/{uid}, friendships/{id}) and lets them add a new friend by code.
 * Searching a code doesn't send the request right away: it shows the found
 * user in [foundUser] first, and the request only goes out once the screen
 * calls [onConfirmSendRequest] (see FriendsScreen's confirmation card).
 * Accepting a pending request happens from the notifications panel (see
 * NotificationsViewModel), so only ACCEPTED friendships show up in [friends].
 * A friend can be removed or blocked; users the signed-in user blocked are
 * listed in [blockedUsers] so they can be unblocked.
 *
 * Sprint 3:
 * - Eventual connectivity, "cache then network": the lists saved in SQLite are
 *   shown first, then replaced by Firestore's when online (and saved again).
 *   Offline, [savedDataNotice] says the lists come from this device, and
 *   search / remove / block / unblock require the server.
 *   Confirmed friend requests are saved in SQLite and delivered by WorkManager
 *   when online. Back online, the lists reload automatically.
 * - Multithreading: friends' usernames are read in parallel (async/awaitAll).
 * - Caching: [userRepository] defaults to the app-wide in-memory user cache.
 */
class FriendsViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = AppContainer.userRepository,
    private val friendshipRepository: FriendshipRepository =
        FriendshipRepository(),
    // Lazy so unit tests don't build the Firebase-backed tracker.
    private val trackFeature: (String) -> Unit = {
        AnalyticsDependencies.tracker.featureUsed(feature = it)
    },
    private val friendsStore: FriendsLocalStore = AppContainer.friendsStore,
    private val profileStore: ProfileLocalStore = AppContainer.profileStore,
    connectivity: ConnectivityObserver = AppContainer.connectivity,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pendingActions: PendingActionStore =
        AppContainer.pendingActions,
    private val syncScheduler: SyncScheduler = AppContainer.syncScheduler
) : ViewModel() {

    private val _myName = mutableStateOf("")
    val myName: State<String> = _myName

    private val _myCode = mutableStateOf("")
    val myCode: State<String> = _myCode

    private val _friends = mutableStateOf<List<Friend>>(emptyList())
    val friends: State<List<Friend>> = _friends

    private val _blockedUsers = mutableStateOf<List<Friend>>(emptyList())
    val blockedUsers: State<List<Friend>> = _blockedUsers

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _isSearching = mutableStateOf(false)
    val isSearching: State<Boolean> = _isSearching

    private val _foundUser = mutableStateOf<User?>(null)
    val foundUser: State<User?> = _foundUser

    private val _isSendingRequest = mutableStateOf(false)
    val isSendingRequest: State<Boolean> = _isSendingRequest

    private val _noticeMessage = mutableStateOf<String?>(null)
    val noticeMessage: State<String?> = _noticeMessage

    private val _noticeType = mutableStateOf(NoticeType.INFO)
    val noticeType: State<NoticeType> = _noticeType

    // Null when the lists are fresh from Firestore; otherwise explains that
    // they are the ones saved on this device (and when they were saved).
    private val _savedDataNotice = mutableStateOf<String?>(null)
    val savedDataNotice: State<String?> = _savedDataNotice

    // Raw friendships with the signed-in user (any status), kept around only
    // to check for an existing relationship before sending a new request -
    // not shown directly (see [friends] for the ACCEPTED/de-duplicated list).
    private var myFriendships: List<Friendship> = emptyList()

    private var loadJob: Job? = null

    // When the lists on screen were last saved on this device.
    private var savedAt: Long? = null

    // Confirmed requests and their delivery results, saved on this device.
    private val _friendRequests =
        mutableStateOf<List<StoredAction>>(emptyList())

    val friendRequests: State<List<StoredAction>> = _friendRequests

    private var requestStatusJob: Job? = null

    val isOnline: State<Boolean> = trackConnectivity(
        connectivity,
        onDisconnected = {
            markSavedData("You're offline.")
        },
        onReconnected = {
            reload()
            refreshFriendRequests(reschedule = true)
        }
    )

    init {
        reload()
        refreshFriendRequests(reschedule = true)
    }

    /** Saved lists first, then Firestore if online. */
    private fun reload() {
        val uid = authRepository.currentUserId ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            showSaved(uid)
            if (!isOnline.value) return@launch

            // My own profile and my friendships don't depend on each other:
            // both requests are in flight at the same time.
            coroutineScope {
                val me = async { userRepository.getUser(uid) }
                val loaded = async { loadFriendships(uid) }
                me.await().onSuccess { user ->
                    if (user != null) {
                        _myName.value = user.username
                        _myCode.value = user.code
                        profileStore.save(user)
                    }
                }
                if (!loaded.await()) markSavedData("Couldn't refresh your friends.")
            }
        }
    }

    /** Cache step of "cache then network": whatever this device saved last time. */
    private suspend fun showSaved(uid: String) {
        profileStore.get(uid)?.let { me ->
            if (_myName.value.isEmpty()) _myName.value = me.username
            if (_myCode.value.isEmpty()) _myCode.value = me.code
        }
        val saved = friendsStore.get(uid)
        if (saved != null && _friends.value.isEmpty() && _blockedUsers.value.isEmpty()) {
            _friends.value = saved.friends
            _blockedUsers.value = saved.blocked
            savedAt = saved.savedAt
        }
        if (!isOnline.value) markSavedData("You're offline.")
    }

    private fun markSavedData(reason: String) {
        val time = savedAt?.let { " (saved at ${timeLabel(it)})" }.orEmpty()
        _savedDataNotice.value = "$reason Showing the friends saved on this device$time."
    }

    /** Network step: reads Firestore, shows the result and saves it. False if Firestore failed. */
    private suspend fun loadFriendships(uid: String): Boolean {
        val friendships = friendshipRepository.getFriendships(uid).getOrElse { return false }
        myFriendships = friendships

        // One entry per ACCEPTED friend, even with duplicate friendship docs.
        val friendIds = FriendshipRepository.acceptedFriendIds(friendships, uid)
        val blockedIds = FriendshipRepository.blockedByMe(friendships, uid)
            .mapNotNull { friendship -> friendship.userIds.firstOrNull { it != uid } }

        // One users/{uid} read per person, all started at once: the wait is
        // the slowest read instead of the sum of all of them.
        coroutineScope {
            val friends = friendIds.map { id -> async { friendFor(id) } }
            val blocked = blockedIds.map { id -> async { friendFor(id) } }
            _friends.value = friends.awaitAll().filterNotNull()
            _blockedUsers.value = blocked.awaitAll().filterNotNull()
        }

        friendsStore.save(uid, _friends.value, _blockedUsers.value)
        savedAt = clock()
        _savedDataNotice.value = null
        return true
    }

    private suspend fun friendFor(userId: String): Friend? =
        userRepository.getUser(userId).getOrNull()?.let { Friend(name = it.username, userId = userId) }

    /** Any existing friendship (pending, accepted or blocked) with [otherId], regardless of who sent it. */
    private fun relationshipWith(otherId: String): Friendship? =
        myFriendships.firstOrNull { otherId in it.userIds }

    // Codes are generated uppercase-only (see RegisterViewModel.generateFriendCode);
    // Firestore's equality query is case-sensitive, so force the input to match
    // regardless of how the user typed it.
    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value.uppercase()
    }

    fun dismissNotice() {
        _noticeMessage.value = null
    }

    /** Looks up a user by their share code; shows them in [foundUser] for confirmation. */
    fun onSearchClick() {
        val uid = authRepository.currentUserId ?: return
        val code = _searchQuery.value.trim().uppercase()
        if (code.isBlank() || _isSearching.value) return
        if (refuseOffline()) return

        _isSearching.value = true
        _foundUser.value = null
        viewModelScope.launch {
            userRepository.findByCode(code)
                .onSuccess { found ->
                    when {
                        found == null -> showNotice("No user found with that code.", NoticeType.ERROR)
                        found.id == uid -> showNotice("That's your own code.", NoticeType.ERROR)
                        else -> when (relationshipWith(found.id)?.status) {
                            FriendshipStatus.ACCEPTED ->
                                showNotice("You're already friends with ${found.username}.", NoticeType.ERROR)
                            FriendshipStatus.PENDING ->
                                showNotice("There's already a pending request with ${found.username}.", NoticeType.ERROR)
                            FriendshipStatus.BLOCKED ->
                                showNotice("You can't send a request to this user.", NoticeType.ERROR)
                            null -> _foundUser.value = found
                        }
                    }
                }
                .onFailure { showNotice("Something went wrong. Please try again.", NoticeType.ERROR) }
            _isSearching.value = false
        }
    }

    /**
     * Confirms the card shown after a successful search.
     * Saves the request before scheduling delivery, even when currently online.
     * Only an explicit confirmation creates a queued request.
     */
    fun onConfirmSendRequest() {
        val uid = authRepository.currentUserId ?: return
        val target = _foundUser.value ?: return

        if (_isSendingRequest.value) return

        if (target.id == uid) {
            showNotice("That's your own code.", NoticeType.ERROR)
            return
        }

        _isSendingRequest.value = true

        viewModelScope.launch {
            var savedLocally = false

            try {
                val existing = pendingActions.pendingFor(uid).any { stored ->
                    val request =
                        stored.action as? PendingAction.SendFriendRequest

                    request?.targetUid == target.id &&
                            request.state == FriendRequestState.PENDING
                }

                currentCoroutineContext().ensureActive()

                if (authRepository.currentUserId != uid) {
                    return@launch
                }

                if (existing) {
                    showNotice(
                        "A request to ${target.username} is already waiting to be sent.",
                        NoticeType.INFO
                    )
                    refreshFriendRequests()
                    return@launch
                }

                pendingActions.enqueue(
                    PendingAction.SendFriendRequest(
                        ownerUid = uid,
                        targetUid = target.id,
                        targetName = target.username,
                        createdAt = clock()
                    )
                )

                savedLocally = true

                currentCoroutineContext().ensureActive()

                _searchQuery.value = ""
                _foundUser.value = null

                refreshFriendRequests()

                showNotice(
                    if (isOnline.value) {
                        "Request saved. Sending it when the connection is available."
                    } else {
                        "Request saved on this device. It will be sent when you're back online."
                    },
                    NoticeType.INFO
                )

                syncScheduler.scheduleSync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showNotice(
                    if (savedLocally) {
                        "Your request is saved. Open Friends again to retry scheduling delivery."
                    } else {
                        "Couldn't save your request. Please try again."
                    },
                    NoticeType.ERROR
                )
            } finally {
                _isSendingRequest.value = false
            }
        }
    }

    /** Removes a delivery result from this device, not the friendship in Firestore. */
    fun dismissFriendRequestResult(id: Long) {
        val uid = authRepository.currentUserId ?: return

        viewModelScope.launch {
            try {
                val stored = pendingActions.pendingFor(uid)
                    .firstOrNull { it.id == id }
                    ?: return@launch

                val request =
                    stored.action as? PendingAction.SendFriendRequest
                        ?: return@launch

                // Pending requests must remain available for background delivery.
                if (request.state == FriendRequestState.PENDING) {
                    return@launch
                }

                pendingActions.remove(id)
                refreshFriendRequests()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showNotice(
                    "Couldn't dismiss the request result. Please try again.",
                    NoticeType.ERROR
                )
            }
        }
    }

    /**
     * Reads request statuses from SQLite.
     * WorkManager notifications will call this after delivery changes.
     * Scheduling is requested only on entry or reconnection, not on every update.
     */
    fun refreshFriendRequests(reschedule: Boolean = false) {
        val uid = authRepository.currentUserId ?: return

        requestStatusJob?.cancel()

        requestStatusJob = viewModelScope.launch {
            try {
                val requests = pendingActions.pendingFor(uid).filter {
                    it.action is PendingAction.SendFriendRequest
                }

                currentCoroutineContext().ensureActive()

                if (authRepository.currentUserId != uid) {
                    return@launch
                }

                _friendRequests.value = requests

                val hasPending = requests.any {
                    val request =
                        it.action as PendingAction.SendFriendRequest

                    request.state == FriendRequestState.PENDING
                }

                if (reschedule && hasPending) {
                    syncScheduler.scheduleSync()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showNotice(
                    "Couldn't refresh your saved request statuses.",
                    NoticeType.ERROR
                )
            }
        }
    }

    /** Dismisses the confirmation card without sending anything. */
    fun onCancelSendRequest() {
        _foundUser.value = null
    }

    private fun showNotice(message: String, type: NoticeType) {
        _noticeMessage.value = message
        _noticeType.value = type
    }

    /** Network-only actions: true (and a message) when there is no connection. */
    private fun refuseOffline(): Boolean {
        if (isOnline.value) return false
        showNotice(OfflineMessages.FRIENDS_ACTION, NoticeType.ERROR)
        return true
    }

    /** Ends the friendship with [friend] (every doc between both users is deleted). */
    fun onRemoveFriend(friend: Friend) {
        runFriendAction(
            action = { uid -> friendshipRepository.removeFriend(uid, friend.userId) },
            success = "${friend.name} was removed from your friends."
        )
    }

    /** Blocks [friend]: no requests or meeting invites between you until you unblock them. */
    fun onBlock(friend: Friend) {
        runFriendAction(
            action = { uid -> friendshipRepository.block(uid, friend.userId) },
            success = "${friend.name} is blocked."
        )
    }

    fun onUnblock(friend: Friend) {
        val uid = authRepository.currentUserId ?: return
        if (refuseOffline()) return
        val friendship = FriendshipRepository.blockedByMe(myFriendships, uid)
            .firstOrNull { friend.userId in it.userIds } ?: return
        runFriendAction(
            action = { friendshipRepository.unblock(friendship.id) },
            success = "${friend.name} is unblocked. You can add them again with their code."
        )
    }

    /** Runs a friendship change, shows the result and reloads both lists. */
    private fun runFriendAction(action: suspend (uid: String) -> Result<Unit>, success: String) {
        val uid = authRepository.currentUserId ?: return
        if (refuseOffline()) return
        viewModelScope.launch {
            action(uid)
                .onSuccess { showNotice(success, NoticeType.INFO) }
                .onFailure { showNotice("Something went wrong. Please try again.", NoticeType.ERROR) }
            loadFriendships(uid)
        }
    }

    // The share sheet itself is opened by the screen (it needs a Context, see
    // util/ShareCode.kt); here we only record the feature use. "Contacts"
    // opens the same sheet so the user picks how to reach the contact.
    fun onShareCodeClick() = trackFeature("SHARE_CODE")
    fun onContactsClick() = trackFeature("SHARE_CODE_CONTACTS")
}
