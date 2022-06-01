/*
 * Copyright (c) 2019 The LineageOS Project
 * Copyright (c) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.camerahelper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAG = FallSensor::class.simpleName!!
private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

private const val FALL_SENSOR = "camera_protect"

class FallSensor(private val context: Context) : SensorEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager.getSensorList(Sensor.TYPE_ALL).find {
        it.stringType == FALL_SENSOR
    }

    private var registered = false

    init {
        if (sensor == null) {
            Log.e(TAG, "Failed to find fall sensor $FALL_SENSOR")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.first() <= 0) return
        Log.i(TAG, "Fall detected, ensuring front camera is closed")

        coroutineScope.launch {
            // We shouldn't really bother doing anything if motor is already closed
            if (getMotorPosition() == Position.DOWN) return@launch

            // Close the camera
            setMotorDirection(Direction.DOWN)
            setMotorEnabled()

            withContext(Dispatchers.Main) {
                showFreeFallDialog()
            }
        }
    }

    private fun showFreeFallDialog() {
        buildSystemAlert(
            context = context,
            title = R.string.free_fall_detected_title,
            message = R.string.free_fall_detected_message,
            negativeButtonText = R.string.raise_the_camera,
            negativeButtonAction = {
                // Reopen the camera
                coroutineScope.launch {
                    setMotorDirection(Direction.UP)
                    setMotorEnabled()
                }
            },
            positiveButtonText = R.string.close,
            positiveButtonAction = {
                // Go back to home screen
                context.startHomeActivity()
            }
        ).show()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        /* Empty */
    }

    fun enable() {
        if (registered) {
            if (DEBUG) Log.d(TAG, "Sensor already registered")
            return
        }
        if (sensor == null) {
            Log.e(TAG, "Cannot enable since sensor is null")
            return
        }
        if (DEBUG) Log.d(TAG, "Enabling")
        coroutineScope.launch {
            sensorManager.registerListener(
                this@FallSensor,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }.invokeOnCompletion {
            registered = true
        }
    }

    fun disable() {
        if (!registered) {
            if (DEBUG) Log.d(TAG, "Sensor already unregistered")
            return
        }
        if (sensor == null) {
            Log.e(TAG, "Cannot disable since sensor is null")
            return
        }
        if (DEBUG) Log.d(TAG, "Disabling")
        coroutineScope.launch {
            sensorManager.unregisterListener(this@FallSensor, sensor)
        }.invokeOnCompletion {
            registered = false
        }
    }

    fun destroy() {
        disable()
        coroutineScope.cancel()
    }
}
