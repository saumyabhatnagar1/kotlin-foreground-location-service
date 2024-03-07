package com.saumya.locationforeground

class KalmanUtil {
    private val MinAccuracy: Float = 1f

    private var Q_metres_per_second: Float = 0f
    private var TimeStamp_milliseconds: Long = 0
    private var lat: Double = 0.toDouble()
    private var lng: Double = 0.toDouble()

    //    var consecutiveRejectCount: Number = 0
    private var variance: Float =
        0.toFloat() // P matrix.  Negative means object uninitialised.  NB: units irrelevant, as long as same units used throughout

    fun KalmanLatLong(Q_metres_per_second: Float) {
        this.Q_metres_per_second = Q_metres_per_second
        variance = -1f
    }

    fun get_TimeStamp(): Long {
        return TimeStamp_milliseconds
    }

    fun get_lat(): Double {
        return lat
    }

    fun get_lng(): Double {
        return lng
    }

    fun get_accuracy(): Float {
        return Math.sqrt(variance.toDouble()).toFloat()
    }

    fun SetState(lat: Double, lng: Double, accuracy: Float, TimeStamp_milliseconds: Long) {
        this.lat = lat
        this.lng = lng
        variance = accuracy * accuracy
        this.TimeStamp_milliseconds = TimeStamp_milliseconds
    }


    fun Process(
        lat_measurement: Double,
        lng_measurement: Double,
        accuracy: Float,
        TimeStamp_milliseconds: Long
    ) {
        var accuracy = accuracy
        if (accuracy < MinAccuracy) accuracy = MinAccuracy

        if (variance < 0) {
            // if variance < 0, object is uninitialized, so initialise with current values
            this.TimeStamp_milliseconds = TimeStamp_milliseconds
            lat = lat_measurement
            lng = lng_measurement
            variance = accuracy * accuracy
        } else {
            // else apply Kalman filter methodology

            val TimeInc_milliseconds = TimeStamp_milliseconds - this.TimeStamp_milliseconds

            if (TimeInc_milliseconds > 0) {
                // time has moved on, so the uncertainty in the current position increases
                variance += TimeInc_milliseconds.toFloat() * Q_metres_per_second * Q_metres_per_second / 1000
                this.TimeStamp_milliseconds = TimeStamp_milliseconds
                // TO DO: USE VELOCITY INFORMATION HERE TO GET A BETTER ESTIMATE OF CURRENT POSITION
            }

            // Kalman gain matrix K = Covariance * Inverse(Covariance + MeasurementVariance)
            // NB: because K is dimensionless, it doesn't matter that variance has different units to lat and lng
            val K = variance / (variance + accuracy * accuracy)
            // apply K
            lat += K * (lat_measurement - lat)
            lng += K * (lng_measurement - lng)
            // new Covariance  matrix is (IdentityMatrix - K) * Covariance
            variance *= (1 - K)
        }
    }
}