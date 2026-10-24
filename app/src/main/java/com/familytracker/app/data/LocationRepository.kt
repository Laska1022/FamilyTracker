package com.familytracker.app.data

import com.familytracker.app.domain.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
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
    private val syncRequested = AtomicBoolean(false)
    private val consentMutex = Mutex()
    private val consent = RecordingConsent()
    private var authUid = auth.currentUser?.uid

    init {
        scope.launch { connected.collect { if (it) sync() } }
        auth.addAuthStateListener {
            val nextUid = it.currentUser?.uid
            if (authUid != nextUid) consent.stop()
            authUid = nextUid
            if (it.currentUser != null) scope.launch { sync() }
            else {
                pending.value = 0
                revocationPending.value = false
            }
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

    suspend fun begin(uid: String, circle: String) = consentMutex.withLock {
        check(auth.currentUser?.uid == uid)
        val attempt = consent.prepareStart()
        // Arm cleanup before advertising sharing. Stop invalidates this attempt immediately,
        // even when acknowledgement is still in flight.
        db.getReference("circles/$circle/locations/$uid").onDisconnect().removeValue().await()
        fs.document("circles/$circle/sharing/$uid").set(mapOf("enabled" to true)).await()
        check(auth.currentUser?.uid == uid && consent.activate(attempt, uid, circle))
        withContext(Dispatchers.IO) { outbox.clearStop(uid, circle) }
        revocationPending.value =
            withContext(Dispatchers.IO) { outbox.pendingStops(uid).isNotEmpty() }
    }

    suspend fun record(p: LocationPoint) {
        if (!p.isValid() || auth.currentUser?.uid != p.uid) return
        val session = consent.session(p.uid, p.circleId) ?: return
        val accepted =
            withContext(Dispatchers.IO) {
                val recorded = consent.ifCurrent(session) { outbox.enqueue(p) }
                pending.value = outbox.count(p.uid)
                recorded
            }
        if (!accepted) return
        // Realtime keeps only the latest point; history is uploaded independently from its bounded
        // outbox.
        if (connected.value && auth.currentUser?.uid == p.uid)
            consent.ifCurrent(session) {
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

    suspend fun confirmStopped(uid: String, circle: String) = consentMutex.withLock {
        check(auth.currentUser?.uid == uid && connected.value) {
            "Connection required to confirm revocation"
        }
        withTimeout(15_000) {
            fs.document("circles/$circle/sharing/$uid").set(mapOf("enabled" to false)).await()
            db.getReference("circles/$circle/locations/$uid").removeValue().await()
        }
        withContext(Dispatchers.IO) { outbox.clearStop(uid, circle) }
        revocationPending.value =
            withContext(Dispatchers.IO) { outbox.pendingStops(uid).isNotEmpty() }
    }

    suspend fun stop(uid: String, circle: String) {
        // Invalidate callbacks synchronously, before any network/SQLite suspension.
        consent.stop()
        revocationPending.value = true
        consentMutex.withLock {
            withContext(Dispatchers.IO) { outbox.markStopped(uid, circle) }
            if (auth.currentUser?.uid == uid) {
                fs.document("circles/$circle/sharing/$uid").set(mapOf("enabled" to false))
                db.getReference("circles/$circle/locations/$uid").removeValue()
            }
        }
        scope.launch { sync() }
    }

    suspend fun sync() {
        syncRequested.set(true)
        drain()
    }

    private suspend fun drain() {
        if (!syncMutex.tryLock()) return
        syncRequested.set(false)
        val uid = auth.currentUser?.uid
        var before = 0
        try {
            before = if (uid == null) 0 else withContext(Dispatchers.IO) { outbox.count(uid) }
            syncLocked()
        } finally {
            try {
                if (uid != null && auth.currentUser?.uid == uid)
                    pending.value =
                        withContext(NonCancellable + Dispatchers.IO) { outbox.count(uid) }
            } finally {
                syncMutex.unlock()
            }
        }
        // Continue a productive time slice until the backlog is empty. Do not spin on failures.
        if (syncRequested.get() || (connected.value && pending.value > 0 && pending.value < before))
            scope.launch { drain() }
    }

    private suspend fun syncLocked() {
        val uid = auth.currentUser?.uid ?: return
        pending.value = withContext(Dispatchers.IO) { outbox.count(uid) }
        if (!connected.value) return
        try {
            withTimeout(30_000) {
                // Serialize tombstone replay with explicit starts. Otherwise an old stop
                // finishing after a new Start could silently re-hide the active session.
                consentMutex.withLock {
                    val stops = withContext(Dispatchers.IO) { outbox.pendingStops(uid) }
                    for (circle in stops) {
                        if (auth.currentUser?.uid != uid) return@withTimeout
                        fs.document("circles/$circle/sharing/$uid")
                            .set(mapOf("enabled" to false))
                            .await()
                        db.getReference("circles/$circle/locations/$uid").removeValue().await()
                        withContext(Dispatchers.IO) { outbox.clearStop(uid, circle) }
                    }
                    revocationPending.value =
                        withContext(Dispatchers.IO) { outbox.pendingStops(uid).isNotEmpty() }
                }
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

    suspend fun clearLocal(uid: String) = syncMutex.withLock {
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
