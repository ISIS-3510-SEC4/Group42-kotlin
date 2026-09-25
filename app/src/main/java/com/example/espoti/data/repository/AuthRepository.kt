package com.example.espoti.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Wraps Firebase Authentication (email + password). Returns Result so callers never see SDK exceptions.
 * `open` + lazy [FirebaseAuth] so ViewModel unit tests can subclass it without initializing Firebase.
 */
open class AuthRepository(
    authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() }
) {
    private val auth by lazy(authProvider)

    open val currentUserId: String? get() = auth.currentUser?.uid

    open suspend fun register(email: String, password: String): Result<String> = runCatching {
        auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
    }

    open suspend fun login(email: String, password: String): Result<String> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user!!.uid
    }

    /** Rolls back a just-created account (used when saving its profile document fails). */
    open suspend fun deleteCurrentUser(): Result<Unit> = runCatching {
        auth.currentUser?.delete()?.await()
        Unit
    }

    open fun logout() = auth.signOut()
}
