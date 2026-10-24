package com.familytracker.app.domain

/** Device-local consent boundary shared by service callbacks and stop/start operations. */
class RecordingConsent {
    class Session internal constructor(val uid: String, val circle: String)

    private var revision = 0L
    private var active: Session? = null

    @Synchronized
    fun prepareStart(): Long {
        active = null
        return ++revision
    }

    @Synchronized
    fun activate(attempt: Long, uid: String, circle: String): Boolean {
        if (attempt != revision) return false
        active = Session(uid, circle)
        return true
    }

    @Synchronized
    fun stop() {
        ++revision
        active = null
    }

    @Synchronized
    fun session(uid: String, circle: String): Session? = active?.takeIf {
        it.uid == uid && it.circle == circle
    }

    /** The action is atomic with stopping consent; stale callback sessions cannot record. */
    @Synchronized
    fun ifCurrent(session: Session, action: () -> Unit): Boolean {
        if (active !== session) return false
        action()
        return true
    }
}
