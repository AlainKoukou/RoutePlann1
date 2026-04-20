package com.univ.routeplanner.data.api

import com.google.gson.annotations.SerializedName

data class GeocodeResponse(
    @SerializedName("features") val features: List<GeocodeFeature>?
)

data class GeocodeFeature(
    @SerializedName("geometry") val geometry: GeocodeGeometry?, // Added this line
    @SerializedName("properties") val properties: GeocodeProperties?
)

// Added this class to handle the [longitude, latitude] list
data class GeocodeGeometry(
    @SerializedName("coordinates") val coordinates: List<Double>?
)

data class GeocodeProperties(
    @SerializedName("label") val label: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("locality") val locality: String?,
    @SerializedName("country") val country: String?
)