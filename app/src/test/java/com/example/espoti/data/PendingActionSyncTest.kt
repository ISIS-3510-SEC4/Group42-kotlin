package com.example.espoti.data

import com.example.espoti.data.local.InMemoryPendingActionStore
import com.example.espoti.data.local.PendingAction
import com.example.espoti.data.local.withPending
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.data.sync.PendingActionSync
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ============================================================================
// PENDING ACTION SYNC TEST (offline outbox)
// ----------------------------------------------------------------------------
// What the background worker does with changes saved offline.
// ============================================================================
class PendingActionSyncTest {

    private class FakeRemote(var result: Result<Unit> = Result.success(Unit)) :
        UserRepository(dbProvider = { error("no Firestore in unit tests") }) {
        val sent = mutableListOf<Triple<String, String, List<ActivityType>>>()
        var hang = false
        override suspend fun updateProfileFields(
            uid: String,
            description: String,
            preferences: List<ActivityType>
        ): Result<Unit> {
            if (hang) awaitCancellation()
            sent += Triple(uid, description, preferences)
            return result
        }
    }

    private fun update(description: String, at: Long = 1L) = PendingAction.UpdateProfile(
        ownerUid = "uid-1",
        description = description,
        preferences = listOf(ActivityType.COFFEE),
        createdAt = at
    )

    @Test
    fun sentAction_isRemoved() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        store.enqueue(update("hello"))

        val done = PendingActionSync(store, remote).syncFor("uid-1")

        assertTrue(done)
        assertEquals(listOf(Triple("uid-1", "hello", listOf(ActivityType.COFFEE))), remote.sent)
        assertTrue(store.all.isEmpty())
    }

    @Test
    fun newerEditReplacesTheOlderOne_soOnlyTheLatestIsSent() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        store.enqueue(update("first", at = 1))
        store.enqueue(update("second", at = 2))

        PendingActionSync(store, remote).syncFor("uid-1")

        assertEquals(listOf("second"), remote.sent.map { it.second })
    }

    @Test
    fun failedAction_staysAndAsksForARetry() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote(Result.failure(RuntimeException("offline")))
        store.enqueue(update("hello"))

        val done = PendingActionSync(store, remote).syncFor("uid-1")

        assertFalse(done)
        assertEquals(1, store.all.single().attempts)
    }

    @Test
    fun actionIsDroppedAfterTheLastAttempt() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote(Result.failure(RuntimeException("rejected")))
        store.enqueue(update("hello"))
        val sync = PendingActionSync(store, remote, maxAttempts = 2)

        sync.syncFor("uid-1")
        sync.syncFor("uid-1")

        assertTrue(store.all.isEmpty())
    }

    @Test
    fun writeThatNeverAnswers_timesOutAndIsRetriedLater() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote().apply { hang = true }
        store.enqueue(update("hello"))

        val done = PendingActionSync(store, remote, timeoutMillis = 1_000).syncFor("uid-1")

        assertFalse(done)
        assertEquals(1, store.all.size)
    }

    @Test
    fun otherUsersActions_areNotSent() = runTest {
        val store = InMemoryPendingActionStore()
        val remote = FakeRemote()
        store.enqueue(update("mine").copy(ownerUid = "uid-9"))

        PendingActionSync(store, remote).syncFor("uid-1")

        assertTrue(remote.sent.isEmpty())
        assertEquals(1, store.all.size)
    }

    @Test
    fun pendingEdit_isAppliedOnTopOfTheProfileShown() = runTest {
        val store = InMemoryPendingActionStore()
        store.enqueue(update("offline text"))

        val shown = User(id = "uid-1", username = "me", description = "old").withPending(store.all)

        assertEquals("offline text", shown.description)
        assertEquals(listOf(ActivityType.COFFEE), shown.preferences)
        assertEquals("me", shown.username)
    }
}
