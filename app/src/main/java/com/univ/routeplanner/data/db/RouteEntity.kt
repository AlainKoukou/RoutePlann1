package com.univ.routeplanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val origin: String,
    val destination: String,
    val originName: String? = null,         // NEW
    val destinationName: String? = null,    // NEW
    val distanceMeters: Double,
    val durationSeconds: Double,
    val geometryJson: String,
    val fetchedAt: Long
)