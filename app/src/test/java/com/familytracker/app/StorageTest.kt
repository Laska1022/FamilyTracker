package com.familytracker.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.familytracker.app.data.LocationOutbox
import com.familytracker.app.domain.*
import com.familytracker.app.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StorageTest {
    @Test
    fun settingsPersistAndRemainAccountScoped() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = SettingsStore(context)
        assertEquals(ReportingInterval.ONE_MINUTE, first.interval("new-user").first())
        first.setInterval("a", ReportingInterval.FIVE_MINUTES)
        first.setCircle("a", "family")
        val reopened = SettingsStore(context)
        assertEquals(ReportingInterval.FIVE_MINUTES, reopened.interval("a").first())
        assertEquals(ReportingInterval.ONE_MINUTE, reopened.interval("b").first())
        assertEquals("family", reopened.circle("a").first())
        assertNull(reopened.circle("b").first())
    }

    @Test
    fun outboxDeduplicatesBoundsAndKeepsStopTombstones() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("location_outbox.db")
        val q = LocationOutbox(context)
        val p = LocationPoint("same", "a", "family", 1.0, 2.0, 3.0, null, null, 123)
        q.enqueue(p)
        q.enqueue(p)
        assertEquals(1, q.count("a"))
        assertEquals(123, q.pending("a").single().deviceTime)
        q.markStopped("a", "family")
        q.close()
        val reopened = LocationOutbox(context)
        assertEquals(listOf("family"), reopened.pendingStops("a"))
        for (i in 1..10002) reopened.enqueue(p.copy(id = "$i", deviceTime = i.toLong()))
        assertEquals(10000, reopened.count("a"))
        assertTrue(reopened.pending("b").isEmpty())
        reopened.clear("a")
        assertEquals(0, reopened.count("a"))
        reopened.close()
    }
}
