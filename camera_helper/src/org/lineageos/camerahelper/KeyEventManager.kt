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
import android.os.Binder
import android.os.ServiceManager
import android.os.RemoteException
import android.util.Log
import android.view.KeyEvent

import com.android.internal.os.IDeviceKeyManager
import com.android.internal.os.IKeyHandler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAG = KeyEventManager::class.simpleName!!

// Camera motor event key codes
private const val MOTOR_EVENT_MANUAL_TO_DOWN = 184
private const val MOTOR_EVENT_UP_ABNORMAL = 186
private const val MOTOR_EVENT_DOWN_ABNORMAL = 189

private const val DEVICE_KEY_MANAGER = "device_key_manager"

class KeyEventManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    private val eventChannel = Channel<Int>(capacity = Channel.CONFLATED)
    private val token = Binder()
    private val keyHandler = object : IKeyHandler.Stub() {
        override fun handleKeyEvent(keyEvent: KeyEvent) {
            coroutineScope.launch {
                eventChannel.send(keyEvent.scanCode)
            }
        }
    }

    fun init() {
        coroutineScope.launch(Dispatchers.Default) {
            registerKeyHandler()
        }
    }

    private fun getDeviceKeyManager(): IDeviceKeyManager? {
        val service = ServiceManager.getService(DEVICE_KEY_MANAGER) ?: run {
            Log.wtf(TAG, "Device key manager service not found")
            return null
        }
        return IDeviceKeyManager.Stub.asInterface(service)
    }

    private suspend fun registerKeyHandler() {
        try {
            getDeviceKeyManager()?.registerKeyHandler(
                token,
                keyHandler,
                intArrayOf(
                    MOTOR_EVENT_MANUAL_TO_DOWN,
                    MOTOR_EVENT_UP_ABNORMAL,
                    MOTOR_EVENT_DOWN_ABNORMAL
                ),
                intArrayOf(KeyEvent.ACTION_DOWN),
                -1
            )
            handleKeyEvents()
        } catch(e: RemoteException) {
            Log.e(TAG, "Failed to register key handler", e)
        }
    }

    private fun unregisterKeyHandler() {
        try {
            getDeviceKeyManager()?.unregisterKeyHandler(token)
        } catch(e: RemoteException) {
            Log.e(TAG, "Failed to register key handler", e)
        }
    }

    fun destroy() {
        unregisterKeyHandler()
    }

    private suspend fun handleKeyEvents() {
        withContext(Dispatchers.Main) {
            for (event in eventChannel) {
                handleKeyEvent(event)
            }
        }
    }

    private fun handleKeyEvent(scanCode: Int) {
        when (scanCode) {
            MOTOR_EVENT_MANUAL_TO_DOWN -> showCameraMotorPressWarning()
            MOTOR_EVENT_UP_ABNORMAL -> showCameraMotorCannotGoUpWarning()
            MOTOR_EVENT_DOWN_ABNORMAL -> showCameraMotorCannotGoDownWarning()
        }
    }

    private fun showCameraMotorCannotGoDownWarning() {
        buildSystemAlert(
            context = context,
            title = R.string.warning,
            message = R.string.motor_cannot_go_down_message,
            negativeButtonText = R.string.retry,
            negativeButtonAction = {
                // Close the camera
                coroutineScope.launch {
                    setMotorDirection(Direction.DOWN)
                    setMotorEnabled()
                }
            }
        ).show()
    }

    private fun showCameraMotorCannotGoUpWarning() {
        buildSystemAlert(
            context = context,
            title = R.string.warning,
            message = R.string.motor_cannot_go_up_message,
            negativeButtonText = R.string.retry,
            negativeButtonAction = {
                // Reopen the camera
                coroutineScope.launch {
                    setMotorDirection(Direction.UP)
                    setMotorEnabled()
                }
            },
            positiveButtonText = R.string.close,
            positiveButtonAction = {
                // Close the camera
                coroutineScope.launch {
                    setMotorDirection(Direction.DOWN)
                    setMotorEnabled()
                    // Go back to home screen
                    context.startHomeActivity()
                }
            }
        ).show()
    }

    private fun showCameraMotorPressWarning() {
        // Go back to home to close all camera apps first
        context.startHomeActivity()
        buildSystemAlert(
            context = context,
            title = R.string.warning,
            message = R.string.motor_press_message,
            positiveButtonText = android.R.string.ok
        ).show()
    }
}
