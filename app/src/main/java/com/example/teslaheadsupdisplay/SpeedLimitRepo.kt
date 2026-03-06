package com.chaitalkstech.teslaheadsupdisplay

import android.content.Context
import android.location.Location
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RoadInfo(
    val speedLimit: String?,
    val roadName: String?,
    val isFromCache: Boolean = false
)

object SpeedLimitRepo {
    private val client = OkHttpClient()
    private var lastRequestTime = 0L
    private var lastRequestLat = 0.0
    private var lastRequestLon = 0.0
    private var lastRoadInfo: RoadInfo = RoadInfo(null, null)
    private var lastPlaceId: String? = null

    private val API_KEY = BuildConfig.MAPS_API_KEY
    private const val PACKAGE_NAME = "com.chaitalkstech.teslaheadsupdisplay"
    
    // TODO: Replace this with your actual SHA-1 fingerprint from ./gradlew signingReport
    private const val SHA1_FINGERPRINT = "YOUR_SHA1_FINGERPRINT_HERE"

    private suspend fun logToDb(context: Context, tag: String, message: String, responseCode: Int? = null, error: String? = null) {
        val logMsg = "[$tag] Code: $responseCode | Msg: $message" + (if (error != null) " | Err: $error" else "")
        Log.d("SpeedLimitRepo", logMsg)
        try {
            val db = AppDatabase.getDatabase(context)
            db.apiLogDao().insertLog(ApiLog(tag = tag, message = message, responseCode = responseCode, error = error))
        } catch (e: Exception) {
            Log.e("SpeedLimitRepo", "Failed to save log to DB", e)
        }
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun getRoadData(context: Context, lat: Double, lon: Double, currentSpeedMph: Int, forceRefresh: Boolean = false): RoadInfo {
        val currentTime = System.currentTimeMillis()

        val (timeThrottle, distanceThreshold) = when {
            currentSpeedMph < 20 -> 30000L to 500
            currentSpeedMph < 50 -> 15000L to 200
            else -> 10000L to 100
        }

        val timeElapsed = currentTime - lastRequestTime
        val results = FloatArray(1)
        Location.distanceBetween(lastRequestLat, lastRequestLon, lat, lon, results)
        val distanceMoved = results[0]

        if (!forceRefresh && timeElapsed < timeThrottle && distanceMoved < distanceThreshold && lastRoadInfo.speedLimit != null) {
            return lastRoadInfo
        }

        return withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh) {
                    lastRequestTime = currentTime
                    lastRequestLat = lat
                    lastRequestLon = lon
                }

                var newLimit: String? = null
                var newPlaceId: String? = null

                // 1. Roads API Request
                val roadsUrl = "https://roads.googleapis.com/v1/speedLimits?path=$lat,$lon&key=$API_KEY"
                logToDb(context, "RoadsAPI", "Requesting Speed Limit...")
                
                val roadsRequest = Request.Builder()
                    .url(roadsUrl)
                    .addHeader("X-Android-Package", PACKAGE_NAME)
                    .addHeader("X-Android-Cert", SHA1_FINGERPRINT)
                    .build()

                client.newCall(roadsRequest).execute().use { response ->
                    val bodyString = response.body?.string()
                    logToDb(context, "RoadsAPI", "Response Body: $bodyString", response.code)
                    
                    if (response.isSuccessful) {
                        val json = JSONObject(bodyString ?: "{}")
                        val speedLimits = json.optJSONArray("speedLimits")
                        if (speedLimits != null && speedLimits.length() > 0) {
                            val limitObj = speedLimits.getJSONObject(0)
                            newPlaceId = limitObj.optString("placeId")
                            val limit = limitObj.optDouble("speedLimit")
                            val units = limitObj.optString("units")
                            newLimit = if (units == "KPH") (limit * 0.621371).toInt().toString() else limit.toInt().toString()
                        }
                    }
                }

                // 2. Geocoding API Request
                var newRoadName: String? = null
                val geoUrl = if (newPlaceId != null) {
                    "https://maps.googleapis.com/maps/api/geocode/json?place_id=$newPlaceId&key=$API_KEY"
                } else {
                    "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lon&key=$API_KEY"
                }

                logToDb(context, "GeocodingAPI", "Requesting Road Name...")
                val geoRequest = Request.Builder()
                    .url(geoUrl)
                    .addHeader("X-Android-Package", PACKAGE_NAME)
                    .addHeader("X-Android-Cert", SHA1_FINGERPRINT)
                    .build()

                client.newCall(geoRequest).execute().use { response ->
                    val bodyString = response.body?.string()
                    logToDb(context, "GeocodingAPI", "Response Body: $bodyString", response.code)
                    
                    if (response.isSuccessful) {
                        val json = JSONObject(bodyString ?: "{}")
                        val resultsArr = json.optJSONArray("results")
                        if (resultsArr != null && resultsArr.length() > 0) {
                            val resultObj = resultsArr.getJSONObject(0)
                            val addressComponents = resultObj.optJSONArray("address_components")
                            if (addressComponents != null) {
                                for (i in 0 until addressComponents.length()) {
                                    val comp = addressComponents.getJSONObject(i)
                                    val types = comp.optJSONArray("types")
                                    if (types != null) {
                                        for (j in 0 until types.length()) {
                                            if (types.getString(j) == "route") {
                                                newRoadName = comp.optString("short_name")
                                                break
                                            }
                                        }
                                    }
                                    if (newRoadName != null) break
                                }
                            }
                            if (newRoadName == null) {
                                newRoadName = resultObj.optString("formatted_address").split(",").firstOrNull()
                            }
                        }
                    }
                }

                val roadInfo = RoadInfo(newLimit, newRoadName, isFromCache = false)
                
                if (newLimit != null || newRoadName != null) {
                    val db = AppDatabase.getDatabase(context)
                    db.roadDataDao().insertRoadData(RoadData(newPlaceId ?: "lat_${lat}_lon_$lon", newLimit, newRoadName))
                    lastRoadInfo = roadInfo
                    lastPlaceId = newPlaceId
                }

                roadInfo
            } catch (e: Exception) {
                Log.e("SpeedLimitRepo", "API Error", e)
                logToDb(context, "System", "Exception in getRoadData", error = e.toString())
                lastRoadInfo
            }
        }
    }
}
