package com.example.espoti.viewmodel

import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.model.Friend
import com.example.espoti.model.NoticeType
import com.example.espoti.model.domain.FriendshipStatus
import com.example.espoti.model.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ============================================================================
// FRIENDS VIEW MODEL TEST
// ----------------------------------------------------------------------------
// Remove / block / unblock with in-memory friendships (FakeFriendshipRepository).
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    private class SignedIn(private val uid: String) : AuthRepository(authProvider = { error("no auth") }) {
        override val currentUserId: String? get() = uid
    }

    private val ana = Friend(name = "ana", userId = "uid-2")
    private val juan = Friend(name = "juan", userId = "uid-3")
    private val users = listOf(
        User(id = "uid-1", username = "me", code = "ME1234"),
        User(id = ana.userId, username = ana.name),
        User(id = juan.userId, username = juan.name)
    )

    private lateinit var friendships: FakeFriendshipRepository
    private lateinit var vm: FriendsViewModel
    private val tracked = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        friendships = FakeFriendshipRepository(friendIds = listOf(ana.userId, juan.userId))
        vm = FriendsViewModel(
            authRepository = SignedIn("uid-1"),
            userRepository = FakeUserRepository(users),
            friendshipRepository = friendships,
            trackFeature = { tracked += it }
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsFriendsWithIds() {
        assertEquals(listOf(ana, juan), vm.friends.value)
        assertEquals("ME1234", vm.myCode.value)
    }

    @Test
    fun remove_deletesTheFriendship() {
        vm.onRemoveFriend(ana)

        assertEquals(listOf(juan), vm.friends.value)
        assertTrue(friendships.friendships.none { ana.userId in it.userIds })
        assertEquals(NoticeType.INFO, vm.noticeType.value)
    }

    @Test
    fun block_movesTheFriendToBlockedUsers() {
        vm.onBlock(ana)

        assertEquals(listOf(juan), vm.friends.value)
        assertEquals(listOf(ana), vm.blockedUsers.value)
        val stored = friendships.friendships.single { ana.userId in it.userIds }
        assertEquals(FriendshipStatus.BLOCKED, stored.status)
        assertEquals("uid-1", stored.blockedBy)
    }

    @Test
    fun unblock_removesTheBlock() {
        vm.onBlock(ana)
        vm.onUnblock(ana)

        assertTrue(vm.blockedUsers.value.isEmpty())
        assertTrue(friendships.friendships.none { ana.userId in it.userIds })
    }

    @Test
    fun blockedUserCode_cannotBeRequested() {
        vm.onBlock(ana)
        val anaWithCode = users[1].copy(code = "ANA123")
        val search = FriendsViewModel(
            authRepository = SignedIn("uid-1"),
            userRepository = object : com.example.espoti.data.repository.UserRepository(dbProvider = { error("no db") }) {
                override suspend fun getUser(uid: String) = Result.success(users.firstOrNull { it.id == uid })
                override suspend fun findByCode(code: String) = Result.success(anaWithCode.takeIf { code == "ANA123" })
            },
            friendshipRepository = friendships,
            trackFeature = {}
        )

        search.onSearchQueryChange("ana123")
        search.onSearchClick()

        assertEquals("You can't send a request to this user.", search.noticeMessage.value)
    }

    @Test
    fun shareAndContacts_areTracked() {
        vm.onShareCodeClick()
        vm.onContactsClick()
        assertEquals(listOf("SHARE_CODE", "SHARE_CODE_CONTACTS"), tracked)
    }
}
