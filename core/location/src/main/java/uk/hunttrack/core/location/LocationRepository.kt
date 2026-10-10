package uk.hunttrack.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import uk.hunttrack.domain.model.GpsFix

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val permissionGranted = MutableStateFlow(hasPermission())
    private val _latestFix = MutableStateFlow<GpsFix?>(null)
    val latestFix: StateFlow<GpsFix?> = _latestFix.asStateFlow()

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /** Call after the runtime permission dialog so GPS collectors resume. */
    fun refreshPermission() {
        permissionGranted.value = hasPermission()
    }

    /**
     * Live GPS updates. Survives permission being granted after collectors start
     * (via [refreshPermission]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<GpsFix> = permissionGranted.flatMapLatest { granted ->
        if (!granted) emptyFlow() else locationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun locationUpdates(): Flow<GpsFix> = callbackFlow {
        try {
            lastKnown()?.let { fix ->
                _latestFix.value = fix
                trySend(fix)
            }
        } catch (_: Exception) {
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_500L)
            .setMinUpdateIntervalMillis(800L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val fix = loc.toGpsFix()
                    _latestFix.value = fix
                    trySend(fix)
                }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): GpsFix? {
        if (!hasPermission()) return null
        return try {
            client.lastLocation.await()?.toGpsFix()?.also { _latestFix.value = it }
        } catch (_: Exception) {
            null
        }
    }

    /** Best available fix for placing a marker: live cache, then last known. */
    suspend fun bestFixOrNull(): GpsFix? {
        refreshPermission()
        if (!hasPermission()) return null
        _latestFix.value?.let { return it }
        return lastKnown()
    }

    /** Default UK central point (Midlands) when GPS unavailable. */
    fun fallbackFix(): GpsFix = GpsFix(
        latitude = 52.4862,
        longitude = -1.8904,
        accuracyMeters = 999f,
        altitudeMeters = null,
        speedMps = null,
        bearingDegrees = null,
        timestampEpochMs = System.currentTimeMillis(),
    )
}

private fun Location.toGpsFix() = GpsFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    altitudeMeters = if (hasAltitude()) altitude else null,
    speedMps = if (hasSpeed()) speed else null,
    bearingDegrees = if (hasBearing()) bearing else null,
    timestampEpochMs = time,
)
