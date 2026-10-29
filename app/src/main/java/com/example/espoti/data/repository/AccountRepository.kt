package com.example.espoti.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

// ============================================================================
// ACCOUNT REPOSITORY (delete account)
// ----------------------------------------------------------------------------
// Removes a user's data from Firestore: the users/{uid} document and every
// friendships/{id} document that includes them (accepted friends, pending
// requests both ways and blocks). The Firebase Auth account itself is deleted
// afterwards by AuthRepository.deleteCurrentUser (see DeleteAccountViewModel).
// Meetings are not touched: other participants keep their shared history.
// `open` + lazy Firestore so ViewModel tests can subclass it without Firebase.
// ============================================================================
open class AccountRepository(
    dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private val db by lazy(dbProvider)

    /**
     * Deletes users/{uid} and all of its friendships in one batch (several if
     * there are more than a batch allows), user document last. Reads from the
     * server so no friendship is missed because it wasn't in the local cache.
     * Returns how many friendships were deleted.
     */
    open suspend fun deleteUserData(uid: String): Result<Int> = runCatching {
        val friendships = db.collection("friendships")
            .whereArrayContains("userIds", uid)
            .get(Source.SERVER).await()
            .documents.map { it.reference }
        val refs = friendships + db.collection("users").document(uid)

        refs.chunked(MAX_WRITES_PER_BATCH).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
        friendships.size
    }

    private companion object {
        // Firestore's limit of writes in one batch.
        const val MAX_WRITES_PER_BATCH = 500
    }
}
