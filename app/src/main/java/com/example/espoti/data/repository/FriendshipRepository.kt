package com.example.espoti.data.repository

import com.example.espoti.model.domain.Friendship
import com.example.espoti.model.domain.FriendshipStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Collection friendships/{id}; each document links exactly two users. */
class FriendshipRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val friendships = db.collection("friendships")

    suspend fun sendRequest(fromUid: String, toUid: String): Result<Unit> = runCatching {
        val ref = friendships.document()
        ref.set(
            Friendship(ref.id, listOf(fromUid, toUid), FriendshipStatus.PENDING, System.currentTimeMillis())
        ).await()
        Unit
    }

    suspend fun updateStatus(id: String, status: FriendshipStatus): Result<Unit> = runCatching {
        friendships.document(id).update("status", status.name).await()
        Unit
    }

    suspend fun getFriendships(uid: String): Result<List<Friendship>> = runCatching {
        friendships.whereArrayContains("userIds", uid).get().await()
            .documents.mapNotNull { d -> d.toObject(Friendship::class.java)?.copy(id = d.id) }
    }
}
