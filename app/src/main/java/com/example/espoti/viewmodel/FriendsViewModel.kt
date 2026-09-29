package com.example.espoti.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.Friend
import com.example.espoti.model.NoticeType
import com.example.espoti.model.domain.Friendship
import com.example.espoti.model.domain.FriendshipStatus
import com.example.espoti.model.domain.User
import kotlinx.coroutines.launch

/**
 * Loads the signed-in user's own code and accepted friends from Firestore
 * (users/{uid}, friendships/{id}) and lets them add a new friend by code.
 * Searching a code doesn't send the request right away: it shows the found
 * user in [foundUser] first, and the request only goes out once the screen
 * calls [onConfirmSendRequest] (see FriendsScreen's confirmation card).
 * Accepting a pending request happens from the notifications panel (see
 * NotificationsViewModel), so only ACCEPTED friendships show up here.
 */
class FriendsViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val friendshipRepository: FriendshipRepository = FriendshipRepository()
) : ViewModel() {

    private val _myName = mutableStateOf("")
    val myName: State<String> = _myName

    private val _myCode = mutableStateOf("")
    val myCode: State<String> = _myCode

    private val _friends = mutableStateOf<List<Friend>>(emptyList())
    val friends: State<List<Friend>> = _friends

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

    // Raw friendships with the signed-in user (any status), kept around only
    // to check for an existing relationship before sending a new request -
    // not shown directly (see [friends] for the ACCEPTED/de-duplicated list).
    private var myFriendships: List<Friendship> = emptyList()

    init {
        val uid = authRepository.currentUserId
        if (uid != null) {
            viewModelScope.launch {
                userRepository.getUser(uid).onSuccess { user ->
                    _myName.value = user?.username.orEmpty()
                    _myCode.value = user?.code.orEmpty()
                }
                loadFriendships(uid)
            }
        }
    }

    private suspend fun loadFriendships(uid: String) {
        friendshipRepository.getFriendships(uid).onSuccess { friendships ->
            myFriendships = friendships

            // Two people can end up with more than one friendship doc between
            // them (e.g. requests sent both ways before this fix) - collapse
            // those to one entry per friend so the list never repeats a name.
            val friendIds = friendships
                .filter { it.status == FriendshipStatus.ACCEPTED }
                .mapNotNull { friendship -> friendship.userIds.firstOrNull { it != uid } }
                .distinct()

            _friends.value = friendIds.mapNotNull { friendId ->
                userRepository.getUser(friendId).getOrNull()?.let { Friend(it.username) }
            }
        }
    }

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

    /** Confirms the card shown after a successful search: sends the request for real. */
    fun onConfirmSendRequest() {
        val uid = authRepository.currentUserId ?: return
        val target = _foundUser.value ?: return
        if (_isSendingRequest.value) return

        _isSendingRequest.value = true
        viewModelScope.launch {
            friendshipRepository.sendRequest(uid, target.id)
                .onSuccess {
                    showNotice("Friend request sent to ${target.username}.", NoticeType.INFO)
                    _searchQuery.value = ""
                    _foundUser.value = null
                    // So a repeated search for this same code is caught as
                    // "already pending" instead of allowing a second request.
                    loadFriendships(uid)
                }
                .onFailure { showNotice("Something went wrong. Please try again.", NoticeType.ERROR) }
            _isSendingRequest.value = false
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

    // Not wired to a backend yet: sharing the code needs Android's share
    // sheet and reading contacts needs a real contacts picker + permission.
    fun onShareCodeClick() {}
    fun onContactsClick() {}
}
