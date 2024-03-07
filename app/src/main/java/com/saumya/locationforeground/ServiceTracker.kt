package com.saumya.locationforeground

import android.app.ActivityManager
import android.content.Context

private const val LOCATIONS = "location_shared"


data class Location(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val created: Long,
    val speed: Float
)

public fun isMyServiceRunning(
    context: Context,
    serviceClass: Class<LocationForegroundService>
): Boolean {
    val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager;
    for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name.equals(service.service.className))
            return true;

    }
    return false
}

private const val earthRadiusKm: Double = 6372.8

public fun haversine(
    origin: android.location.Location,
    destination: android.location.Location
): Double {
    val dLat = Math.toRadians(destination.latitude - origin.latitude);
    val dLon = Math.toRadians(destination.longitude - origin.longitude);
    val originLat = Math.toRadians(origin.latitude);
    val destinationLat = Math.toRadians(destination.latitude);

    val a = Math.pow(Math.sin(dLat / 2), 2.toDouble()) + Math.pow(
        Math.sin(dLon / 2),
        2.toDouble()
    ) * Math.cos(originLat) * Math.cos(destinationLat);
    val c = 2 * Math.asin(Math.sqrt(a));
    return earthRadiusKm * c;
}
