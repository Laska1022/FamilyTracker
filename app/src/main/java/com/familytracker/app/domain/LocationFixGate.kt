package com.familytracker.app.domain

/** Reject cached pre-consent fixes and duplicate callbacks, including after interval changes. */
class LocationFixGate(private val sharingStartedElapsed: Long) {
    private var lastAcceptedElapsed = Long.MIN_VALUE

    fun accept(fixElapsed: Long, nowElapsed: Long): Boolean {
        if (fixElapsed < sharingStartedElapsed || fixElapsed > nowElapsed) return false
        if (fixElapsed <= lastAcceptedElapsed) return false
        lastAcceptedElapsed = fixElapsed
        return true
    }
}
