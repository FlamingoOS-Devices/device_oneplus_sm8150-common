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

import android.content.Intent
import android.os.ServiceManager
import android.os.RemoteException
import android.util.Log
import android.view.KeyEvent

import androidx.annotation.Keep

import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.android.internal.os.IDeviceKeyManager
import com.android.internal.os.IKeyHandler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAG = KeyHandler::class.simpleName!!

// Camera motor event key codes
private const val MOTOR_EVENT_MANUAL_TO_DOWN = 184
private const val MOTOR_EVENT_UP_ABNORMAL = 186
private const val MOTOR_EVENT_DOWN_ABNORMAL = 189

private val ScanCodes = intArrayOf(
    MOTOR_EVENT_MANUAL_TO_DOWN,
    MOTOR_EVENT_UP_ABNORMAL,
    MOTOR_EVENT_DOWN_ABNORMAL
)
private val Actions = intArrayOf(KeyEvent.ACTION_DOWN)

private const val DEVICE_KEY_MANAGER = "device_key_manager"

@Keep
class KeyHandler : LifecycleService() {

    private val eventChannel = Channel<KeyEvent>(capacity = Channel.CONFLATED)
    private val keyHandler = object : IKeyHandler.Stub() {
        override fun handleKeyEvent(keyEvent: KeyEvent) {
            lifecycleScope.launch {
                eventChannel.send(keyEvent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch(Dispatchers.Default) {
            registerKeyHandler()
        }
    }

    private suspend fun registerKeyHandler() {
        val service = ServiceManager.getService(DEVICE_KEY_MANAGER) ?: run {
            Log.wtf(TAG, "Device key manager service not found")
            return
        }
        try {
            IDeviceKeyManager.Stub.asInterface(service)
                .registerKeyHandler(keyHandler, ScanCodes, Actions)
            handleKeyEvents()
        } catch(e: RemoteException) {
            Log.e(TAG, "Failed to register key handler", e)
            stopSelf()
        }
    }

    private suspend fun handleKeyEvents() {
        withContext(Dispatchers.Main) {
            for (event in eventChannel) {
                handleKeyEvent(event)
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent) {
        when (event.scanCode) {
            MOTOR_EVENT_MANUAL_TO_DOWN -> showCameraMotorPressWarning()
            MOTOR_EVENT_UP_ABNORMAL -> showCameraMotorCannotGoUpWarning()
            MOTOR_EVENT_DOWN_ABNORMAL -> showCameraMotorCannotGoDownWarning()
        }
    }

    private fun showCameraMotorCannotGoDownWarning() {
        buildSystemAlert(
            context = this,
            title = R.string.warning,
            message = R.string.motor_cannot_go_down_message,
            negativeButtonText = R.string.retry,
            negativeButtonAction = {
                // Close the camera
                lifecycleScope.launch(Dispatchers.IO) {
                    setMotorDirection(Direction.DOWN)
                    setMotorEnabled()
                }
            }
        ).show()
    }

    private fun showCameraMotorCannotGoUpWarning() {
        buildSystemAlert(
            context = this,
            title = R.string.warning,
            message = R.string.motor_cannot_go_up_message,
            negativeButtonText = R.string.retry,
            negativeButtonAction = {
                // Reopen the camera
                lifecycleScope.launch(Dispatchers.IO) {
                    setMotorDirection(Direction.UP)
                    setMotorEnabled()
                }
            },
            positiveButtonText = R.string.close,
            positiveButtonAction = {
                // Close the camera
                lifecycleScope.launch(Dispatchers.IO) {
                    setMotorDirection(Direction.DOWN)
                    setMotorEnabled()
                    withContext(Dispatchers.Main) {
                        // Go back to home screen
                        startHomeActivity()
                    }
                }
            }
        ).show()
    }

    private fun showCameraMotorPressWarning() {
        // Go back to home to close all camera apps first
        startHomeActivity()
        buildSystemAlert(
            context = this,
            title = R.string.warning,
            message = R.string.motor_press_message,
            positiveButtonText = android.R.string.ok
        ).show()
    }
}
