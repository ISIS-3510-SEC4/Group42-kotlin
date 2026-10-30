package com.example.espoti.data

import com.example.espoti.data.local.FriendRequestState
import com.example.espoti.data.local.InMemoryPendingActionStore
import com.example.espoti.data.local.PendingAction
import com.example.espoti.data.repository.FriendRequestSendOutcome
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.data.sync.PendingActionSync
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Delivery behavior without Firebase or WorkManager. */
class FriendRequestSyncTest {

    private class FakeRemote : FriendshipRepository(
        dbProvider = { error("No Firestore in unit tests") }
    ) {
        var result: Result<FriendRequestSendOutcome> =
            Result.success(FriendRequestSendOutcome.SENT)

        var hang = false

        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun sendRequestSafely(
            fromUid: String,
            toUid: String
        ): Result<FriendRequestSendOutcome> {
            calls += fromUid to toUid

            if (hang) {
                awaitCancellation()
            }

            return result
        }
    }

    private fun request(
        ownerUid: String = "owner",
        targetUid: String = "target"
    ) = PendingAction.SendFriendRequest(
        ownerUid = ownerUid,
        targetUid = targetUid,
        targetName = "Ana",
        createdAt = 1L
    )

    private fun synchronizer(
        store: InMemoryPendingActionStore,
        remote: FakeRemote,
        maxAttempts: Int = 5,
        timeoutMillis: Long = 20_000L
    ) = PendingActionSync(
        store = store,
        userRepository = UserRepository(
            dbProvider = { error("No Firestore in unit tests") }
        ),
        maxAttempts = maxAttempts,
        timeoutMillis = timeoutMillis,
        friendshipRepository = remote
    )

    @Test
    fun successfulRequest_keepsResult_andIsNotSentAgain() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        val sync = synchronizer(store, remote)

        store.enqueue(request())

        assertTrue(sync.syncFor("owner"))

        val saved = store.all.single().action
                as PendingAction.SendFriendRequest

        assertEquals(FriendRequestState.COMPLETED, saved.state)
        assertNotNull(saved.resultMessage)

        assertTrue(sync.syncFor("owner"))
        assertEquals(1, remote.calls.size)
    }

    @Test
    fun temporaryFailure_keepsPending_thenSucceeds() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        val sync = synchronizer(store, remote)

        store.enqueue(request())
        remote.result = Result.failure(
            IllegalStateException("Connection unavailable")
        )

        assertFalse(sync.syncFor("owner"))
        assertEquals(1, store.all.single().attempts)

        val pending = store.all.single().action
                as PendingAction.SendFriendRequest

        assertEquals(FriendRequestState.PENDING, pending.state)

        remote.result = Result.success(FriendRequestSendOutcome.SENT)

        assertTrue(sync.syncFor("owner"))

        val completed = store.all.single().action
                as PendingAction.SendFriendRequest

        assertEquals(FriendRequestState.COMPLETED, completed.state)
    }

    @Test
    fun blockedRequest_keepsFailure_andIsNotRetried() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        val sync = synchronizer(store, remote)

        store.enqueue(request())
        remote.result = Result.success(FriendRequestSendOutcome.BLOCKED)

        assertTrue(sync.syncFor("owner"))

        val failed = store.all.single().action
                as PendingAction.SendFriendRequest

        assertEquals(FriendRequestState.FAILED, failed.state)
        assertNotNull(failed.resultMessage)

        assertTrue(sync.syncFor("owner"))
        assertEquals(1, remote.calls.size)
    }

    @Test
    fun exhaustedRetries_keepsVisibleFailure() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        val sync = synchronizer(store, remote, maxAttempts = 2)

        store.enqueue(request())
        remote.result = Result.failure(
            IllegalStateException("Connection unavailable")
        )

        assertFalse(sync.syncFor("owner"))
        assertTrue(sync.syncFor("owner"))

        val failed = store.all.single().action
                as PendingAction.SendFriendRequest

        assertEquals(FriendRequestState.FAILED, failed.state)
        assertNotNull(failed.resultMessage)

        sync.syncFor("owner")
        assertEquals(2, remote.calls.size)
    }

    @Test
    fun accountsKeepSeparateRequests_andOnlyCurrentOwnerIsSynced() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        val sync = synchronizer(store, remote)

        store.enqueue(request(ownerUid = "owner"))
        store.enqueue(request(ownerUid = "other-owner"))

        assertEquals(2, store.all.size)
        assertTrue(sync.syncFor("owner"))

        assertEquals(
            listOf("owner" to "target"),
            remote.calls
        )

        val other = store.pendingFor("other-owner")
            .single().action as PendingAction.SendFriendRequest

        assertEquals(FriendRequestState.PENDING, other.state)
    }

    @Test
    fun timeout_leavesRequestPendingForRetry() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        val sync = synchronizer(
            store,
            remote,
            timeoutMillis = 1_000L
        )

        store.enqueue(request())
        remote.hang = true

        assertFalse(sync.syncFor("owner"))

        val pending = store.all.single().action
                as PendingAction.SendFriendRequest

        assertEquals(FriendRequestState.PENDING, pending.state)
        assertEquals(1, store.all.single().attempts)
    }
}