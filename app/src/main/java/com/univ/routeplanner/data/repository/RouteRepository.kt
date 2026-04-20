package com.univ.routeplanner.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.univ.routeplanner.data.api.OrsApiService
import com.univ.routeplanner.data.api.RetrofitClient
import com.univ.routeplanner.data.db.RouteDao
import com.univ.routeplanner.data.db.RouteEntity

class RouteRepository(
    private val apiService: OrsApiService = RetrofitClient.api,
    private val dao: RouteDao
) {

    private val gson = Gson()

    suspend fun getRoute(origin: String, destination: String): RouteResult {
        // Removed apiKey = BuildConfig.ORS_API_KEY because the Interceptor handles it now
        val response = apiService.getDrivingRoute(
            start = origin,
            end = destination
        )

        val feature = response.features?.firstOrNull()
        val segment = feature?.properties?.segments?.firstOrNull()
            ?: throw IllegalStateException("No route data returned from API")

        val coordinates: List<List<Double>> = feature.geometry?.coordinates ?: emptyList()
        val geometryJson = gson.toJson(coordinates)
        val geometryPairs = coordinates.mapNotNull { pt ->
            if (pt.size >= 2) Pair(pt[0], pt[1]) else null
        }

        // Reverse-geocode both endpoints (fails gracefully if they can't be resolved)
        val originName = reverseGeocodeSafely(origin)
        val destinationName = reverseGeocodeSafely(destination)

        val entity = RouteEntity(
            origin = origin,
            destination = destination,
            originName = originName,
            destinationName = destinationName,
            distanceMeters = segment.distance,
            durationSeconds = segment.duration,
            geometryJson = geometryJson,
            fetchedAt = System.currentTimeMillis()
        )
        dao.insert(entity)

        return RouteResult(
            distanceMeters = segment.distance,
            durationSeconds = segment.duration,
            origin = origin,
            destination = destination,
            originName = originName,
            destinationName = destinationName,
            source = "live API",
            geometry = geometryPairs
        )
    }

    suspend fun getLatestCachedRoute(): RouteResult? {
        val entity = dao.getLatestRoute() ?: return null

        val listType = object : TypeToken<List<List<Double>>>() {}.type
        val coords: List<List<Double>> = try {
            gson.fromJson(entity.geometryJson, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val geometryPairs = coords.mapNotNull { pt ->
            if (pt.size >= 2) Pair(pt[0], pt[1]) else null
        }

        return RouteResult(
            distanceMeters = entity.distanceMeters,
            durationSeconds = entity.durationSeconds,
            origin = entity.origin,
            destination = entity.destination,
            originName = entity.originName,
            destinationName = entity.destinationName,
            source = "offline cache",
            geometry = geometryPairs
        )
    }

    suspend fun getAllHistory(): List<RouteEntity> {
        return dao.getAllRoutes()
    }

    suspend fun clearCache() {
        dao.clearAll()
    }

    private suspend fun reverseGeocodeSafely(coords: String): String? {
        return try {
            val parts = coords.split(",")

            if (parts.size != 2) return null

            val lon = parts[0].trim().toDoubleOrNull() ?: return null
            val lat = parts[1].trim().toDoubleOrNull() ?: return null

            // Removed apiKey = BuildConfig.ORS_API_KEY here as well
            val response = apiService.reverseGeocode(
                lon = lon,
                lat = lat
            )

            val props = response.features
                ?.firstOrNull()
                ?.properties
                ?: return null

            val rawLabel = props.label
                ?: listOfNotNull(
                    props.name ?: props.locality,
                    props.country
                )
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() }

            rawLabel
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.take(2)
                ?.joinToString(", ")

        } catch (e: Exception) {
            null
        }
    }
}

data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val origin: String,
    val destination: String,
    val originName: String? = null,
    val destinationName: String? = null,
    val source: String,
    val geometry: List<Pair<Double, Double>> = emptyList()
)