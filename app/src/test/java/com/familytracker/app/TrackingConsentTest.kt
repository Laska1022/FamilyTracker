package com.familytracker.app

import com.familytracker.app.domain.LocationFixGate
import com.familytracker.app.domain.RecordingConsent
import org.junit.Assert.*
import org.junit.Test

class TrackingConsentTest {
    @Test
    fun stopInvalidatesLateCallbacksEvenAfterRestartInSameCircle() {
        val consent = RecordingConsent()
        assertNull(consent.session("user", "circle"))
        assertTrue(consent.activate(consent.prepareStart(), "user", "circle"))
        val oldSession = consent.session("user", "circle")!!
        var records = 0
        assertTrue(consent.ifCurrent(oldSession) { records++ })
        consent.stop()
        assertFalse(consent.ifCurrent(oldSession) { records++ })
        assertTrue(consent.activate(consent.prepareStart(), "user", "circle"))
        assertFalse(consent.ifCurrent(oldSession) { records++ })
        assertTrue(consent.ifCurrent(consent.session("user", "circle")!!) { records++ })
        assertEquals(2, records)
    }

    @Test
    fun stopDuringNetworkAcknowledgementCannotReactivateConsent() {
        val consent = RecordingConsent()
        val pendingStart = consent.prepareStart()
        consent.stop()
        assertFalse(consent.activate(pendingStart, "user", "circle"))
        assertNull(consent.session("user", "circle"))
    }

    @Test
    fun callbacksCannotUseAnotherAccountsOrCirclesConsent() {
        val consent = RecordingConsent()
        consent.activate(consent.prepareStart(), "alice", "family")
        assertNull(consent.session("bob", "family"))
        assertNull(consent.session("alice", "other"))
    }

    @Test
    fun cachedPreConsentAndDuplicateFixesAreRejected() {
        val gate = LocationFixGate(100_000)
        assertFalse(gate.accept(99_999, 100_000))
        assertTrue(gate.accept(100_000, 100_100))
        assertFalse(gate.accept(100_000, 160_000))
        assertFalse(gate.accept(200_000, 160_000))
    }

    @Test
    fun normalIntervalJitterAndDelayedBatchesStillProduceHistory() {
        val gate = LocationFixGate(100_000)
        assertTrue(gate.accept(100_000, 100_010))
        assertTrue(gate.accept(159_999, 160_010))
        // A five-minute request may be delivered late after Android wakes the process.
        assertTrue(gate.accept(460_000, 600_000))
        assertFalse(gate.accept(159_999, 600_001))
    }
}
