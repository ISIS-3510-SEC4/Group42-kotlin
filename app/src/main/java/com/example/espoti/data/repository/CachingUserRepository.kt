package com.example.espoti.data.repository

import com.example.espoti.data.cache.UserCache
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.User

/**
 * [UserRepository] with an in-memory [UserCache] in front of [getUser]
 * ("cache, falling back to network"): a cached user is returned right away,
 * otherwise it is read from Firestore and remembered. Writes go straight to
 * Firestore and drop the cached copy of that user, so the next read is fresh.
 * Everything else is passed through to [remote] unchanged.
 */
class CachingUserRepository(
    private val remote: UserRepository,
    private val cache: UserCache
) : UserRepository(dbProvider = { error("CachingUserRepository delegates every call to remote") }) {

    override suspend fun getUser(uid: String): Result<User?> {
        cache.get(uid)?.let { return Result.success(it) }
        return remote.getUser(uid).onSuccess { user -> user?.let(cache::put) }
    }

    override suspend fun saveUser(user: User): Result<Unit> =
        remote.saveUser(user).also { cache.invalidate(user.id) }

    override suspend fun findByCode(code: String): Result<User?> = remote.findByCode(code)

    override suspend fun findByUsername(username: String): Result<User?> = remote.findByUsername(username)

    override suspend fun updatePreferences(uid: String, preferences: List<ActivityType>): Result<Unit> =
        remote.updatePreferences(uid, preferences).also { cache.invalidate(uid) }

    override suspend fun updateProfileFields(
        uid: String,
        description: String,
        preferences: List<ActivityType>
    ): Result<Unit> = remote.updateProfileFields(uid, description, preferences).also { cache.invalidate(uid) }

    override suspend fun setBiometricEnabled(uid: String, enabled: Boolean): Result<Unit> =
        remote.setBiometricEnabled(uid, enabled).also { cache.invalidate(uid) }
}
