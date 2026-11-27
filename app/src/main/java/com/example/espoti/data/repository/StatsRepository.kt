package com.example.espoti.data.repository

import com.example.espoti.model.domain.TrendingPlace
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Reads analytics_results/popular_meeting_areas, written by the
 * popular_meeting_areas.js script (BQ type 4 of Sprint 2), so "My stats" can
 * show the places people meet at the most across Espoti.
 * `open` + lazy [FirebaseFirestore] so ViewModel tests can subclass it.
 */
open class StatsRepository(
    dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private val results by lazy { dbProvider().collection("analytics_results") }

    /** Top [limit] places; empty when the script has not run yet. */
    open suspend fun getTrendingPlaces(limit: Int = 3): Result<List<TrendingPlace>> = runCatching {
        val document = results.document(POPULAR_AREAS_DOC).get().await()
        val rows = document.get("topPlaces") as? List<*> ?: return@runCatching emptyList()
        rows.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull { row ->
                val name = row["placeName"] as? String ?: return@mapNotNull null
                TrendingPlace(
                    name = name,
                    cityName = row["cityName"] as? String ?: "",
                    selections = (row["selections"] as? Number)?.toInt() ?: 0
                )
            }
            .take(limit)
            .toList()
    }

    private companion object {
        const val POPULAR_AREAS_DOC = "popular_meeting_areas"
    }
}
