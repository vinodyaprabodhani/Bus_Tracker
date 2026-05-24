package com.example.util

import kotlin.math.*

object LocationUtils {
    /**
     * Calculates the distance in kilometers between two GPS coordinates using the Haversine formula.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius of the Earth in kilometers
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
    
    /**
     * Formats distance nicely (e.g., 1.2 km or 450 m).
     */
    fun formatDistance(km: Double): String {
        return if (km < 1.0) {
            "${(km * 1000).toInt()} m"
        } else {
            String.format("%.2f km", km)
        }
    }

    /**
     * Formats speed nicely (e.g., 45.2 km/h).
     */
    fun formatSpeed(mps: Float): String {
        val kmh = mps * 3.6
        return String.format("%.1f km/h", kmh)
    }
}
