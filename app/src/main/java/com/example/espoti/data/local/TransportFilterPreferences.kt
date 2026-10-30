package com.example.espoti.data.local

import android.content.Context
import com.example.espoti.model.TransportFilters
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Small user settings, independent from meetings and analytics events. */
interface TransportFilterPreferences {
    suspend fun read(ownerUid: String): TransportFilters
    suspend fun save(ownerUid: String, filters: TransportFilters)
}

/**
 * One private SharedPreferences file per account.
 * Both limits are saved in the same editor operation.
 * Disk access runs on Dispatchers.IO.
 */
class SharedPreferencesTransportFilterPreferences(
    context: Context
) : TransportFilterPreferences {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    override suspend fun read(
        ownerUid: String
    ): TransportFilters = withContext(Dispatchers.IO) {
        mutex.withLock {
            val values = preferences(ownerUid).all

            val distance = (values["max_distance_km"] as? String)
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }

            val minutes = (values["max_walking_minutes"] as? Int)
                ?.takeIf { it > 0 }

            TransportFilters(
                maxDistanceKm = distance,
                maxWalkingMinutes = minutes
            )
        }
    }

    override suspend fun save(
        ownerUid: String,
        filters: TransportFilters
    ) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val editor = preferences(ownerUid).edit()

                val distance = filters.maxDistanceKm
                if (distance == null) {
                    editor.remove("max_distance_km")
                } else {
                    editor.putString(
                        "max_distance_km",
                        distance.toString()
                    )
                }

                val minutes = filters.maxWalkingMinutes
                if (minutes == null) {
                    editor.remove("max_walking_minutes")
                } else {
                    editor.putInt("max_walking_minutes", minutes)
                }

                // commit() confirms disk persistence; it runs off the main thread.
                check(editor.commit()) {
                    "Couldn't save transport filter preferences."
                }
            }
        }
    }

    private fun preferences(ownerUid: String) =
        appContext.getSharedPreferences(
            fileName(ownerUid),
            Context.MODE_PRIVATE
        )

    private fun fileName(ownerUid: String): String {
        require(ownerUid.isNotBlank())

        val key = MessageDigest.getInstance("SHA-256")
            .digest(ownerUid.toByteArray(Charsets.UTF_8))
            .joinToString("") {
                (it.toInt() and 0xff)
                    .toString(16)
                    .padStart(2, '0')
            }

        return "transport_filters_$key"
    }
}

/** Stand-in for unit tests without an Android Context. */
class InMemoryTransportFilterPreferences :
    TransportFilterPreferences {

    private val values = mutableMapOf<String, TransportFilters>()

    override suspend fun read(ownerUid: String): TransportFilters =
        values[ownerUid] ?: TransportFilters()

    override suspend fun save(
        ownerUid: String,
        filters: TransportFilters
    ) {
        values[ownerUid] = filters
    }
}