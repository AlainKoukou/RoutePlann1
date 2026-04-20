package com.univ.routeplanner.data.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface OrsApiService {

    // 1. Removed @Header because your Interceptor now handles this automatically
    @GET("v2/directions/driving-car")
    suspend fun getDrivingRoute(
        @Query("start") start: String,
        @Query("end") end: String
    ): RouteResponse

    // 2. Removed api_key query because of the Interceptor
    @GET("geocode/reverse")
    suspend fun reverseGeocode(
        @Query("point.lon") lon: Double,
        @Query("point.lat") lat: Double,
        @Query("size") size: Int = 1
    ): GeocodeResponse

    // 3. CRITICAL FIX: Changed return type to Call<GeocodeResponse> to fix the "enqueue" error
    @GET("geocode/search")
    fun searchLocation(
        @Query("text") text: String,
        @Query("size") size: Int = 1
    ): Call<GeocodeResponse>
}