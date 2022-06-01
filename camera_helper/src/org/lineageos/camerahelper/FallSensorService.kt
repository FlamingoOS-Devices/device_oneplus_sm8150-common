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

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log

import androidx.annotation.Keep

private val TAG = FallSensorService::class.simpleName!!
private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

@Keep
class FallSensorService : Service() {

    private lateinit var fallSensor: FallSensor

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    if (DEBUG) Log.d(TAG, "Screen on, enabling fall sensor")
                    fallSensor.enable()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (DEBUG) Log.d(TAG, "Screen off, disabling fall sensor")
                    fallSensor.disable()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "Creating service")
        fallSensor = FallSensor(this)
        registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (DEBUG) Log.d(TAG, "Starting service")
        fallSensor.enable()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service")
        fallSensor.disable()
        fallSensor.destroy()
        unregisterReceiver(screenStateReceiver)
        super.onDestroy()
    }
}
