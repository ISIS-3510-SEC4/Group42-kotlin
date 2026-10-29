package com.example.espoti.viewmodel

import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.InMemoryFriendsLocalStore
import com.example.espoti.data.local.InMemoryNotificationsLocalStore
import com.example.espoti.data.local.InMemoryPendingActionStore
import com.example.espoti.data.local.InMemoryProfileLocalStore
import com.example.espoti.data.local.PendingAction
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.data.sync.SyncScheduler
import com.example.espoti.model.AppNotification
import com.example.espoti.model.Friend
import com.example.espoti.model.NoticeType
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.Friendship
import com.example.espoti.model.domain.FriendshipStatus
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ============================================================================
// OFFLINE VIEW MODELS TEST (eventual connectivity, local storage, multithreading)
// ----------------------------------------------------------------------------
// Each scenario flips a fake connectivity observer and checks what the user
// would see: saved data instead of empty screens, specific messages instead
// of failed network calls, offline edits queued for a later sync.
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineViewModelsTest {

    // ---- Fakes -------------------------------------------------------------

    private class FakeConnectivity(online: Boolean) : ConnectivityObserver {
        override val isOnline = MutableStateFlow(online)
    }

    private class SignedIn(private val uid: String = ME) : AuthRepository(authProvider = { error("no auth") }) {
        var loginCalls = 0
        override val currentUserId: String? get() = uid
        override suspend fun login(email: String, password: String): Result<String> {
            loginCalls++
            return Result.success(uid)
        }
        override suspend fun register(email: String, password: String): Result<String> {
            loginCalls++
            return Result.success(uid)
        }
    }

    /** users/{uid} in memory; optionally holds every read until [gate] opens. */
    private class Users(users: List<User>) : UserRepository(dbProvider = { error("no Firestore") }) {
        val byId = users.associateBy { it.id }.toMutableMap()
        var reads = 0
        var started = 0
        var gate: CompletableDeferred<Unit>? = null
        val saved = mutableListOf<User>()
        var saveResult: Result<Unit> = Result.success(Unit)

        override suspend fun getUser(uid: String): Result<User?> {
            started++
            gate?.await()
            reads++
            return Result.success(byId[uid])
        }
        override suspend fun findByUsername(username: String): Result<User?> =
            Result.success(byId.values.firstOrNull { it.username == username })
        override suspend fun saveUser(user: User): Result<Unit> {
            saved += user
            return saveResult
        }
    }

    private class FailingFriendships : FriendshipRepository(dbProvider = { error("no Firestore") }) {
        override suspend fun getFriendships(uid: String): Result<List<Friendship>> =
            Result.failure(RuntimeException("unreachable"))
    }

    private class Invitations(private val meetings: List<MeetingEvent>) :
        MeetingEventRepository(dbProvider = { error("no Firestore") }) {
        override suspend fun getInvitationsFor(uid: String) = Result.success(meetings)
    }

    private class CountingScheduler : SyncScheduler {
        var calls = 0
        override fun scheduleSync() { calls++ }
    }

    private companion object {
        const val ME = "uid-1"
    }

    private val ana = Friend(name = "ana", userId = "uid-2")
    private val juan = Friend(name = "juan", userId = "uid-3")
    private val me = User(id = ME, username = "me", code = "ME1234", description = "old")
    private val users = listOf(me, User(id = ana.userId, username = ana.name), User(id = juan.userId, username = juan.name))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- EC1 / EC2: login and register are network only ---------------------

    @Test
    fun login_offline_doesNotCallFirebaseAndExplainsWhy() {
        val auth = SignedIn()
        val connectivity = FakeConnectivity(online = false)
        val vm = LoginViewModel(auth, connectivity)
        vm.onEmailChange("me@example.com")
        vm.onPasswordChange("secret123")

        var loggedIn = false
        vm.login { loggedIn = true }

        assertFalse(loggedIn)
        assertEquals(0, auth.loginCalls)
        assertEquals(OfflineMessages.LOGIN, vm.noticeMessage.value)

        // Back online: the offline message goes away by itself and login works.
        connectivity.isOnline.value = true
        assertNull(vm.noticeMessage.value)
        vm.login { loggedIn = true }
        assertTrue(loggedIn)
    }

    @Test
    fun register_offline_keepsTheFormAndExplainsWhy() {
        val auth = SignedIn()
        val vm = RegisterViewModel(auth, Users(emptyList()), FakeConnectivity(online = false))
        vm.onUsernameChange("newuser")
        vm.onEmailChange("new@example.com")
        vm.onConfirmEmailChange("new@example.com")
        vm.onPasswordChange("secret123")
        vm.onConfirmPasswordChange("secret123")

        vm.register { }

        assertEquals(0, auth.loginCalls)
        assertEquals(OfflineMessages.REGISTER, vm.noticeMessage.value)
        assertEquals("newuser", vm.username.value)
        assertFalse(vm.isLoading.value)
    }

    // ---- EC8: Friends, cache then network ----------------------------------

    @Test
    fun friends_offline_showsTheSavedListAndRefusesChanges() = runBlocking {
        val store = InMemoryFriendsLocalStore().apply { save(ME, listOf(ana), listOf(juan)) }
        val profiles = InMemoryProfileLocalStore().apply { save(me) }
        val remoteUsers = Users(users)

        val vm = FriendsViewModel(
            authRepository = SignedIn(),
            userRepository = remoteUsers,
            friendshipRepository = FakeFriendshipRepository(friendIds = listOf(ana.userId, juan.userId)),
            trackFeature = {},
            friendsStore = store,
            profileStore = profiles,
            connectivity = FakeConnectivity(online = false)
        )

        assertEquals(listOf(ana), vm.friends.value)
        assertEquals(listOf(juan), vm.blockedUsers.value)
        assertEquals("ME1234", vm.myCode.value)
        assertNotNull(vm.savedDataNotice.value)
        assertEquals(0, remoteUsers.reads)

        vm.onRemoveFriend(ana)
        assertEquals(OfflineMessages.FRIENDS_ACTION, vm.noticeMessage.value)
        assertEquals(NoticeType.ERROR, vm.noticeType.value)
        assertEquals(listOf(ana), vm.friends.value)
    }

    @Test
    fun friends_reconnecting_reloadsFromFirestoreAndSavesIt() = runBlocking {
        val store = InMemoryFriendsLocalStore()
        val connectivity = FakeConnectivity(online = false)
        val vm = FriendsViewModel(
            authRepository = SignedIn(),
            userRepository = Users(users),
            friendshipRepository = FakeFriendshipRepository(friendIds = listOf(ana.userId, juan.userId)),
            trackFeature = {},
            friendsStore = store,
            profileStore = InMemoryProfileLocalStore(),
            connectivity = connectivity
        )
        assertTrue(vm.friends.value.isEmpty())

        connectivity.isOnline.value = true

        assertEquals(listOf(ana, juan), vm.friends.value)
        assertNull(vm.savedDataNotice.value)
        assertEquals(listOf(ana, juan), store.get(ME)?.friends)
    }

    @Test
    fun friends_whenFirestoreFails_keepsTheSavedList() = runBlocking {
        val store = InMemoryFriendsLocalStore().apply { save(ME, listOf(ana), emptyList()) }
        val vm = FriendsViewModel(
            authRepository = SignedIn(),
            userRepository = Users(users),
            friendshipRepository = FailingFriendships(),
            trackFeature = {},
            friendsStore = store,
            profileStore = InMemoryProfileLocalStore(),
            connectivity = FakeConnectivity(online = true)
        )

        assertEquals(listOf(ana), vm.friends.value)
        assertTrue(vm.savedDataNotice.value!!.startsWith("Couldn't refresh"))
    }

    // ---- MT2: usernames are read in parallel -------------------------------

    @Test
    fun friends_readsEveryUsernameAtTheSameTime() {
        val remoteUsers = Users(users).apply { gate = CompletableDeferred() }
        val vm = FriendsViewModel(
            authRepository = SignedIn(),
            userRepository = remoteUsers,
            friendshipRepository = FakeFriendshipRepository(friendIds = listOf(ana.userId, juan.userId)),
            trackFeature = {},
            friendsStore = InMemoryFriendsLocalStore(),
            profileStore = InMemoryProfileLocalStore(),
            connectivity = FakeConnectivity(online = true)
        )

        // Nothing has answered yet, but all 3 reads (me, ana, juan) already
        // started: none of them waited for another one to finish.
        assertEquals(3, remoteUsers.started)
        assertEquals(0, remoteUsers.reads)

        remoteUsers.gate!!.complete(Unit)
        assertEquals(listOf(ana, juan), vm.friends.value)
    }

    // ---- EC9: notifications, network falling back to cache -----------------

    @Test
    fun notifications_online_areSavedForLater() = runBlocking {
        val store = InMemoryNotificationsLocalStore()
        val friendships = FakeFriendshipRepository().apply {
            friendships += Friendship(id = "req-1", userIds = listOf(ana.userId, ME), status = FriendshipStatus.PENDING)
        }
        val vm = NotificationsViewModel(
            authRepository = SignedIn(),
            userRepository = Users(users),
            friendshipRepository = friendships,
            meetingRepository = Invitations(emptyList()),
            notificationsStore = store,
            connectivity = FakeConnectivity(online = true)
        )

        val expected = listOf(AppNotification.FriendInvite(id = "req-1", fromUserId = ana.userId, fromName = "ana"))
        assertEquals(expected, vm.notifications.value)
        assertEquals(expected, store.get(ME)?.notifications)
        assertNull(vm.savedDataNotice.value)
    }

    @Test
    fun notifications_offline_showTheSavedOnesAndRefuseAnswers() = runBlocking {
        val saved = AppNotification.FriendInvite(id = "req-1", fromUserId = ana.userId, fromName = "ana")
        val store = InMemoryNotificationsLocalStore().apply { save(ME, listOf(saved)) }
        val friendships = FakeFriendshipRepository()
        val vm = NotificationsViewModel(
            authRepository = SignedIn(),
            userRepository = Users(users),
            friendshipRepository = friendships,
            meetingRepository = Invitations(emptyList()),
            notificationsStore = store,
            connectivity = FakeConnectivity(online = false)
        )

        assertEquals(listOf(saved), vm.notifications.value)
        assertNotNull(vm.savedDataNotice.value)

        vm.onAcceptFriendInvite(saved)
        assertEquals(OfflineMessages.NOTIFICATION_ACTION, vm.actionError.value)
        assertEquals(listOf(saved), vm.notifications.value)
    }

    @Test
    fun notifications_whenFirestoreFails_fallBackToTheSavedOnes() = runBlocking {
        val saved = AppNotification.FriendInvite(id = "req-1", fromUserId = ana.userId, fromName = "ana")
        val store = InMemoryNotificationsLocalStore().apply { save(ME, listOf(saved)) }
        val vm = NotificationsViewModel(
            authRepository = SignedIn(),
            userRepository = Users(users),
            friendshipRepository = FailingFriendships(),
            meetingRepository = Invitations(emptyList()),
            notificationsStore = store,
            connectivity = FakeConnectivity(online = true)
        )

        assertEquals(listOf(saved), vm.notifications.value)
        assertTrue(vm.savedDataNotice.value!!.startsWith("Couldn't refresh"))
    }

    // ---- EC10: edit profile offline is queued ------------------------------

    @Test
    fun editProfile_offline_savesOnTheDeviceAndQueuesTheChange() = runBlocking {
        val profiles = InMemoryProfileLocalStore().apply { save(me) }
        val outbox = InMemoryPendingActionStore()
        val scheduler = CountingScheduler()
        val remoteUsers = Users(users)
        val vm = EditProfileViewModel(
            authRepository = SignedIn(),
            userRepository = remoteUsers,
            profileStore = profiles,
            pendingActions = outbox,
            syncScheduler = scheduler,
            connectivity = FakeConnectivity(online = false),
            onProfileChanged = {},
            clock = { 42L }
        )
        assertEquals("old", vm.description.value) // loaded from the device

        vm.onDescriptionChange("written offline")
        vm.onPreferencesChange("coffee, bar")
        var done = false
        vm.onSave { done = true }

        assertTrue(done)
        assertTrue(remoteUsers.saved.isEmpty())
        val queued = outbox.all.single().action as PendingAction.UpdateProfile
        assertEquals("written offline", queued.description)
        assertEquals(listOf(ActivityType.COFFEE, ActivityType.BAR), queued.preferences)
        assertEquals(1, scheduler.calls)
        assertEquals("written offline", profiles.get(ME)?.description)
        assertEquals(OfflineMessages.PROFILE_SAVED_OFFLINE, vm.noticeMessage.value)
    }

    @Test
    fun editProfile_offline_refusesANewUsername() {
        val outbox = InMemoryPendingActionStore()
        val vm = EditProfileViewModel(
            authRepository = SignedIn(),
            userRepository = Users(users),
            profileStore = InMemoryProfileLocalStore().apply { runBlocking { save(me) } },
            pendingActions = outbox,
            syncScheduler = CountingScheduler(),
            connectivity = FakeConnectivity(online = false),
            onProfileChanged = {}
        )

        vm.onUsernameChange("someone-else")
        var done = false
        vm.onSave { done = true }

        assertFalse(done)
        assertTrue(vm.usernameError.value)
        assertEquals(OfflineMessages.USERNAME_CHANGE, vm.noticeMessage.value)
        assertTrue(outbox.all.isEmpty())
    }

    @Test
    fun editProfile_online_whenFirestoreFails_queuesTheChangeInstead() = runBlocking {
        val outbox = InMemoryPendingActionStore()
        val remoteUsers = Users(users).apply { saveResult = Result.failure(RuntimeException("dropped")) }
        val vm = EditProfileViewModel(
            authRepository = SignedIn(),
            userRepository = remoteUsers,
            profileStore = InMemoryProfileLocalStore(),
            pendingActions = outbox,
            syncScheduler = CountingScheduler(),
            connectivity = FakeConnectivity(online = true),
            onProfileChanged = {}
        )

        vm.onDescriptionChange("new text")
        var done = false
        vm.onSave { done = true }

        assertTrue(done)
        assertEquals("new text", (outbox.all.single().action as PendingAction.UpdateProfile).description)
    }

    @Test
    fun editProfile_onlineSave_dropsAnOlderOfflineEdit() = runBlocking {
        val outbox = InMemoryPendingActionStore().apply {
            enqueue(PendingAction.UpdateProfile(ME, "stale offline edit", emptyList(), createdAt = 1))
        }
        val vm = EditProfileViewModel(
            authRepository = SignedIn(),
            userRepository = Users(users),
            profileStore = InMemoryProfileLocalStore(),
            pendingActions = outbox,
            syncScheduler = CountingScheduler(),
            connectivity = FakeConnectivity(online = true),
            onProfileChanged = {}
        )

        vm.onDescriptionChange("newer online edit")
        vm.onSave { }

        assertTrue(outbox.all.isEmpty())
    }

    @Test
    fun profile_showsChangesStillWaitingToSync() = runBlocking {
        val outbox = InMemoryPendingActionStore().apply {
            enqueue(PendingAction.UpdateProfile(ME, "offline text", listOf(ActivityType.PARK), createdAt = 1))
        }
        val vm = ProfileViewModel(
            authRepository = SignedIn(),
            userRepository = Users(users),
            profileStore = InMemoryProfileLocalStore(),
            pendingActions = outbox,
            connectivity = FakeConnectivity(online = true),
            clearLocalData = {}
        )

        assertEquals("offline text", vm.profile.value?.description)
        assertEquals(listOf(ActivityType.PARK), vm.profile.value?.preferences)
        assertTrue(vm.hasPendingSync.value)
    }

    @Test
    fun profile_offline_showsTheSavedProfile() = runBlocking {
        val remoteUsers = Users(users)
        val vm = ProfileViewModel(
            authRepository = SignedIn(),
            userRepository = remoteUsers,
            profileStore = InMemoryProfileLocalStore().apply { save(me) },
            pendingActions = InMemoryPendingActionStore(),
            connectivity = FakeConnectivity(online = false),
            clearLocalData = {}
        )

        assertEquals("me", vm.profile.value?.username)
        assertEquals(0, remoteUsers.reads)
    }
}
