@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.familytracker.app

import com.familytracker.app.domain.LocationPoint
import com.familytracker.app.domain.Profile
import com.familytracker.app.map.selectedPoint
import com.familytracker.app.ui.accountCircleSelections
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.geojson.Feature

class UiFlowTest {
    @Test
    fun changingAccountClearsCircleBeforeNewPreferencesArrive() = runTest {
        val first = Profile("first", "First", "first@example.test")
        val second = Profile("second", "Second", "second@example.test")
        val profiles = MutableStateFlow<Profile?>(first)
        val firstPreferences = MutableSharedFlow<String?>()
        val secondPreferences = MutableSharedFlow<String?>()
        val observed = mutableListOf<Pair<String?, String?>>()
        val job =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                accountCircleSelections(profiles) {
                        if (it == first.uid) firstPreferences else secondPreferences
                    }
                    .collect { observed += it.profile?.uid to it.circleId }
            }
        firstPreferences.emit("family-one")
        profiles.value = second
        assertEquals("second" to null, observed.last())
        firstPreferences.emit("old-circle-after-switch")
        assertEquals("second" to null, observed.last())
        secondPreferences.emit("family-two")
        assertEquals("second" to "family-two", observed.last())
        assertFalse(observed.contains("second" to "family-one"))
        profiles.value = null
        assertEquals(null to null, observed.last())
        job.cancel()
    }

    @Test
    fun mapSelectionHandlesDifferentLayerSchemasAndMissingProperties() {
        val p = LocationPoint("point-id", "member-id", "circle", 1.0, 2.0, 3.0, null, null, 1000)
        val member =
            Feature.fromJson(
                """{"type":"Feature","geometry":null,"properties":{"uid":"member-id"}}"""
            )
        val route =
            Feature.fromJson(
                """{"type":"Feature","geometry":null,"properties":{"id":"point-id"}}"""
            )
        val empty = Feature.fromJson("""{"type":"Feature","geometry":null,"properties":{}}""")
        val invalid =
            Feature.fromJson(
                """{"type":"Feature","geometry":null,"properties":{"uid":null,"id":42}}"""
            )
        assertEquals(p, selectedPoint(member, listOf(p)))
        assertEquals(p, selectedPoint(route, listOf(p)))
        assertNull(selectedPoint(empty, listOf(p)))
        assertNull(selectedPoint(invalid, listOf(p)))
        assertNull(selectedPoint(null, listOf(p)))
        assertNull(selectedPoint(member, emptyList()))
    }
}
