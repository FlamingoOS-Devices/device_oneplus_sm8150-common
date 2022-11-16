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

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.FileUtils
import android.os.UserHandle
import android.provider.Settings
import android.util.Log

import java.io.File

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAG = CameraMotorController::class.simpleName!!
private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

// Should follow KEY_SETTINGS_PREFIX + KEY_ALWAYS_CAMERA_DIALOG From DeviceSettings
private const val ALWAYS_ON_DIALOG_KEY = "device_setting_always_on_camera_dialog"

private const val CAMERA_EVENT_DELAY_TIME = 200L // ms

private const val FRONT_CAMERA_ID = "1"

// Camera motor paths
private const val BASE_PATH = "/sys/class/motor/"
private const val CAMERA_MOTOR_ENABLE_PATH = BASE_PATH + "enable"
private const val CAMERA_MOTOR_HALL_CALIBRATION = BASE_PATH + "hall_calibration"
private const val CAMERA_MOTOR_DIRECTION_PATH = BASE_PATH + "direction"
private const val CAMERA_MOTOR_POSITION_PATH = BASE_PATH + "position"

// Motor calibration data path
private const val CAMERA_PERSIST_HALL_CALIBRATION = "/mnt/vendor/persist/engineermode/hall_calibration"

// Motor fallback calibration data
private const val HALL_CALIBRATION_DEFAULT = "170,170,480,0,0,480,500,0,0,500,1500"

// Motor control values
const val ENABLED = "1"
enum class Direction(val value: String) {
    DOWN("0"),
    UP("1")
}
enum class Position(val value: String?) {
    DOWN("1"),
    UP("0"),
    UNKNOWN(null)
}

// Camera events
enum class Event {
    CLOSE,
    OPEN
}

class CameraMotorController(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val eventChannel = Channel<Event>(capacity = Channel.CONFLATED)

    private var alertDialog: AlertDialog? = null
    private var eventJob: Job? = null
    private var screenOn = false

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraClosed(cameraId: String) {
            super.onCameraClosed(cameraId)
            if (cameraId != FRONT_CAMERA_ID) {
                return
            }
            eventJob?.cancel()
            eventJob = coroutineScope.launch {
                delay(CAMERA_EVENT_DELAY_TIME)
                eventChannel.send(Event.CLOSE)
            }
        }

        override fun onCameraOpened(cameraId: String, packageId: String) {
            super.onCameraOpened(cameraId, packageId)
            if (cameraId != FRONT_CAMERA_ID) {
                return
            }
            eventJob?.cancel()
            eventJob = coroutineScope.launch {
                delay(CAMERA_EVENT_DELAY_TIME)
                eventChannel.send(Event.OPEN)
            }
        }
    }

    fun init() {
        coroutineScope.launch {
            calibrate()
            cameraManager.registerAvailabilityCallback(availabilityCallback, null)
            for (event in eventChannel) {
                handleEvent(event)
            }
        }
    }

    fun setScreenOn(screenOn: Boolean) {
        this.screenOn = screenOn
    }

    private suspend fun handleEvent(event: Event) {
        when (event) {
            Event.CLOSE -> lowerCamera()
            Event.OPEN -> maybeRaiseCamera()
        }
    }

    private suspend fun maybeRaiseCamera() {
        val alwaysOnDialog = withContext(Dispatchers.IO) {
            Settings.System.getIntForUser(
                context.contentResolver,
                ALWAYS_ON_DIALOG_KEY,
                0,
                UserHandle.USER_CURRENT
            ) == 1
        }
        if (screenOn && !alwaysOnDialog) {
            raiseCamera()
        } else {
            if (alertDialog == null) {
                alertDialog = buildSystemAlert(
                    context = context,
                    message = R.string.popup_camera_dialog_message,
                    negativeButtonText = R.string.popup_camera_dialog_no,
                    negativeButtonAction = {
                        // Go back to home screen
                        context.startHomeActivity()
                    },
                    positiveButtonText = R.string.popup_camera_dialog_raise,
                    positiveButtonAction = {
                        coroutineScope.launch {
                            raiseCamera()
                        }
                    }
                )
            }
            alertDialog?.let { if (!it.isShowing) it.show() }
        }
    }

    private suspend fun raiseCamera() {
        if (DEBUG) Log.d(TAG, "Raising camera")
        setMotorDirection(Direction.UP)
        setMotorEnabled()
    }

    private suspend fun lowerCamera() {
        if (DEBUG) Log.d(TAG, "Lowering camera")
        alertDialog?.let { if (it.isShowing) it.dismiss() }
        setMotorDirection(Direction.DOWN)
        setMotorEnabled()
    }

    fun destroy() {
        cameraManager.unregisterAvailabilityCallback(availabilityCallback)
    }
}

suspend fun calibrate() {
    withContext(Dispatchers.IO) {
        val calibrationData = runCatching {
            FileUtils.readTextFile(File(CAMERA_PERSIST_HALL_CALIBRATION), 0, null)
        }.getOrElse {
            Log.e(TAG, "Failed to read $CAMERA_PERSIST_HALL_CALIBRATION", it)
            HALL_CALIBRATION_DEFAULT
        }
        runCatching {
            FileUtils.stringToFile(CAMERA_MOTOR_HALL_CALIBRATION, calibrationData)
        }.onFailure {
            Log.e(TAG, "Failed to write to $CAMERA_MOTOR_HALL_CALIBRATION", it)
        }
    }
}

suspend fun setMotorDirection(direction: Direction) {
    withContext(Dispatchers.IO) {
        runCatching {
            FileUtils.stringToFile(CAMERA_MOTOR_DIRECTION_PATH, direction.value)
        }.onFailure {
            Log.e(TAG, "Failed to write to $CAMERA_MOTOR_DIRECTION_PATH", it)
        }
    }
}

suspend fun setMotorEnabled() {
    withContext(Dispatchers.IO) {
        runCatching {
            FileUtils.stringToFile(CAMERA_MOTOR_ENABLE_PATH, ENABLED)
        }.onFailure {
            Log.e(TAG, "Failed to write to $CAMERA_MOTOR_ENABLE_PATH", it)
        }
    }
}

suspend fun getMotorPosition(): Position {
    return withContext(Dispatchers.IO) {
        runCatching {
            FileUtils.readTextFile(File(CAMERA_MOTOR_POSITION_PATH), 1, null)
        }.map { pos ->
            Position.values().find { it.value == pos }
        }.getOrElse {
            Log.e(TAG, "Failed to read $CAMERA_MOTOR_POSITION_PATH", it)
            null
        } ?: Position.UNKNOWN
    }
}
