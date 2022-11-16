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
import kotlinx.coroutines.launch

private val TAG = FallSensorController::class.simpleName!!
private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

private const val FALL_SENSOR = "camera_protect"

class FallSensorController(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager.getSensorList(Sensor.TYPE_ALL).find {
        it.stringType == FALL_SENSOR
    }

    private var registered = false

    fun init() {
        enableSensor()
    }

    private fun enableSensor() {
        if (sensor == null) {
            Log.e(TAG, "Cannot enable since sensor is null")
            return
        }
        if (registered) {
            if (DEBUG) Log.d(TAG, "Not enabling since it is already registered")
            return
        }
        sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )
        registered = true
    }

    fun setScreenOn(screenOn: Boolean) {
        if (screenOn) {
            if (DEBUG) Log.d(TAG, "Screen on, enabling fall sensor")
            enableSensor()
        } else {
            if (DEBUG) Log.d(TAG, "Screen off, disabling fall sensor")
            disableSensor()
        }
    }

    private fun disableSensor() {
        if (sensor == null) {
            Log.e(TAG, "Cannot disable since sensor is null")
            return
        }
        if (!registered) {
            if (DEBUG) Log.d(TAG, "Not enabling since it is not registered")
            return
        }
        sensorManager.unregisterListener(this, sensor)
        registered = false
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

            showFreeFallDialog()
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

    fun destroy() {
        disableSensor()
    }
}
