/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.settings.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.provider.Settings

import androidx.annotation.Keep

import com.android.internal.util.flamingo.FileUtils

@Keep
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                restoreVibrationStrengthPreference(context)
            }
        }
    }

    private fun restoreVibrationStrengthPreference(context: Context) {
        if (!FileUtils.isFileWritable(FILE_LEVEL)) return
        val storedValue = Settings.System.getStringForUser(
            context.contentResolver,
            KEY_VIBSTRENGTH,
            UserHandle.USER_CURRENT
        ) ?: DEFAULT
        FileUtils.writeLine(FILE_LEVEL, storedValue)
    }

    companion object {
        private const val KEY_VIBSTRENGTH = "device_setting_vib_strength"
        private const val FILE_LEVEL = "/sys/devices/platform/soc/89c000.i2c/i2c-2/2-005a/leds/vibrator/level"
        private const val DEFAULT = "2"
    }
}
