@file:JvmName("EncryptedHarmony")

package com.frybits.harmony.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.SecureHarmonyPreferences

/*
 *  Copyright 2020 Pablo Baxter
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
 */

/**
 * Main entry to get Encrypted Harmony Preferences
 *
 * This creates an Encrypted Harmony object.
 * Similar to Harmony, this Encrypted SharedPreferences object is process-safe.
 *
 * <pre>
 *
 *  val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
 *
 *  val sharedPreferences = context.getEncryptedHarmonySharedPreferences(
 *      "secret_shared_prefs",
 *      masterKeyAlias,
 *      context,
 *      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
 *      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
 *  )
 *
 *  // use the shared preferences and editor as you normally would
 *  val editor = sharedPreferences.edit()
 *
 * </pre>
 *
 * @receiver Any valid context
 * @param fileName The desired preference file
 * @param masterKeyAlias The alias of the master key to use
 * @param prefKeyEncryptionScheme The scheme to use for encrypting keys
 * @param prefValueEncryptionScheme The scheme to use for encrypting values
 *
 * @return A [SharedPreferences] object backed by Harmony
 */
@JvmName("getSharedPreferences")
fun Context.getEncryptedHarmonySharedPreferences(
    fileName: String,
    masterKeyAlias: String,
    prefKeyEncryptionScheme: EncryptedSharedPreferences.PrefKeyEncryptionScheme,
    prefValueEncryptionScheme: EncryptedSharedPreferences.PrefValueEncryptionScheme
): SharedPreferences {
    return SecureHarmonyPreferences(fileName, masterKeyAlias, this, prefKeyEncryptionScheme, prefValueEncryptionScheme)
}
