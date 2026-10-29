package com.example.espoti.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.di.AppContainer
import java.util.concurrent.TimeUnit

// ============================================================================
// BACKGROUND SYNC (eventual connectivity + multithreading)
// ----------------------------------------------------------------------------
// WorkManager runs SyncWorker on a background thread only once the device has
// a network (NetworkType.CONNECTED), and keeps that promise even if the user
// closes the app or the phone restarts. If sending fails it retries later with
// exponential backoff (30 s, 60 s, 120 s, ...).
// ============================================================================

/** Asks for the offline changes to be sent as soon as there is internet. */
interface SyncScheduler {
    fun scheduleSync()
}

/** Unit tests: does nothing. */
object NoOpSyncScheduler : SyncScheduler {
    override fun scheduleSync() = Unit
}

class WorkManagerSyncScheduler(context: Context) : SyncScheduler {
    private val appContext = context.applicationContext

    override fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // One sync chain for the whole app. APPEND_OR_REPLACE: if a sync is
        // already running, this one runs right after it, so a change saved
        // while the previous run was sending is never skipped.
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "espoti-sync"
    }
}

/**
 * Sends the signed-in user's pending actions. Changes of a user who logged out
 * stay saved and are sent the next time that same user logs in (the app
 * schedules a sync on every start), never with another account.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = AuthRepository().currentUserId ?: return Result.success()
        // The plain (uncached) repository: this is a write to Firestore.
        val sync = PendingActionSync(AppContainer.pendingActions, UserRepository())
        return if (sync.syncFor(uid)) Result.success() else Result.retry()
    }
}
