package com.familytracker.app.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.familytracker.app.domain.Profile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth) {
    val profiles = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.let { Profile(it.uid, it.displayName.orEmpty(), it.email.orEmpty()) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
    suspend fun register(email: String, password: String, name: String) {
        require(name.trim().length in 1..60)
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
    }
    suspend fun signIn(email: String, password: String) { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun reset(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }
    fun signOut() = auth.signOut()
}
