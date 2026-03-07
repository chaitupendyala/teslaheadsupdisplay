package com.chaitalkstech.teslaheadsupdisplay

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeedLimitRepoTest {

    // Hillcrest Drive, Austin TX — confirmed to return a road name via Overpass/Geocoding
    private val lat = 30.3182029
    private val lon = -97.6970964

    /**
     * Verifies the full API pipeline (Overpass + Geocoding fallback) returns a road name.
     * Speed limit parsing logic is covered separately in ParseMaxspeedTest (JVM unit tests).
     */
    @Test
    fun testOverpassOrGeocodingReturnsRoadName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val result = runBlocking {
            SpeedLimitRepo.getRoadData(appContext, lat, lon, 30, forceRefresh = true)
        }
        println("DEBUG: speedLimit=${result.speedLimit}, roadName=${result.roadName}")
        assertNotNull("Should return a road name (via Overpass or Geocoding fallback)", result.roadName)
        assertTrue("Road name should not be blank", result.roadName!!.isNotBlank())
    }

    /**
     * If Overpass returns a speed limit, it must be a valid parseable integer string.
     * This test is a no-op when Overpass has no maxspeed tag for this location (speedLimit stays null).
     */
    @Test
    fun testSpeedLimitIfPresentIsValidInteger() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val result = runBlocking {
            SpeedLimitRepo.getRoadData(appContext, lat, lon, 30, forceRefresh = true)
        }
        println("DEBUG: speedLimit=${result.speedLimit}, roadName=${result.roadName}")
        result.speedLimit?.let { limit ->
            assertNotNull("speedLimit '$limit' must be a parseable integer", limit.toIntOrNull())
        }
    }
}
