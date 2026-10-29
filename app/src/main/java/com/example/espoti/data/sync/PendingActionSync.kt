package com.example.espoti.data.sync

import com.example.espoti.data.local.PendingAction
import com.example.espoti.data.local.PendingActionStore
import com.example.espoti.data.repository.UserRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Sends the offline changes of one user to Firestore, oldest first. Kept apart
 * from [SyncWorker] so it can be unit-tested without WorkManager.
 *
 * A sent action is removed. A failed one stays and its attempts grow; after
 * [maxAttempts] it is dropped so a change Firestore keeps rejecting doesn't
 * retry forever. Each call has a timeout because a Firestore write only
 * finishes when the server confirms it: on a network that says "connected"
 * but doesn't deliver, it would otherwise wait forever.
 */
class PendingActionSync(
    private val store: PendingActionStore,
    private val userRepository: UserRepository,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val timeoutMillis: Long = TIMEOUT_MILLIS
) {

    /** True when nothing is left to retry later. */
    suspend fun syncFor(uid: String): Boolean {
        var retryLater = false
        for (stored in store.pendingFor(uid)) {
            val sent = send(stored.action)
            when {
                sent -> store.remove(stored.id)
                stored.attempts + 1 >= maxAttempts -> store.remove(stored.id)
                else -> {
                    store.recordFailure(stored.id)
                    retryLater = true
                }
            }
        }
        return !retryLater
    }

    private suspend fun send(action: PendingAction): Boolean = try {
        withTimeout(timeoutMillis) {
            when (action) {
                is PendingAction.UpdateProfile ->
                    userRepository.updateProfileFields(action.ownerUid, action.description, action.preferences)
            }
        }.isSuccess
    } catch (timeout: TimeoutCancellationException) {
        false
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val TIMEOUT_MILLIS = 20_000L
    }
}
