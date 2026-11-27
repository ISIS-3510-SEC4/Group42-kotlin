package com.example.espoti.data.cache

import com.example.espoti.model.domain.UserStats
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of the last "My stats" snapshot per user, with a time to
 * live. "My stats" gets a new ViewModel every time it is opened, so without
 * this, going Profile -> My stats -> back -> My stats would read every
 * meeting from Firestore and aggregate them again. Within [ttlMillis] the
 * screen is answered from memory (cache falling back to network).
 *
 * Normally holds one entry (the signed-in user), so a plain thread-safe map
 * is enough. No LRU needed.
 */
class StatsCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val entries = ConcurrentHashMap<String, UserStats>()

    /** The snapshot for [uid] while it is younger than the TTL, else null. */
    fun get(uid: String): UserStats? {
        val stats = entries[uid] ?: return null
        if (clock() - stats.computedAt >= ttlMillis) {
            entries.remove(uid, stats)
            return null
        }
        return stats
    }

    fun put(uid: String, stats: UserStats) {
        entries[uid] = stats
    }

    fun clear() = entries.clear()

    companion object {
        const val DEFAULT_TTL_MILLIS = 5 * 60 * 1000L
    }
}
