package com.example.espoti.data.repository

import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Source

/** Collection users/{uid}; the document id is the Firebase Auth uid. */
open class UserRepository(
    dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private val users by lazy { dbProvider().collection("users") }

    open suspend fun saveUser(user: User): Result<Unit> = runCatching {
        users.document(user.id).set(user.copy()).await()
        Unit
    }

    open suspend fun getUser(uid: String): Result<User?> = runCatching {
        users.document(uid).get().await().toObject(User::class.java)?.copy(id = uid)
    }
    open suspend fun getCachedUser(uid: String): Result<User?> =
        runCatching {
            users.document(uid)
                .get(Source.CACHE)
                .await()
                .toObject(User::class.java)
                ?.copy(id = uid)
        }

    /** Used to add friends by their share code. */
    open suspend fun findByCode(code: String): Result<User?> = runCatching {
        users.whereEqualTo("code", code).limit(1).get().await()
            .documents.firstOrNull()?.let { it.toObject(User::class.java)?.copy(id = it.id) }
    }

    /** Used by Register to enforce unique usernames. */
    open suspend fun findByUsername(username: String): Result<User?> = runCatching {
        users.whereEqualTo("username", username).limit(1).get().await()
            .documents.firstOrNull()?.let { it.toObject(User::class.java)?.copy(id = it.id) }
    }

    open suspend fun updatePreferences(uid: String, preferences: List<ActivityType>): Result<Unit> = runCatching {
        users.document(uid).update("preferences", preferences.map { it.name }).await()
        Unit
    }

    /**
     * Sends a profile edit made offline (see SyncWorker). Only the fields that
     * can be edited offline; setting the same values twice is harmless, so a
     * retry after a timeout is safe.
     */
    open suspend fun updateProfileFields(
        uid: String,
        description: String,
        preferences: List<ActivityType>
    ): Result<Unit> = runCatching {
        users.document(uid).update(
            "description", description,
            "preferences", preferences.map { it.name }
        ).await()
        Unit
    }

    /** Mirrors on the backend (Firestore) whether the user enrolled biometric login. */
    open suspend fun setBiometricEnabled(uid: String, enabled: Boolean): Result<Unit> = runCatching {
        users.document(uid).update("biometricEnabled", enabled).await()
        Unit
    }
}
