package com.angel.speednet.domain.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Obtiene la ubicación aproximada actual si hay permiso y la ubicación está activa.
 * Devuelve null (y la medición continúa) si no es posible.
 */
class LocationProvider(private val context: Context) {

    suspend fun currentLocation(): Pair<Double, Double>? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager)) {
            return null
        }

        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            suspendCancellableCoroutine { continuation ->
                val tokenSource = CancellationTokenSource()
                continuation.invokeOnCancellation { tokenSource.cancel() }

                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) {
                            continuation.resume(location?.let { it.latitude to it.longitude })
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }
        } catch (_: Exception) {
            null
        }
    }
}
