package com.example.espoti.data.repository

import com.example.espoti.model.domain.Recommendation
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where the NestJS backend lives. 10.0.2.2 is the host machine as seen from the Android emulator. */
object ApiConfig {
    // Debug-only default (plain HTTP). Change to the deployed HTTPS URL for release.
    const val BASE_URL = "http://10.0.2.2:3000"
}

/**
 * Asks the backend for place recommendations. The backend talks to Pinecone
 * (the API key never ships in the app); we only send the user's Firebase ID
 * token, which the backend verifies.
 */
class RecommendationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val baseUrl: String = ApiConfig.BASE_URL
) {

    /** Recommendations for the signed-in user. */
    suspend fun forCurrentUser(
        lat: Double? = null,
        lng: Double? = null,
        topK: Int = 10,
        category: String? = null
    ): Result<List<Recommendation>> =
        fetch("users/me", lat, lng, topK, category)

    /** Recommendations for a whole meeting (average of every participant's taste). */
    suspend fun forMeeting(
        meetingId: String,
        lat: Double? = null,
        lng: Double? = null,
        topK: Int = 10,
        category: String? = null
    ): Result<List<Recommendation>> =
        fetch("meetings/$meetingId", lat, lng, topK, category)

    private suspend fun fetch(
        path: String,
        lat: Double?,
        lng: Double?,
        topK: Int,
        category: String?
    ): Result<List<Recommendation>> = runCatching {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: error("Not signed in")

        val query = buildList {
            add("topK=$topK")
            if (lat != null && lng != null) {
                add("lat=$lat")
                add("lng=$lng")
            }
            if (category != null) add("category=${URLEncoder.encode(category, "UTF-8")}")
        }.joinToString("&")

        withContext(Dispatchers.IO) {
            val conn = URL("$baseUrl/recommendations/$path?$query").openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                if (conn.responseCode !in 200..299) error("Server error ${conn.responseCode}")
                parse(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parse(body: String): List<Recommendation> {
        val array = JSONArray(body)
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Recommendation(
                placeId = o.getString("placeId"),
                name = o.getString("name"),
                category = o.getString("category"),
                rating = o.getInt("rating"),
                score = o.getDouble("score"),
                distanceKm = if (o.isNull("distanceKm")) null else o.getDouble("distanceKm")
            )
        }
    }
}
