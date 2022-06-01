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

import android.os.FileUtils
import android.util.Log

import java.io.File
import java.io.IOException

private const val TAG = "CameraMotorController"

// Camera motor paths
private const val BASE_PATH = "/sys/class/motor"
private const val CAMERA_MOTOR_ENABLE_PATH = "$BASE_PATH/enable"
private const val CAMERA_MOTOR_HALL_CALIBRATION = "$BASE_PATH/hall_calibration"
private const val CAMERA_MOTOR_DIRECTION_PATH = "$BASE_PATH/direction"
private const val CAMERA_MOTOR_POSITION_PATH = "$BASE_PATH/position"

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

fun calibrate() {
    val calibrationData = try {
        FileUtils.readTextFile(File(CAMERA_PERSIST_HALL_CALIBRATION), 0, null)
    } catch (e: IOException) {
        Log.e(TAG, "Failed to read $CAMERA_PERSIST_HALL_CALIBRATION", e)
        HALL_CALIBRATION_DEFAULT
    }
    try {
        FileUtils.stringToFile(CAMERA_MOTOR_HALL_CALIBRATION, calibrationData)
    } catch (e: IOException) {
        Log.e(TAG, "Failed to write to $CAMERA_MOTOR_HALL_CALIBRATION", e)
    }
}

fun setMotorDirection(direction: Direction) {
    try {
        FileUtils.stringToFile(CAMERA_MOTOR_DIRECTION_PATH, direction.value)
    } catch (e: IOException) {
        Log.e(TAG, "Failed to write to $CAMERA_MOTOR_DIRECTION_PATH", e)
    }
}

fun setMotorEnabled() {
    try {
        FileUtils.stringToFile(CAMERA_MOTOR_ENABLE_PATH, ENABLED)
    } catch (e: IOException) {
        Log.e(TAG, "Failed to write to $CAMERA_MOTOR_ENABLE_PATH", e)
    }
}

fun getMotorPosition(): Position {
    return try {
        val position = FileUtils.readTextFile(File(CAMERA_MOTOR_POSITION_PATH), 1, null)
        Position.values().find { it.value == position } ?: Position.UNKNOWN
    } catch (e: IOException) {
        Log.e(TAG, "Failed to read $CAMERA_MOTOR_POSITION_PATH", e)
        Position.UNKNOWN
    }
}