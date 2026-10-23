package com.familytracker.app.data

import com.familytracker.app.domain.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class LocationRepository(
    private val db: FirebaseDatabase,
    private val fs: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val outbox: LocationOutbox,
    private val scope: CoroutineScope,
) {
    val connected =
        db.getReference(".info/connected")
            .snapshots()
            .map { it.getValue(Boolean::class.java) == true }
            .stateIn(scope, SharingStarted.Eagerly, false)
    val pending = MutableStateFlow(0)
    val syncError = MutableStateFlow(false)
    val revocationPending = MutableStateFlow(false)
    private val syncMutex = Mutex()
    private var liveSession: Pair<String, String>? = null

    init {
        scope.launch { connected.collect { if (it) sync() } }
        auth.addAuthStateListener {
            if (it.currentUser != null) scope.launch { sync() } else pending.value = 0
        }
    }

    fun live(circle: Circle): Flow<List<LocationPoint>> {
        if (circle.members.isEmpty()) return flowOf(emptyList())
        return combine(
            circle.members.map { member ->
                db.getReference("circles/${circle.id}/locations/${member.uid}")
                    .snapshots()
                    .map { s ->
                        val v = s.value as? Map<*, *> ?: return@map null
                        point(v, member.uid, circle.id, "live")
                    }
                    .catch { emit(null) }
            }
        ) {
            it.filterNotNull()
        }
    }

    fun historyVisible(circle: String, target: String): Flow<Boolean> = callbackFlow {
        val listener =
            fs.document("circles/$circle/sharing/$target").addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->
                trySend(
                    error == null &&
                        snapshot?.getBoolean("enabled") == true &&
                        !snapshot.metadata.isFromCache
                )
            }
        awaitClose { listener.remove() }
    }

    suspend fun begin(uid: String, circle: String) {
        syncMutex.withLock {
            fs.document("circles/$circle/sharing/$uid").set(mapOf("enabled" to true)).await()
            outbox.clearStop(uid, circle)
            revocationPending.value = outbox.pendingStops(uid).isNotEmpty()
            liveSession = uid to circle
            db.getReference("circles/$circle/locations/$uid").onDisconnect().removeValue().await()
        }
    }

    suspend fun record(p: LocationPoint) {
        if (!p.isValid()) return
        withContext(Dispatchers.IO) {
            outbox.enqueue(p)
            pending.value = outbox.count(p.uid)
        }
        // Realtime keeps only the latest point; history is uploaded independently from its bounded
        // outbox.
        if (liveSession == (p.uid to p.circleId) && connected.value) {
            val ref = db.getReference("circles/${p.circleId}/locations/${p.uid}")
            ref.onDisconnect().removeValue()
            ref.setValue(
                    mapOf(
                        "uid" to p.uid,
                        "circleId" to p.circleId,
                        "latitude" to p.latitude,
                        "longitude" to p.longitude,
                        "accuracy" to p.accuracy,
                        "battery" to p.battery,
                        "deviceTime" to p.deviceTime,
                        "uploadedAt" to ServerValue.TIMESTAMP,
                        "intervalMs" to p.intervalMs,
                    )
                )
                .addOnFailureListener { syncError.value = true }
        }
        scope.launch { sync() }
    }

    suspend fun confirmStopped(uid: String, circle: String) {
        check(connected.value) { "Connection required to confirm revocation" }
        withTimeout(15_000) {
            fs.document("circles/$circle/sharing/$uid").set(mapOf("enabled" to false)).await()
            db.getReference("circles/$circle/locations/$uid").removeValue().await()
        }
        outbox.clearStop(uid, circle)
        revocationPending.value = outbox.pendingStops(uid).isNotEmpty()
    }

    suspend fun stop(uid: String, circle: String) {
        liveSession = null
        revocationPending.value = true
        withContext(Dispatchers.IO) { outbox.markStopped(uid, circle) }
        fs.document("circles/$circle/sharing/$uid").set(mapOf("enabled" to false))
        // Queued delete persists as a tombstone across process death, and onDisconnect is also
        // armed.
        db.getReference("circles/$circle/locations/$uid").removeValue()
        scope.launch { sync() }
    }

    suspend fun sync() {
        if (!syncMutex.tryLock()) return
        val uid = auth.currentUser?.uid
        var before = 0
        try {
            before = if (uid == null) 0 else withContext(Dispatchers.IO) { outbox.count(uid) }
            syncLocked()
        } finally {
            if (uid != null)
                pending.value = withContext(NonCancellable + Dispatchers.IO) { outbox.count(uid) }
            syncMutex.unlock()
        }
        // Continue a productive time slice until the backlog is empty. Do not spin on failures.
        if (connected.value && pending.value > 0 && pending.value < before) scope.launch { sync() }
    }

    private suspend fun syncLocked() {
        val uid = auth.currentUser?.uid ?: return
        pending.value = withContext(Dispatchers.IO) { outbox.count(uid) }
        if (!connected.value) return
        try {
            withTimeout(30_000) {
                for (circle in outbox.pendingStops(uid)) {
                    fs.document("circles/$circle/sharing/$uid")
                        .set(mapOf("enabled" to false))
                        .await()
                    db.getReference("circles/$circle/locations/$uid").removeValue().await()
                    outbox.clearStop(uid, circle)
                }
                revocationPending.value = outbox.pendingStops(uid).isNotEmpty()
                while (connected.value && auth.currentUser?.uid == uid) {
                    val batch = withContext(Dispatchers.IO) { outbox.pending(uid) }
                    if (batch.isEmpty()) break
                    for (p in batch) {
                        if (auth.currentUser?.uid != uid) return@withTimeout
                        val ref = fs.document("circles/${p.circleId}/history/$uid/points/${p.id}")
                        try {
                            ref.set(
                                    mapOf(
                                        "uid" to uid,
                                        "circleId" to p.circleId,
                                        "latitude" to p.latitude,
                                        "longitude" to p.longitude,
                                        "accuracy" to p.accuracy,
                                        "speed" to p.speed,
                                        "battery" to p.battery,
                                        "deviceTime" to p.deviceTime,
                                        "uploadedAt" to FieldValue.serverTimestamp(),
                                    )
                                )
                                .await()
                        } catch (e: FirebaseFirestoreException) {
                            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                            // Membership was revoked: never keep retrying private points for an
                            // unauthorized Circle.
                        }
                        withContext(Dispatchers.IO) { outbox.remove(p.id) }
                    }
                    pending.value = withContext(Dispatchers.IO) { outbox.count(uid) }
                }
                syncError.value = false
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) syncError.value = true else throw e
        } catch (_: Exception) {
            syncError.value = true
        }
    }

    suspend fun history(
        circle: String,
        uid: String,
        date: LocalDate,
        zone: ZoneId,
    ): List<LocationPoint> {
        val range = dayRange(date, zone)
        val points = fs.collection("circles/$circle/history/$uid/points")
        // Paginate within the day; no whole-history listener or query.
        val result = mutableListOf<LocationPoint>()
        var cursor: DocumentSnapshot? = null
        do {
            var query =
                points
                    .whereGreaterThanOrEqualTo("deviceTime", range.start)
                    .whereLessThan("deviceTime", range.endExclusive)
                    .orderBy("deviceTime")
                    .limit(500)
            if (cursor != null) query = query.startAfter(cursor)
            val page = query.get(Source.SERVER).await()
            result += page.documents.mapNotNull { point(it.data.orEmpty(), uid, circle, it.id) }
            cursor = page.documents.lastOrNull()
        } while (page.size() == 500)
        return result
    }

    suspend fun deleteHistory(uid: String, circle: String) = syncMutex.withLock {
        withContext(Dispatchers.IO) { outbox.clear(uid, circle) }
        val collection = fs.collection("circles/$circle/history/$uid/points")
        while (true) {
            val page = collection.limit(400).get(Source.SERVER).await()
            if (page.isEmpty) break
            fs.runBatch { batch -> page.forEach { batch.delete(it.reference) } }.await()
        }
        pending.value = outbox.count(uid)
    }

    suspend fun clearLocal(uid: String) {
        withContext(Dispatchers.IO) { outbox.clear(uid) }
        pending.value = 0
    }

    private fun point(v: Map<*, *>, uid: String, circle: String, id: String): LocationPoint? =
        runCatching {
            LocationPoint(
                id,
                uid,
                circle,
                (v["latitude"] as Number).toDouble(),
                (v["longitude"] as Number).toDouble(),
                (v["accuracy"] as Number).toDouble(),
                (v["speed"] as? Number)?.toDouble(),
                (v["battery"] as? Number)?.toInt(),
                (v["deviceTime"] as Number).toLong(),
                when (val time = v["uploadedAt"]) {
                    is Timestamp -> time.toDate().time
                    is Number -> time.toLong()
                    else -> 0
                },
                (v["intervalMs"] as? Number)?.toLong() ?: 60_000,
            )
        }
        .getOrNull()
        ?.takeIf { it.isValid() }
}
