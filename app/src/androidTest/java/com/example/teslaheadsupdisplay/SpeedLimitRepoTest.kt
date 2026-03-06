package com.chaitalkstech.teslaheadsupdisplay

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeedLimitRepoTest {

    @Test
    fun testGetRoadData_RealApiCall() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Coordinates for Cameron Rd in Austin (Highly likely to have speed limit data)
        val lat = 30.316214
        val lon = -97.700922
        val speed = 65

        // Force refresh to trigger real API calls
        val result = SpeedLimitRepo.getRoadData(appContext, lat, lon, speed, forceRefresh = true)

        assertNotNull("Result should not be null", result)
        
        println("DEBUG: API Key used: ${BuildConfig.MAPS_API_KEY.take(5)}...")
        println("DEBUG: Speed Limit: ${result.speedLimit}, Road Name: ${result.roadName}")
        
        // Check if either is populated. If both are null, the API call likely failed or returned empty.
        assertTrue(
            "Expected speed limit or road name. Check Logcat for 'RoadsAPI' or 'GeocodingAPI' entries to see the raw response.",
            result.speedLimit != null || result.roadName != null
        )
    }
}
