package com.example.espoti.data.local

import android.database.sqlite.SQLiteDatabase
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.MonthCount
import com.example.espoti.model.domain.RankedItem
import com.example.espoti.model.domain.TrendingPlace
import com.example.espoti.model.domain.UserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Last stats computed for each user, saved on the device so "My stats" opens
 * offline (cache then network) and survives app restarts.
 */
interface StatsLocalStore {
    suspend fun get(uid: String): UserStats?
    suspend fun save(uid: String, stats: UserStats)
    suspend fun clear(uid: String)
}

/**
 * Table `user_stats` of [EspotiDatabase]: one row per user (owner_uid PK) with
 * the finished snapshot as JSON. A snapshot is always read and written whole,
 * so one TEXT column is simpler than a table per list (friends, places...).
 */
class SqliteStatsLocalStore(private val database: EspotiDatabase) : StatsLocalStore {

    override suspend fun get(uid: String): UserStats? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            EspotiDatabase.USER_STATS, arrayOf("payload"), "owner_uid = ?", arrayOf(uid),
            null, null, null
        ).readAll { string("payload") }
            .firstOrNull()
            ?.let { runCatching { decodeStats(JSONObject(it)) }.getOrNull() }
    }

    override suspend fun save(uid: String, stats: UserStats) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.insertWithOnConflict(
                EspotiDatabase.USER_STATS,
                null,
                contentValuesOf(
                    "owner_uid" to uid,
                    "payload" to encodeStats(stats).toString(),
                    "computed_at" to stats.computedAt
                ),
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    override suspend fun clear(uid: String) {
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(EspotiDatabase.USER_STATS, "owner_uid = ?", arrayOf(uid))
        }
    }
}

/** In-memory version: unit tests, and the fallback when the app context isn't available. */
class InMemoryStatsLocalStore : StatsLocalStore {
    val saved = mutableMapOf<String, UserStats>()
    override suspend fun get(uid: String): UserStats? = saved[uid]
    override suspend fun save(uid: String, stats: UserStats) { saved[uid] = stats }
    override suspend fun clear(uid: String) { saved.remove(uid) }
}

// ---- JSON encoding (only used by the SQLite store) --------------------------

private fun encodeStats(stats: UserStats): JSONObject = JSONObject()
    .put("attended", stats.attended)
    .put("upcoming", stats.upcoming)
    .put("canceled", stats.canceled)
    .put("votesCast", stats.votesCast)
    .put("decidedWithVote", stats.decidedWithVote)
    .put("wonVotes", stats.wonVotes)
    .put("topFriends", encodeRanked(stats.topFriends))
    .put("topPlaces", encodeRanked(stats.topPlaces))
    .put("favoriteActivity", stats.favoriteActivity?.name ?: JSONObject.NULL)
    .put("perMonth", JSONArray().apply {
        stats.perMonth.forEach { put(JSONObject().put("key", it.key).put("label", it.label).put("count", it.count)) }
    })
    .put("trendingPlaces", JSONArray().apply {
        stats.trendingPlaces.forEach {
            put(JSONObject().put("name", it.name).put("cityName", it.cityName).put("selections", it.selections))
        }
    })
    .put("computedAt", stats.computedAt)

private fun encodeRanked(items: List<RankedItem>) = JSONArray().apply {
    items.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("count", it.count)) }
}

private fun decodeStats(json: JSONObject) = UserStats(
    attended = json.getInt("attended"),
    upcoming = json.getInt("upcoming"),
    canceled = json.getInt("canceled"),
    votesCast = json.getInt("votesCast"),
    decidedWithVote = json.getInt("decidedWithVote"),
    wonVotes = json.getInt("wonVotes"),
    topFriends = decodeRanked(json.getJSONArray("topFriends")),
    topPlaces = decodeRanked(json.getJSONArray("topPlaces")),
    favoriteActivity = json.optString("favoriteActivity")
        .let { name -> ActivityType.entries.firstOrNull { it.name == name } },
    perMonth = json.getJSONArray("perMonth").objects().map {
        MonthCount(it.getString("key"), it.getString("label"), it.getInt("count"))
    },
    trendingPlaces = json.getJSONArray("trendingPlaces").objects().map {
        TrendingPlace(it.getString("name"), it.getString("cityName"), it.getInt("selections"))
    },
    computedAt = json.getLong("computedAt")
)

private fun decodeRanked(array: JSONArray) = array.objects().map {
    RankedItem(it.getString("id"), it.getString("label"), it.getInt("count"))
}

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
