package com.example.espoti.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

// ============================================================================
// DEVICE LOCATION (Profile > Location)
// ----------------------------------------------------------------------------
// Two things must be on for the app to use the location: the app permission
// and the phone's location switch. These helpers tell which one is missing
// and open the system screen where the user turns it on.
// ============================================================================

enum class DeviceLocationStatus {
    /** Permission granted and location turned on. */
    ON,
    /** The app isn't allowed to read the location yet. */
    PERMISSION_NEEDED,
    /** Permission granted, but the phone's location switch is off. */
    TURNED_OFF
}

val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

fun Context.deviceLocationStatus(): DeviceLocationStatus {
    val granted = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    if (!granted) return DeviceLocationStatus.PERMISSION_NEEDED
    val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return if (LocationManagerCompat.isLocationEnabled(manager)) {
        DeviceLocationStatus.ON
    } else {
        DeviceLocationStatus.TURNED_OFF
    }
}

/** System screen with the phone's location switch. */
fun Context.openDeviceLocationSettings() {
    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}

/** This app's system settings (used when the permission was denied for good). */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    )
}
