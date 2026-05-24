package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journeys")
data class JourneyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fromStation: String,
    val toStation: String,
    val distanceKm: Double,
    val fareLkr: Double,
    val durationMinutes: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val busCategory: String = "Normal",
    val busType: String = "Private",
    val busNumber: String = ""
)
