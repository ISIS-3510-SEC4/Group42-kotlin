package com.example.espoti.data.repository

import com.example.espoti.model.domain.Friendship
import com.example.espoti.model.domain.FriendshipStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

/**
 * Collection friendships/{id}; each document links exactly two users.
 * `open` + lazy Firestore so ViewModel tests can subclass it without Firebase.
 */

enum class FriendRequestSendOutcome {
    SENT,
    ALREADY_PENDING,
    ALREADY_FRIENDS,
    BLOCKED,
    USER_NOT_FOUND
}
open class FriendshipRepository(
    dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private val friendships by lazy { dbProvider().collection("friendships") }

    /**
     * Uids of [uid]'s ACCEPTED friends. Two people can end up with more than
     * one friendship doc between them (requests sent both ways), so the ids
     * are de-duplicated: the list never repeats a friend.
     */
    open suspend fun getAcceptedFriendIds(uid: String): Result<List<String>> =
        getFriendships(uid).map { acceptedFriendIds(it, uid) }

    companion object {
        /** Pure part of [getAcceptedFriendIds], for callers that already loaded the friendships. */
        fun acceptedFriendIds(friendships: List<Friendship>, uid: String): List<String> =
            friendships
                .filter { it.status == FriendshipStatus.ACCEPTED }
                .mapNotNull { friendship -> friendship.userIds.firstOrNull { it != uid } }
                .distinct()

        /** Friendships [uid] blocked (shown in "Blocked users", only they can unblock). */
        fun blockedByMe(friendships: List<Friendship>, uid: String): List<Friendship> =
            friendships.filter { it.status == FriendshipStatus.BLOCKED && it.blockedBy == uid }

        /** Uids with a block between them and [uid], whoever blocked whom. */
        fun blockedUserIds(friendships: List<Friendship>, uid: String): Set<String> =
            friendships
                .filter { it.status == FriendshipStatus.BLOCKED }
                .mapNotNull { friendship -> friendship.userIds.firstOrNull { it != uid } }
                .toSet()
    }

    suspend fun sendRequest(fromUid: String, toUid: String): Result<Unit> = runCatching {
        val ref = friendships.document()
        ref.set(
            Friendship(ref.id, listOf(fromUid, toUid), FriendshipStatus.PENDING, System.currentTimeMillis())
        ).await()
        Unit
    }
    open suspend fun sendRequestSafely(
        fromUid: String,
        toUid: String
    ): Result<FriendRequestSendOutcome> {
        return try {
            require(fromUid.isNotBlank() && toUid.isNotBlank()) {
                "Both users are required."
            }
            require(fromUid != toUid) {
                "You cannot send a request to yourself."
            }

            val database = friendships.firestore

            // The same pair always uses the same document, in either direction.
            val pairKey = listOf(fromUid, toUid)
                .sorted()
                .joinToString(separator = "") { "${it.length}:$it" }

            val pairHash = MessageDigest.getInstance("SHA-256")
                .digest(pairKey.toByteArray(Charsets.UTF_8))
                .joinToString("") {
                    (it.toInt() and 0xff).toString(16).padStart(2, '0')
                }

            val requestRef = friendships.document("pair_$pairHash")

            // Include relationships created by the previous implementation.
            // SERVER prevents making this decision from stale cached data.
            val existingRefs = friendships
                .whereArrayContains("userIds", fromUid)
                .get(Source.SERVER)
                .await()
                .documents
                .filter { document ->
                    val users = document.get("userIds") as? List<*>
                    users?.contains(toUid) == true
                }
                .map { it.reference }

            val refsToCheck = (existingRefs + requestRef)
                .distinctBy { it.path }

            val recipientRef = database
                .collection("users")
                .document(toUid)

            val outcome = database.runTransaction { transaction ->
                // All reads happen before any write.
                val recipientExists = transaction.get(recipientRef).exists()

                val relationships = refsToCheck.mapNotNull { reference ->
                    transaction.get(reference)
                        .toObject(Friendship::class.java)
                }

                when {
                    relationships.any {
                        it.status == FriendshipStatus.BLOCKED
                    } -> FriendRequestSendOutcome.BLOCKED

                    !recipientExists ->
                        FriendRequestSendOutcome.USER_NOT_FOUND

                    relationships.any {
                        it.status == FriendshipStatus.ACCEPTED
                    } -> FriendRequestSendOutcome.ALREADY_FRIENDS

                    relationships.any {
                        it.status == FriendshipStatus.PENDING
                    } -> FriendRequestSendOutcome.ALREADY_PENDING

                    else -> {
                        transaction.set(
                            requestRef,
                            Friendship(
                                id = requestRef.id,
                                userIds = listOf(fromUid, toUid),
                                status = FriendshipStatus.PENDING,
                                dateAdded = System.currentTimeMillis()
                            )
                        )

                        FriendRequestSendOutcome.SENT
                    }
                }
            }.await()

            Result.success(outcome)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun updateStatus(id: String, status: FriendshipStatus): Result<Unit> = runCatching {
        friendships.document(id).update("status", status.name).await()
        Unit
    }

    open suspend fun getFriendships(uid: String): Result<List<Friendship>> = runCatching {
        friendships.whereArrayContains("userIds", uid).get().await()
            .documents.mapNotNull { d -> d.toObject(Friendship::class.java)?.copy(id = d.id) }
    }

    /** Declining a request: there's no "REJECTED" status, so it's just removed. */
    suspend fun deleteFriendship(id: String): Result<Unit> = runCatching {
        friendships.document(id).delete().await()
        Unit
    }

    /** Ends the friendship: deletes every doc between [uid] and [otherId] (duplicates included). */
    open suspend fun removeFriend(uid: String, otherId: String): Result<Unit> = runCatching {
        val between = between(uid, otherId)
        if (between.isEmpty()) return@runCatching
        val batch = friendships.firestore.batch()
        between.forEach { batch.delete(friendships.document(it.id)) }
        batch.commit().await()
        Unit
    }

    /**
     * [uid] blocks [otherId]: one doc stays between them with status BLOCKED
     * and blockedBy = uid (an existing friendship/request is reused, extra
     * duplicates are deleted, or a new doc is created). Written in one batch.
     */
    open suspend fun block(uid: String, otherId: String): Result<Unit> = runCatching {
        val between = between(uid, otherId)
        val batch = friendships.firestore.batch()
        val keep = between.firstOrNull()?.let { friendships.document(it.id) } ?: friendships.document()
        batch.set(
            keep,
            Friendship(
                id = keep.id,
                userIds = listOf(uid, otherId),
                status = FriendshipStatus.BLOCKED,
                dateAdded = System.currentTimeMillis(),
                blockedBy = uid
            )
        )
        between.drop(1).forEach { batch.delete(friendships.document(it.id)) }
        batch.commit().await()
        Unit
    }

    /** Lifts a block: the doc is deleted, so either user can send a new request. */
    open suspend fun unblock(friendshipId: String): Result<Unit> = deleteFriendship(friendshipId)

    private suspend fun between(uid: String, otherId: String): List<Friendship> =
        getFriendships(uid).getOrThrow().filter { otherId in it.userIds }
}
