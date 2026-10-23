package com.familytracker.app.circles

import android.content.Context
import com.familytracker.app.data.snapshots
import com.familytracker.app.domain.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class CircleRepository(private val db: FirebaseDatabase, private val fs: FirebaseFirestore, context: Context) {
    private val journal = context.getSharedPreferences("membership_operations", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private fun root(id: String) = db.getReference("circles/$id")
    private fun member(id: String,uid: String) = fs.document("circles/$id/members/$uid")
    fun approved(id: String, uid: String): Flow<Boolean> {
        val firestore = callbackFlow {
            val listener = member(id,uid).addSnapshotListener { value,error ->
                if(error != null) trySend(false) else trySend(value?.exists() == true)
            }; awaitClose { listener.remove() }
        }
        return combine(root(id).child("members/$uid").snapshots().map { it.exists() }.catch { emit(false) }, firestore) { a,b -> a && b }
            .distinctUntilChanged()
    }
    fun circle(id: String,uid: String): Flow<Circle?> = approved(id,uid).flatMapLatest { approved ->
        if(!approved) flowOf(null) else {
            val requests = if(uid == id) root(id).child("requests").snapshots().map { s -> s.children.map { Member(it.key!!,it.child("name").getValue(String::class.java).orEmpty()) } } else flowOf(emptyList())
            combine(root(id).child("name").snapshots(),root(id).child("members").snapshots(),requests) { name,members,pending ->
                Circle(id,name.getValue(String::class.java).orEmpty(), members.children.map { Member(it.key!!, it.child("name").getValue(String::class.java).orEmpty()) },pending)
            }.catch { emit(null) }
        }
    }
    suspend fun create(uid: String,name: String,displayName: String) {
        require(name.trim().length in 1..60)
        fs.document("circles/$uid").set(mapOf("name" to name.trim(),"ownerUid" to uid)).await()
        root(uid).child("name").setValue(name.trim()).await()
        changeMembership(uid,uid,uid,displayName,true)
    }
    suspend fun request(id: String,uid: String,name: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        root(id).child("requests/$uid").setValue(mapOf("name" to name.take(60).ifBlank { "Участник" })).await()
    }
    suspend fun reject(id: String,uid: String) { root(id).child("requests/$uid").removeValue().await() }
    suspend fun changeMembership(actor: String,id: String,uid: String,name: String,add: Boolean) = mutex.withLock {
        require(actor==id || (!add && actor==uid))
        val key="$actor:$id:$uid"
        val op=JSONObject().put("circle",id).put("uid",uid).put("name",name).put("add",add)
        check(journal.edit().putString(key,op.toString()).commit())
        apply(id,uid,name,add)
        check(journal.edit().remove(key).commit())
    }
    private suspend fun apply(id: String,uid: String,name: String,add: Boolean) {
        // Firestore first. Until RTDB succeeds the operation is visibly pending and durable.
        if(add) member(id,uid).set(mapOf("name" to name.take(60))).await() else member(id,uid).delete().await()
        val updates=mutableMapOf<String,Any?>("members/$uid" to if(add) mapOf("name" to name.take(60)) else null)
        if(add) updates["requests/$uid"]=null else updates["locations/$uid"]=null
        root(id).updateChildren(updates).await()
    }
    fun pendingCount(actor: String) = journal.all.keys.count { it.startsWith("$actor:") }
    suspend fun retry(actor: String) = mutex.withLock {
        journal.all.filterKeys { it.startsWith("$actor:") }.forEach { (key,value) ->
            val j=JSONObject(value as String)
            apply(j.getString("circle"),j.getString("uid"),j.getString("name"),j.getBoolean("add"))
            check(journal.edit().remove(key).commit())
        }
    }
}
