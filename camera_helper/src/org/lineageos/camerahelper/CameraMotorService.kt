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
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.Display

import androidx.annotation.Keep

private val TAG = CameraMotorService::class.simpleName!!
private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

// Should follow KEY_SETTINGS_PREFIX + KEY_ALWAYS_CAMERA_DIALOG From DeviceSettings
private const val ALWAYS_ON_DIALOG_KEY = "device_setting_always_on_camera_dialog"

private const val CAMERA_EVENT_DELAY_TIME = 100L // ms

private const val FRONT_CAMERA_ID = "1"

private const val MSG_CAMERA_CLOSED = 1000
private const val MSG_CAMERA_OPEN = 1001

@Keep
class CameraMotorService : Service(), Handler.Callback {

    private val handler = Handler(Looper.getMainLooper(), this)

    private lateinit var displayManager: DisplayManager

    private var alertDialog: AlertDialog? = null

    private var closeEvent = 0L
    private var openEvent = 0L

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraClosed(cameraId: String) {
            super.onCameraClosed(cameraId)
            if (cameraId == FRONT_CAMERA_ID) {
                closeEvent = SystemClock.elapsedRealtime()
                if ((SystemClock.elapsedRealtime() - openEvent) < CAMERA_EVENT_DELAY_TIME
                        && handler.hasMessages(MSG_CAMERA_OPEN)) {
                    handler.removeMessages(MSG_CAMERA_OPEN)
                }
                handler.sendEmptyMessageDelayed(
                    MSG_CAMERA_CLOSED,
                    CAMERA_EVENT_DELAY_TIME
                )
            }
        }

        override fun onCameraOpened(cameraId: String, packageId: String) {
            super.onCameraOpened(cameraId, packageId)
            if (cameraId == FRONT_CAMERA_ID) {
                openEvent = SystemClock.elapsedRealtime()
                if ((SystemClock.elapsedRealtime() - closeEvent) < CAMERA_EVENT_DELAY_TIME
                        && handler.hasMessages(MSG_CAMERA_CLOSED)) {
                    handler.removeMessages(MSG_CAMERA_CLOSED)
                }
                handler.sendEmptyMessageDelayed(
                    MSG_CAMERA_OPEN,
                    CAMERA_EVENT_DELAY_TIME
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        calibrate()
        getSystemService(CameraManager::class.java)
            .registerAvailabilityCallback(availabilityCallback, null)
        displayManager = getSystemService(DisplayManager::class.java)
        if (DEBUG) Log.d(TAG, "Starting service")
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service")
        super.onDestroy()
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_CAMERA_CLOSED -> lowerCamera()
            MSG_CAMERA_OPEN -> maybeRaiseCamera()
        }
        return true
    }

    private fun maybeRaiseCamera() {
        val screenOn = displayManager.displays.any { it.state != Display.STATE_OFF }
        val alwaysOnDialog = Settings.System.getIntForUser(
            contentResolver,
            ALWAYS_ON_DIALOG_KEY,
            0,
            UserHandle.USER_CURRENT
        ) == 1
        if (screenOn && !alwaysOnDialog) {
            raiseCamera()
        } else {
            if (alertDialog == null) {
                alertDialog = buildSystemAlert(
                    context = this,
                    message = R.string.popup_camera_dialog_message,
                    negativeButtonText = R.string.popup_camera_dialog_no,
                    negativeButtonAction = {
                        // Go back to home screen
                        startHomeActivity()
                    },
                    positiveButtonText = R.string.popup_camera_dialog_raise,
                    positiveButtonAction = {
                        raiseCamera()
                    }
                )
            }
            alertDialog?.let { if (!it.isShowing) it.show() }
        }
    }

    private fun raiseCamera() {
        if (DEBUG) Log.d(TAG, "Raising camera")
        setMotorDirection(Direction.UP)
        setMotorEnabled()
    }

    private fun lowerCamera() {
        if (DEBUG) Log.d(TAG, "Lowering camera")
        alertDialog?.let { if (it.isShowing) it.dismiss() }
        setMotorDirection(Direction.DOWN)
        setMotorEnabled()
    }
}
