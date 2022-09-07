package com.frybits.harmony

import android.content.SharedPreferences
import android.os.Build

/*
 *  Copyright 2022 Pablo Baxter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * Created by Pablo Baxter (Github: pablobaxter)
 * https://github.com/pablobaxter/Harmony
 *
 */

/**
 * Similar to [SharedPreferences.OnSharedPreferenceChangeListener], except that this will not emit
 * a `null` key when preferences are cleared. Instead, another function is provided to notify of
 * preferences being cleared.
 */
interface OnHarmonySharedPreferenceChangedListener: SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Called when a shared preference is changed, added, or removed. This
     * may be called even if a preference is set to its existing value.
     *
     * This callback will be run on your main thread.
     *
     * **Note:** This callback will not be triggered when preferences are cleared
     * via [SharedPreferences.Editor.clear], unless targeting [Build.VERSION_CODES.R]
     * on devices running OS versions [Build.VERSION_CODES.R]
     * or later.
     *
     * @param sharedPreferences The [SharedPreferences] that received the change.
     */
    fun onSharedPreferencesCleared(sharedPreferences: SharedPreferences)
}
