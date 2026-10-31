package com.example.espoti.di

import android.content.Context
import com.example.espoti.data.cache.UserCache
import com.example.espoti.data.connectivity.AlwaysOnlineConnectivity
import com.example.espoti.data.connectivity.AndroidConnectivityObserver
import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.local.EspotiDatabase
import com.example.espoti.data.local.FriendsLocalStore
import com.example.espoti.data.local.InMemoryFriendsLocalStore
import com.example.espoti.data.local.InMemoryNotificationsLocalStore
import com.example.espoti.data.local.InMemoryPendingActionStore
import com.example.espoti.data.local.InMemoryProfileLocalStore
import com.example.espoti.data.local.NotificationsLocalStore
import com.example.espoti.data.local.PendingActionStore
import com.example.espoti.data.local.ProfileLocalStore
import com.example.espoti.data.local.SqliteFriendsLocalStore
import com.example.espoti.data.local.SqliteNotificationsLocalStore
import com.example.espoti.data.local.SqlitePendingActionStore
import com.example.espoti.data.local.SqliteProfileLocalStore
import com.example.espoti.data.repository.CachingUserRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.data.sync.NoOpSyncScheduler
import com.example.espoti.data.sync.SyncScheduler
import com.example.espoti.data.sync.WorkManagerSyncScheduler
import com.example.espoti.security.BiometricCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.espoti.data.local.FileFriendSearchDraftStore
import com.example.espoti.data.local.FriendSearchDraftStore
import com.example.espoti.data.local.InMemoryFriendSearchDraftStore
import java.io.File
import com.example.espoti.data.local.TransportFilterPreferences
import com.example.espoti.data.local.SharedPreferencesTransportFilterPreferences
import com.example.espoti.data.local.InMemoryTransportFilterPreferences
import android.util.Log
import android.content.pm.ApplicationInfo
import com.example.espoti.data.cache.FriendCodeCache

// ============================================================================
// APP CONTAINER (manual dependency injection for the Sprint 3 layers)
// ----------------------------------------------------------------------------
// One shared instance of each offline piece: connectivity observer, SQLite
// database and its stores, user cache and sync scheduler. ViewModels take
// them as constructor defaults (same pattern as AnalyticsDependencies).
//
// EspotiApp calls init() when the process starts. Before that - i.e. in JVM
// unit tests, where there is no Android Context - every getter returns a
// harmless stand-in (always online, fresh in-memory stores, no-op scheduler),
// so existing tests don't need to know about any of this.
// ============================================================================
object AppContainer {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val context: Context
        get() = checkNotNull(appContext) { "AppContainer.init() was not called" }

    private val isReady: Boolean get() = appContext != null

    /** Work that must finish even if the screen that started it is closed. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Connectivity -------------------------------------------------------

    private val androidConnectivity by lazy { AndroidConnectivityObserver(context) }

    val connectivity: ConnectivityObserver
        get() = if (isReady) androidConnectivity else AlwaysOnlineConnectivity

    // ---- Local storage (SQLite) ---------------------------------------------

    private val database by lazy { EspotiDatabase(context) }
    private val sqliteProfiles by lazy { SqliteProfileLocalStore(database) }
    private val sqliteFriends by lazy { SqliteFriendsLocalStore(database) }
    private val sqliteNotifications by lazy { SqliteNotificationsLocalStore(database) }
    private val sqlitePendingActions by lazy { SqlitePendingActionStore(database) }

    val profileStore: ProfileLocalStore
        get() = if (isReady) sqliteProfiles else InMemoryProfileLocalStore()

    val friendsStore: FriendsLocalStore
        get() = if (isReady) sqliteFriends else InMemoryFriendsLocalStore()

    val notificationsStore: NotificationsLocalStore
        get() = if (isReady) sqliteNotifications else InMemoryNotificationsLocalStore()

    val pendingActions: PendingActionStore
        get() = if (isReady) sqlitePendingActions else InMemoryPendingActionStore()


    // ---- Local files -------------------------------------------------------

    private val fileFriendSearchDrafts by lazy {
        FileFriendSearchDraftStore(
            File(context.filesDir, "friend_search_drafts")
        )
    }

    val friendSearchDraftStore: FriendSearchDraftStore
        get() = if (isReady) {
            fileFriendSearchDrafts
        } else {
            InMemoryFriendSearchDraftStore()
        }
    // ---- Preferences -------------------------------------------------------

    private val savedTransportFilters by lazy {
        SharedPreferencesTransportFilterPreferences(context)
    }

    val transportFilterPreferences: TransportFilterPreferences
        get() = if (isReady) {
            savedTransportFilters
        } else {
            InMemoryTransportFilterPreferences()
        }
    // ---- Caching ------------------------------------------------------------
// ---- Caching ------------------------------------------------------------

    private val sharedUserCache by lazy { UserCache() }
    private val sharedFriendCodeCache by lazy { FriendCodeCache() }

    private val sharedUserRepository by lazy {
        CachingUserRepository(
            remote = UserRepository(),
            cache = sharedUserCache,
            codeCache = sharedFriendCodeCache,
            onCodeCacheEvent = { event ->
                // Debug diagnostics contain no codes, names or account ids.
                val isDebuggable =
                    (context.applicationInfo.flags and
                            ApplicationInfo.FLAG_DEBUGGABLE) != 0

                if (isDebuggable) {
                    Log.d("EspotiCache", "Friend code cache: $event")
                }
            }
        )
    }

    val userCache: UserCache
        get() = if (isReady) sharedUserCache else UserCache()

    val friendCodeCache: FriendCodeCache
        get() = if (isReady) {
            sharedFriendCodeCache
        } else {
            FriendCodeCache()
        }

    /** User reads and friend-code lookups share the app-wide caches. */
    val userRepository: UserRepository
        get() = if (isReady) {
            sharedUserRepository
        } else {
            CachingUserRepository(
                remote = UserRepository(),
                cache = UserCache(),
                currentUserId = { null }
            )
        }
    // ---- Background sync ----------------------------------------------------

    private val workManagerSync by lazy { WorkManagerSyncScheduler(context) }

    val syncScheduler: SyncScheduler
        get() = if (isReady) workManagerSync else NoOpSyncScheduler

    /**
     * Logout: forget what was saved for [uid] (profile, friends, notifications,
     * cached users), so the next account on this phone never sees it. Pending
     * changes are kept: they are sent when the same user logs in again.
     */
    fun clearUserData(uid: String) {
        userCache.clear()
        friendCodeCache.clear()
        val profiles = profileStore
        val friends = friendsStore
        val notifications = notificationsStore
        appScope.launch {
            profiles.clear(uid)
            friends.clear(uid)
            notifications.clear(uid)
        }
    }

    /**
     * Delete account: like [clearUserData], but also drops the changes still
     * waiting to sync (the account no longer exists) and the biometric login
     * saved on this device for [uid].
     */
    fun forgetDeletedAccount(uid: String) {
        clearUserData(uid)
        val pending = pendingActions
        appScope.launch {
            pending.pendingFor(uid).forEach { pending.remove(it.id) }
        }
        if (isReady) {
            BiometricCredentialStore(context).takeIf { it.isEnrolledFor(uid) }?.clear()
        }
    }
}
