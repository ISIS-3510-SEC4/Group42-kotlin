package com.example.espoti.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import android.annotation.SuppressLint
import kotlin.coroutines.resume
import com.example.espoti.model.LocationFix
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

fun interface LocationService {
    suspend fun currentLocation(): LocationFix
}

class AndroidLocationService(context: Context) : LocationService {

    private val app = context.applicationContext

    override suspend fun currentLocation(): LocationFix {
        val fineGranted = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        check(fineGranted || coarseGranted) {
            "Allow location access to calculate travel status."
        }

        val manager = app.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        val provider = when {
            fineGranted &&
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER

            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER

            else -> error("Turn on device location and try again.")
        }

        val location = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellation = CancellationSignal()

                continuation.invokeOnCancellation {
                    cancellation.cancel()
                }

                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    cancellation,
                    ContextCompat.getMainExecutor(app),
                    androidx.core.util.Consumer<Location> { result ->
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                )
            }
        } ?: error("Location unavailable. Try again outdoors.")

        val ageNanos =
            SystemClock.elapsedRealtimeNanos() -
                    location.elapsedRealtimeNanos

        check(
            location.hasAccuracy() &&
                    location.accuracy >= 0 &&
                    ageNanos in 0L..60_000_000_000L
        ) {
            "Location is too old or inaccurate. Refresh location."
        }

        return LocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.toDouble()
        )
    }
}