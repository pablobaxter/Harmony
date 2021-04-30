@file:JvmName("EncryptedHarmony")

package com.frybits.harmony.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.SecureHarmonyPreferences

/**
 * Main entry to get Encrypted Harmony Preferences
 *
 * This creates an Encrypted Harmony object.
 * Similar to Harmony, this Encrypted SharedPreferences object is process-safe.
 *
 * <pre>
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
 * </pre>
 *
 * @receiver Any valid context
 * @param fileName The desired preference file
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
    // 128 KB is ~3k transactions with single operations.
    return SecureHarmonyPreferences(fileName, masterKeyAlias, this, prefKeyEncryptionScheme, prefValueEncryptionScheme)
}


