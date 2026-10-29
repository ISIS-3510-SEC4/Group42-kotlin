package com.example.espoti.data.cache

import androidx.collection.LruCache
import com.example.espoti.model.domain.User

// ============================================================================
// USER CACHE (in-memory caching strategy)
// ----------------------------------------------------------------------------
// Friends, the notifications panel and the "Who will go?" dialog all show
// usernames, and each one used to read users/{uid} from Firestore every time.
// This keeps recently used users in memory, shared by the whole app.
//
// LruCache: when it is full, the Least Recently Used user is evicted (the
// friends you look at most stay). It holds strong references, so its size is
// bounded by a number of entries: a User is a few short strings, so 100
// entries is a few KB - enough for anyone's friend list without any risk of
// running out of memory. Too small would evict friends while scrolling; too
// big only wastes memory. androidx.collection.LruCache has the same API as
// android.util.LruCache but also runs in JVM unit tests. It is thread-safe.
//
// Expiration policy: an entry older than [ttlMillis] is treated as missing,
// so a renamed user shows the new name after at most 10 minutes.
// ============================================================================
class UserCache(
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class Entry(val user: User, val storedAt: Long)

    private val cache = LruCache<String, Entry>(maxEntries)

    /** The cached user, or null if missing or expired. */
    fun get(uid: String): User? {
        val entry = cache[uid] ?: return null
        if (clock() - entry.storedAt > ttlMillis) {
            cache.remove(uid)
            return null
        }
        return entry.user
    }

    fun put(user: User) {
        if (user.id.isNotBlank()) cache.put(user.id, Entry(user, clock()))
    }

    fun invalidate(uid: String) {
        cache.remove(uid)
    }

    fun clear() = cache.evictAll()

    val size: Int get() = cache.size()

    companion object {
        const val DEFAULT_MAX_ENTRIES = 100
        const val DEFAULT_TTL_MILLIS = 10 * 60 * 1000L
    }
}
