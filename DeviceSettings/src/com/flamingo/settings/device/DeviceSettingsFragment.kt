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

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator

import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat

import com.android.internal.util.flamingo.FileUtils
import com.android.internal.util.flamingo.FlamingoUtils
import com.flamingo.support.preference.CustomSeekBarPreference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var vibrator: Vibrator

    override fun onAttach(context: Context) {
        super.onAttach(context)
        vibrator = context.getSystemService(Vibrator::class.java)
    }

    override fun onCreatePreferences(bundle: Bundle?, key: String?) {
        setPreferencesFromResource(R.xml.device_settings, key)

        if (!FileUtils.fileExists(FILE_LEVEL)) {
            preferenceScreen.removePreferenceRecursively(KEY_VIBRATOR_PREFERENCE)
            preferenceScreen.removePreferenceRecursively(KEY_VIBRATOR_FOOTER)
        } else {
            findPreference<CustomSeekBarPreference>(KEY_VIBRATOR_PREFERENCE)
                ?.setOnPreferenceChangeListener { _, newValue ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        runCatching {
                            FileUtils.writeLine(FILE_LEVEL, (newValue as Int).toString())
                        }
                        if (vibrator.hasVibrator()) {
                            vibrator.vibrate(HEAVY_CLICK_EFFECT)
                        }
                    }
                    return@setOnPreferenceChangeListener true
                }
        }
        if (!FlamingoUtils.isPackageInstalled(requireContext(), POPUP_HELPER_PKG_NAME)) {
            preferenceScreen.removePreferenceRecursively(KEY_CAMERA_PREF)
        }
    }

    companion object {
        private const val KEY_VIBRATOR_PREFERENCE = "device_setting_vib_strength"
        private const val KEY_VIBRATOR_FOOTER = "vibration_footer_preference"
        private const val FILE_LEVEL = "/sys/devices/platform/soc/89c000.i2c/i2c-2/2-005a/leds/vibrator/level"

        private val HEAVY_CLICK_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)

        private const val POPUP_HELPER_PKG_NAME = "org.lineageos.camerahelper"
        private const val KEY_CAMERA_PREF = "device_setting_always_on_camera_dialog"
    }
}
