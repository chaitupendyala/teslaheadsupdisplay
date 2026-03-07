package com.chaitalkstech.teslaheadsupdisplay

import android.content.Context
import android.location.Location
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    private val API_KEY = BuildConfig.MAPS_API_KEY

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
                var osmRoadName: String? = null
                var osmWayId: Long? = null

                // 1. Overpass API Request (replaces Roads API)
                val overpassQuery = "[out:json][timeout:10];way(around:30,$lat,$lon)[highway][maxspeed];out body 1;"
                val requestBody = overpassQuery.toRequestBody("text/plain".toMediaType())
                val overpassRequest = Request.Builder()
                    .url("https://overpass-api.de/api/interpreter")
                    .post(requestBody)
                    .build()

                logToDb(context, "OverpassAPI", "Requesting OSM road data...")

                client.newCall(overpassRequest).execute().use { response ->
                    val bodyString = response.body?.string()
                    logToDb(context, "OverpassAPI", "Response Body: $bodyString", response.code)
                    if (response.isSuccessful) {
                        val json = JSONObject(bodyString ?: "{}")
                        val elements = json.optJSONArray("elements")
                        if (elements != null && elements.length() > 0) {
                            val element = elements.getJSONObject(0)
                            osmWayId = element.optLong("id")
                            val tags = element.optJSONObject("tags")
                            if (tags != null) {
                                osmRoadName = tags.optString("name").takeIf { it.isNotBlank() }
                                val maxspeed = tags.optString("maxspeed")
                                if (maxspeed.isNotBlank()) {
                                    newLimit = parseMaxspeed(maxspeed)
                                }
                            }
                        }
                    }
                }

                // 2. Geocoding API fallback for road name when OSM has none
                var newRoadName: String? = osmRoadName

                if (newRoadName == null) {
                    val geoUrl = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lon&key=$API_KEY"
                    logToDb(context, "GeocodingAPI", "OSM had no road name, requesting via Geocoding...")
                    val geoRequest = Request.Builder().url(geoUrl).build()
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
                }

                val roadInfo = RoadInfo(newLimit, newRoadName, isFromCache = false)

                if (newLimit != null || newRoadName != null) {
                    val cacheKey = if (osmWayId != null) "osm_way_$osmWayId" else "lat_${lat}_lon_$lon"
                    val db = AppDatabase.getDatabase(context)
                    db.roadDataDao().insertRoadData(RoadData(cacheKey, newLimit, newRoadName))
                    lastRoadInfo = roadInfo
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
