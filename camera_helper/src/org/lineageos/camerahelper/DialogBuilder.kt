/*
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
import android.view.WindowManager

import androidx.annotation.StringRes

inline fun buildSystemAlert(
    context: Context,
    @StringRes title: Int? = null,
    @StringRes message: Int? = null,
    @StringRes negativeButtonText: Int? = null,
    crossinline negativeButtonAction: () -> Unit = {},
    @StringRes positiveButtonText: Int? = null,
    crossinline positiveButtonAction: () -> Unit = {},
    cancelable: Boolean = true
) : AlertDialog {
    val dialog = with(AlertDialog.Builder(context)) {
        if (title != null) {
            setTitle(title)
        }
        if (message != null) {
            setMessage(message)
        }
        if (negativeButtonText != null) {
            setNegativeButton(negativeButtonText) { _, _ ->
                negativeButtonAction()
            }
        }
        if (positiveButtonText != null) {
            setPositiveButton(positiveButtonText) { _, _ ->
                positiveButtonAction()
            }
        }
        setCancelable(cancelable)
    }.create()
    return dialog.also {
        it.window.apply {
            attributes = attributes.apply {
                privateFlags = privateFlags or WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
            }
            @Suppress("DEPRECATION")
            setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        }
    }
}