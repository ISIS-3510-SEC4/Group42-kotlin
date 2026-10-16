package com.example.espoti.data.repository

import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.PlaceCategory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Collection places/{id}.
 * `open` + lazy Firestore so ViewModel tests can subclass it without Firebase.
 */
open class PlaceRepository(
    dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private val places by lazy { dbProvider().collection("places") }

    open suspend fun getPlace(id: String): Result<Place?> = runCatching {
        places.document(id).get().await().toObject(Place::class.java)?.copy(id = id)
    }

    suspend fun getPlaces(category: PlaceCategory? = null): Result<List<Place>> = runCatching {
        val query = if (category == null) places else places.whereEqualTo("category", category.name)
        query.get().await().documents.mapNotNull { d -> d.toObject(Place::class.java)?.copy(id = d.id) }
    }

    suspend fun savePlace(place: Place): Result<String> = runCatching {
        val ref = if (place.id.isBlank()) places.document() else places.document(place.id)
        ref.set(place.copy(id = ref.id)).await()
        ref.id
    }
}
