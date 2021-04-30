package com.google.crypto.tink.integration.android

import android.content.Context
import android.content.SharedPreferences
import com.frybits.harmony.getHarmonySharedPreferences
import com.google.crypto.tink.KeysetReader
import com.google.crypto.tink.proto.EncryptedKeyset
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.shaded.protobuf.ExtensionRegistryLite
import com.google.crypto.tink.subtle.Hex
import java.io.CharConversionException
import java.io.FileNotFoundException

/**
 * A [KeysetReader] that can read keysets from private harmony shared preferences on Android.
 *
 * Creates a [KeysetReader] that reads and hex-decodes keysets from the preference
 * name `keysetName` in the private harmony shared preferences file `prefFilename`.
 *
 *
 * If `prefFilename` is null, uses the default name for the harmony shared preferences file.
 *
 * @throws IllegalArgumentException if `keysetName` is null
*/
internal class HarmonyKeysetReader(context: Context, private val keysetName: String, prefFilename: String?) : KeysetReader {

    private val sharedPreferences: SharedPreferences = context.applicationContext.let { appContext ->
        if (prefFilename == null) {
            appContext.getHarmonySharedPreferences(appContext.packageName + "_preference")
        } else {
            appContext.getHarmonySharedPreferences(prefFilename)
        }
    }

    private fun readPref(): ByteArray {
        return try {
            val keysetHex = sharedPreferences.getString(keysetName, null /* default value */) ?: throw FileNotFoundException("can't read keyset; the pref value $keysetName does not exist")
            Hex.decode(keysetHex)
        } catch (ex: ClassCastException) {
            // The original exception is swallowed to prevent leaked key material.
            throw CharConversionException("can't read keyset; the pref value $keysetName is not a valid hex string")
        } catch (ex: IllegalArgumentException) {
            throw CharConversionException("can't read keyset; the pref value $keysetName is not a valid hex string")
        }
    }

    override fun read(): Keyset {
        return Keyset.parseFrom(readPref(), ExtensionRegistryLite.getEmptyRegistry())
    }

    override fun readEncrypted(): EncryptedKeyset {
        return EncryptedKeyset.parseFrom(readPref(), ExtensionRegistryLite.getEmptyRegistry())
    }
}
