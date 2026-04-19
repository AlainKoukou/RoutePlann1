package com.univ.routeplanner.data.api

import com.google.gson.annotations.SerializedName

data class GeocodeResponse(
    @SerializedName("features") val features: List<GeocodeFeature>?
)

data class GeocodeFeature(
    @SerializedName("properties") val properties: GeocodeProperties?
)

data class GeocodeProperties(
    @SerializedName("label") val label: String?,        // "Hamra, Beirut, Lebanon" — pre-built, preferred
    @SerializedName("name") val name: String?,          // "Hamra" — just the main name
    @SerializedName("locality") val locality: String?,  // "Beirut" — city
    @SerializedName("country") val country: String?     // "Lebanon"
)