package com.saumya.locationforeground

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

class LocationForegroundService : Service(), CoroutineScope {

    private val TAG = "LocationForegroundService"
    private val NOTIFICATION_CHANNEL_ID = "LocationForegroundServiceChannel"
    private val LOCATION_INTERVAL = 30000L // 30 seconds
    private val MAX_SPEED_THRESHOLD = 120

    private val binder = LocalBinder()
    private lateinit var job: Job

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var wakeLock: WakeLock
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var notificationManager: NotificationManager
    private var locationArray = mutableListOf<Location>()
    private var lastLocationTimestamp: Long? = null
    private lateinit var lastValidLocation: android.location.Location
    private var distanceArray = mutableListOf<Float>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService = this@LocationForegroundService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        job = Job()

        acquireWakeLock()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        okHttpClient = OkHttpClient()


        // Create notification channel
        createNotificationChannel()

        // Start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService()
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        job.cancel()
        releaseWakeLock()
        stopForeground(true)
        val intent = Intent(this, LocationForegroundService::class.java)
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification =
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle("Location Service")
                .setContentText("Service Killed | Click to restart")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent).build()
        notificationManager.notify(1, notification)
        stopSelf()
        super.onDestroy()
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Location Service"
            val descriptionText = "Location updates"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundService() {
        val notificationIntent = Intent(this, LocationForegroundService::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location Service")
            .setContentText("Fetching location every 30 seconds")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        // Start location updates
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocation(location)
                }
            }
        }

        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL).apply {
                setIntervalMillis(LOCATION_INTERVAL)
                setMinUpdateIntervalMillis(LOCATION_INTERVAL)
                setWaitForAccurateLocation(true)
            }.build()

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun handleLocation(location: android.location.Location) {

        val locationTimeInMillis = location.elapsedRealtimeNanos / 1000000
        val elapsedTimeInSeconds =
            (SystemClock.elapsedRealtimeNanos() / 1000000 - locationTimeInMillis) / 1000
        val currentLocationTimeStamp = location.time / 1000
        val speed: Float?



        if (elapsedTimeInSeconds < 20) {
            if (lastLocationTimestamp == null) {
                locationArray.add(
                    Location(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        created = (location.time / 1000),
                        speed = 0F
                    )
                )
                lastValidLocation = location
                lastLocationTimestamp = currentLocationTimeStamp
            } else if ((currentLocationTimeStamp - lastLocationTimestamp!!) >= 30) {

                val haversineDistance = haversine(location, lastValidLocation)

                val timeDiff = (location.time - lastValidLocation.time) / 1000

                val speedInKmph = (haversineDistance / timeDiff) * 3.6
                if (speedInKmph <= MAX_SPEED_THRESHOLD) {
                    locationArray.add(
                        Location(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            created = (location.time / 1000),
                            speed = speedInKmph.toFloat()
                        )
                    )
                    lastValidLocation = location
                    lastLocationTimestamp = currentLocationTimeStamp
                }
            }
        }

        if (locationArray.size >= 4) {
            launch {
//                sendLocationToServer(locationArray)
            }
        }

    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LocationForegroundService::WakeLock"
        )
        wakeLock.acquire()
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }


    private fun createLocationObject(
        locations: MutableList<Location>,
        companyId: String,
        userId: String,
        batteryLevel: String,
    ): JSONObject {
        val eventsArray = JSONArray()
        val jsonPayload = JSONObject()
        for (location in locations) {
            val locationObject = JSONObject()
            locationObject.put("company_id", companyId)
            locationObject.put("user_id", userId)
            locationObject.put("created", location.created)

            val coordinatesArray = JSONArray()

            coordinatesArray.put(location.latitude)
            coordinatesArray.put(location.longitude)

            val locationData = JSONObject()
            locationData.put("type", "Point")
            locationData.put("coordinates", coordinatesArray)

            locationObject.put("location", locationData)
            locationObject.put("battery_level", batteryLevel)
            locationObject.put("accuracy", location.accuracy)
            locationObject.put("speed", location.speed)
            locationObject.put("testTracking", true)

            eventsArray.put(locationObject)
        }
        jsonPayload.put("events", eventsArray)
        jsonPayload.put("company_id", companyId)
        return jsonPayload
    }

}
