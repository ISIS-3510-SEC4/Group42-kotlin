package com.example.espoti.data.repository

import com.example.espoti.data.cache.FriendCodeCache
import com.example.espoti.data.cache.UserCache
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User
import java.util.Locale

/**
 * [UserRepository] with two independent in-memory caches:
 * - LRU user records, indexed by uid.
 * - Bounded friend-code search results, indexed by account and code.
 *
 * Writes go to Firestore and invalidate both cached representations.
 * Local-only reads are delegated explicitly to avoid network access.
 */
class CachingUserRepository(
    private val remote: UserRepository,
    private val cache: UserCache,
    private val codeCache: FriendCodeCache = FriendCodeCache(),
    private val currentUserId: () -> String? = {
        AuthRepository().currentUserId
    },
    private val onCodeCacheEvent: (String) -> Unit = {}
) : UserRepository(
    dbProvider = {
        error("CachingUserRepository delegates every call to remote")
    }
) {

    override suspend fun getUser(uid: String): Result<User?> {
        cache.get(uid)?.let {
            return Result.success(it)
        }

        return remote.getUser(uid).onSuccess { user ->
            user?.let(cache::put)
        }
    }

    /** A local-only read must never use the normal network fallback. */
    override suspend fun getCachedUser(uid: String): Result<User?> {
        cache.get(uid)?.let {
            return Result.success(it)
        }

        return remote.getCachedUser(uid).onSuccess { user ->
            user?.let(cache::put)
        }
    }

    override suspend fun findByCode(code: String): Result<User?> {
        val normalized = code.trim().uppercase(Locale.ROOT)
        val ownerUid = currentUserId()

        if (ownerUid.isNullOrBlank()) {
            return remote.findByCode(normalized)
        }

        codeCache.get(ownerUid, normalized)?.let {
            onCodeCacheEvent("HIT")
            return Result.success(it)
        }

        onCodeCacheEvent("MISS")

        return remote.findByCode(normalized).onSuccess { user ->
            // Never cache missing users or results under a changed session.
            if (
                user != null &&
                currentUserId() == ownerUid
            ) {
                codeCache.put(ownerUid, normalized, user)
            }
        }
    }

    override suspend fun findByUsername(
        username: String
    ): Result<User?> = remote.findByUsername(username)

    override suspend fun saveUser(user: User): Result<Unit> =
        remote.saveUser(user).also {
            invalidateUser(user.id)
        }

    override suspend fun updatePreferences(
        uid: String,
        preferences: List<ActivityType>
    ): Result<Unit> =
        remote.updatePreferences(uid, preferences).also {
            invalidateUser(uid)
        }

    override suspend fun updateProfileFields(
        uid: String,
        description: String,
        preferences: List<ActivityType>
    ): Result<Unit> =
        remote.updateProfileFields(uid, description, preferences).also {
            invalidateUser(uid)
        }

    override suspend fun setBiometricEnabled(
        uid: String,
        enabled: Boolean
    ): Result<Unit> =
        remote.setBiometricEnabled(uid, enabled).also {
            invalidateUser(uid)
        }

    private fun invalidateUser(uid: String) {
        cache.invalidate(uid)
        codeCache.invalidateUser(uid)
    }
}