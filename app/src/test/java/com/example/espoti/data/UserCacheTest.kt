package com.example.espoti.data

import com.example.espoti.data.cache.UserCache
import com.example.espoti.data.repository.CachingUserRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.domain.User
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// ============================================================================
// USER CACHE TEST (caching strategy)
// ----------------------------------------------------------------------------
// LruCache + expiration policy in front of users/{uid} reads.
// ============================================================================
class UserCacheTest {

    /** Counts how many reads reach "Firestore". */
    private class CountingUserRepository(users: List<User>) :
        UserRepository(dbProvider = { error("no Firestore in unit tests") }) {
        private val byId = users.associateBy { it.id }
        var reads = 0
        override suspend fun getUser(uid: String): Result<User?> {
            reads++
            return Result.success(byId[uid])
        }
        override suspend fun saveUser(user: User): Result<Unit> = Result.success(Unit)
    }

    private val ana = User(id = "uid-2", username = "ana")
    private val juan = User(id = "uid-3", username = "juan")

    @Test
    fun secondReadOfTheSameUser_doesNotGoToTheNetwork() = runTest {
        val remote = CountingUserRepository(listOf(ana))
        val repository = CachingUserRepository(remote, UserCache())

        assertEquals(ana, repository.getUser(ana.id).getOrNull())
        assertEquals(ana, repository.getUser(ana.id).getOrNull())

        assertEquals(1, remote.reads)
    }

    @Test
    fun expiredEntry_isReadAgain() = runTest {
        var now = 0L
        val remote = CountingUserRepository(listOf(ana))
        val repository = CachingUserRepository(remote, UserCache(ttlMillis = 1_000, clock = { now }))

        repository.getUser(ana.id)
        now = 1_001
        repository.getUser(ana.id)

        assertEquals(2, remote.reads)
    }

    @Test
    fun whenFull_theLeastRecentlyUsedUserIsEvicted() {
        val cache = UserCache(maxEntries = 2)
        val pedro = User(id = "uid-4", username = "pedro")

        cache.put(ana)
        cache.put(juan)
        cache.get(ana.id) // ana is now the most recently used
        cache.put(pedro)  // evicts juan

        assertEquals(ana, cache.get(ana.id))
        assertNull(cache.get(juan.id))
        assertEquals(pedro, cache.get(pedro.id))
    }

    @Test
    fun missingUsersAreNotCached() = runTest {
        val remote = CountingUserRepository(emptyList())
        val repository = CachingUserRepository(remote, UserCache())

        repository.getUser("nobody")
        repository.getUser("nobody")

        assertEquals(2, remote.reads)
    }

    @Test
    fun savingAUser_dropsItsCachedCopy() = runTest {
        val remote = CountingUserRepository(listOf(ana))
        val repository = CachingUserRepository(remote, UserCache())

        repository.getUser(ana.id)
        repository.saveUser(ana.copy(username = "ana2"))
        repository.getUser(ana.id)

        assertEquals(2, remote.reads)
    }
}
