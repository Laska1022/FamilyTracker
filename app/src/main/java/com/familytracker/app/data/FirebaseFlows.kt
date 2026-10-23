package com.familytracker.app.data

import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

fun DatabaseReference.snapshots() = callbackFlow {
    val listener =
        object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}
