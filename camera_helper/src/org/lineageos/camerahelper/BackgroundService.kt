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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

class BackgroundService : LifecycleService() {

    private val cameraMotorController by lazy {
        CameraMotorController(this, lifecycleScope)
    }
    private val fallSensorController by lazy {
        FallSensorController(this, lifecycleScope)
    }
    private val keyEventManager by lazy {
        KeyEventManager(this, lifecycleScope)
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    cameraMotorController.setScreenOn(true)
                    fallSensorController.setScreenOn(true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    cameraMotorController.setScreenOn(false)
                    fallSensorController.setScreenOn(false)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )
        cameraMotorController.init()
        fallSensorController.init()
        keyEventManager.init()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenStateReceiver)
        cameraMotorController.destroy()
        fallSensorController.destroy()
        keyEventManager.destroy()
        super.onDestroy()
    }
}
