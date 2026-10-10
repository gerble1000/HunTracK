package uk.hunttrack.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class CompassReading(
    val magneticHeadingDeg: Float,
    val accuracy: Int,
    val available: Boolean = true,
)

@Singleton
class CompassRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun observeCompass(): Flow<CompassReading> = callbackFlow {
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationVector == null && (accelerometer == null || magnetometer == null)) {
            trySend(CompassReading(0f, SensorManager.SENSOR_STATUS_UNRELIABLE, available = false))
            awaitClose { }
            return@callbackFlow
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMag = false
        var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        var lastSent = -999f

        val orientation = FloatArray(3)
        val remapped = FloatArray(9)
        val rotMatrix = FloatArray(9)

        fun screenRotation(): Int {
            @Suppress("DEPRECATION")
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return wm.defaultDisplay.rotation
        }

        fun publish(rotationMatrix: FloatArray) {
            val (axisX, axisY) = when (screenRotation()) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            val matrix = if (SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)) {
                remapped
            } else {
                rotationMatrix
            }
            SensorManager.getOrientation(matrix, orientation)
            var deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (deg < 0f) deg += 360f

            val smoothed = if (lastSent < 0f) {
                deg
            } else {
                var delta = deg - lastSent
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                ((lastSent + delta * 0.28f) + 360f) % 360f
            }
            if (abs(smoothed - lastSent) >= 0.35f || lastSent < 0f) {
                lastSent = smoothed
                trySend(CompassReading(smoothed, accuracy, available = true))
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                        publish(rotMatrix)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        for (i in 0..2) gravity[i] = gravity[i] * 0.8f + event.values[i] * 0.2f
                        hasGravity = true
                        if (rotationVector == null && hasGravity && hasMag) {
                            if (SensorManager.getRotationMatrix(rotMatrix, null, gravity, geomagnetic)) {
                                publish(rotMatrix)
                            }
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        for (i in 0..2) geomagnetic[i] = geomagnetic[i] * 0.8f + event.values[i] * 0.2f
                        hasMag = true
                        if (rotationVector == null && hasGravity && hasMag) {
                            if (SensorManager.getRotationMatrix(rotMatrix, null, gravity, geomagnetic)) {
                                publish(rotMatrix)
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, value: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                    sensor?.type == Sensor.TYPE_ROTATION_VECTOR
                ) {
                    accuracy = value
                }
            }
        }

        if (rotationVector != null) {
            sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
            // Also register mag for accuracy status when available
            magnetometer?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        }

        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
