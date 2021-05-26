package com.google.crypto.tink.integration.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.frybits.harmony.getHarmonySharedPreferences
import com.google.crypto.tink.KeysetWriter
import com.google.crypto.tink.proto.EncryptedKeyset
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.subtle.Hex
import java.io.IOException

/**
 * A [KeysetWriter] that can write keysets to private harmony shared preferences on Android.
 *
 * Creates a [KeysetWriter] that hex-encodes and writes keysets to the preference
 * name `keysetName` in the private harmony shared preferences file `prefFileName`.
 *
 *
 * If `prefFileName` is null, uses the default name for the harmony shared preferences file.
 *
 * @throws IllegalArgumentException if `keysetName` is null
 */
@SuppressLint("CommitPrefEdits")
class HarmonyKeysetWriter(context: Context, private val keysetName: String, prefFileName: String?) : KeysetWriter {

    private val editor: SharedPreferences.Editor = context.applicationContext.let { appContext ->
        if (prefFileName == null) {
            appContext.getHarmonySharedPreferences(appContext.packageName + "_preference").edit()
        } else {
            appContext.getHarmonySharedPreferences(prefFileName).edit()
        }
    }

    override fun write(keyset: Keyset) {
        val success = editor.putString(keysetName, Hex.encode(keyset.toByteArray())).commit()
        if (!success) {
            throw IOException("Failed to write to SharedPreferences")
        }
    }

    override fun write(keyset: EncryptedKeyset) {
        val success = editor.putString(keysetName, Hex.encode(keyset.toByteArray())).commit()
        if (!success) {
            throw IOException("Failed to write to SharedPreferences")
        }
    }
}