package com.familytracker.app
import com.familytracker.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DomainTest {
    private fun point(lat: Double=0.0,lon: Double=0.0,time: Long=1_000) = LocationPoint("id","uid","circle",lat,lon,10.0,null,80,time)
    @Test fun defaultsAndSupportedIntervals() { assertEquals(60_000,ReportingInterval.fromMillis(1).millis);assertEquals(300_000,ReportingInterval.FIVE_MINUTES.millis) }
    @Test fun validatesPhysicalAndTemporalBounds() {
        assertTrue(point().isValid(2000));assertFalse(point(91.0).isValid());assertFalse(point(lon=Double.NaN).isValid())
        assertFalse(point().copy(accuracy=-1.0).isValid());assertFalse(point().copy(speed=Double.POSITIVE_INFINITY).isValid())
        assertFalse(point().copy(battery=101).isValid());assertFalse(point(time=100_000).isValid(1_000))
    }
    @Test fun freshnessUsesAcquisitionTimeAndInterval() {
        assertTrue(point(time=1_000).isFresh(120_000));assertFalse(point(time=1_000).isFresh(200_000))
        assertTrue(point(time=1_000).copy(intervalMs=300_000).isFresh(500_000))
        assertFalse(point(time=1_000).copy(uploadedAt=1_000_000).isFresh(1_000_000))
    }
    @Test fun distanceAndDateLineAreStable() {
        assertEquals(111_195.0,routeDistanceMeters(listOf(point(),point(lon=1.0,time=2_000))),5.0)
        assertEquals(222_390.0,routeDistanceMeters(listOf(point(lon=179.0),point(lon=-179.0,time=2_000))),10.0)
        assertEquals(0.0,routeDistanceMeters(emptyList()),0.0)
    }
    @Test fun historyDayHonorsDstAndExclusiveEnd() {
        val spring=dayRange(LocalDate.of(2025,3,30),ZoneId.of("Europe/Berlin"))
        val autumn=dayRange(LocalDate.of(2025,10,26),ZoneId.of("Europe/Berlin"))
        assertEquals(23*3600_000L,spring.endExclusive-spring.start);assertEquals(25*3600_000L,autumn.endExclusive-autumn.start)
        assertEquals(spring.endExclusive,dayRange(LocalDate.of(2025,3,31),ZoneId.of("Europe/Berlin")).start)
    }
    @Test fun noTrackingWithoutEveryPrerequisite() {
        assertTrue(mayStartTracking(true,true,true,true))
        for(i in 0..3) { val flags=MutableList(4){true};flags[i]=false;assertFalse(mayStartTracking(flags[0],flags[1],flags[2],flags[3])) }
    }
}
