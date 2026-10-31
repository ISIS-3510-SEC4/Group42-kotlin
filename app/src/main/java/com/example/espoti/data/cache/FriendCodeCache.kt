package com.example.espoti.data.cache

import androidx.collection.ArrayMap
import com.example.espoti.model.domain.User
import java.util.Locale

/**
 * Small in-memory cache for successful friend-code lookups.
 *
 * ArrayMap stores keys and values in compact arrays. It suits this small,
 * bounded collection; it is not intended for large datasets.
 *
 * Entries expire after one minute. When full, the oldest stored entry is
 * removed. Reads do not change eviction order: this is FIFO, not LRU.
 *
 * Account ids are part of the key. Synchronization protects ArrayMap when
 * callers use different coroutine dispatchers.
 */
class FriendCodeCache(
    private val maxEntries: Int = 32,
    private val ttlMillis: Long = 60_000L,
    private val clock: () -> Long = {
        System.nanoTime() / 1_000_000L
    }
) {
    init {
        require(maxEntries > 0)
        require(ttlMillis > 0)
    }

    private data class Key(
        val ownerUid: String,
        val code: String
    )

    private data class Entry(
        val user: User,
        val storedAt: Long,
        val order: Long
    )

    private val entries = ArrayMap<Key, Entry>()
    private var nextOrder = 0L

    @Synchronized
    fun get(ownerUid: String, code: String): User? {
        val now = clock()
        removeExpired(now)

        return entries[
            Key(ownerUid, normalize(code))
        ]?.user
    }

    @Synchronized
    fun put(
        ownerUid: String,
        code: String,
        user: User
    ) {
        val normalized = normalize(code)

        if (
            ownerUid.isBlank() ||
            normalized.isBlank() ||
            user.id.isBlank()
        ) {
            return
        }

        val now = clock()
        removeExpired(now)

        val key = Key(ownerUid, normalized)
        entries.remove(key)

        if (entries.size >= maxEntries) {
            val oldestIndex = (0 until entries.size)
                .minByOrNull { entries.valueAt(it).order }

            if (oldestIndex != null) {
                entries.removeAt(oldestIndex)
            }
        }

        entries[key] = Entry(
            user = user,
            storedAt = now,
            order = nextOrder++
        )
    }

    /** Remove every code lookup pointing to a changed user. */
    @Synchronized
    fun invalidateUser(userId: String) {
        for (index in entries.size - 1 downTo 0) {
            if (entries.valueAt(index).user.id == userId) {
                entries.removeAt(index)
            }
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun removeExpired(now: Long) {
        for (index in entries.size - 1 downTo 0) {
            val storedAt = entries.valueAt(index).storedAt
            val age = now - storedAt

            if (age < 0 || age >= ttlMillis) {
                entries.removeAt(index)
            }
        }
    }

    private fun normalize(code: String): String =
        code.trim().uppercase(Locale.ROOT)
}