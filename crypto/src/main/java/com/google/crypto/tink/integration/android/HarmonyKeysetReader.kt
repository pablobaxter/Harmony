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
import java.io.IOException

/**
 * A [KeysetReader] that can read keysets from private harmony shared preferences on Android.
 *
 * @since 1.0.0
 */
internal class HarmonyKeysetReader : KeysetReader {
    private val sharedPreferences: SharedPreferences
    private val keysetName: String

    /**
     * Creates a [KeysetReader] that reads and hex-decodes keysets from the preference
     * name `keysetName` in the private harmony shared preferences file `prefFilename`.
     *
     *
     * If `prefFilename` is null, uses the default name for the harmony shared preferences file.
     *
     * @throws IOException if cannot read the keyset
     * @throws IllegalArgumentException if `keysetName` is null
     */
    constructor(context: Context, keysetName: String, prefFilename: String?) {
        this.keysetName = keysetName
        val appContext = context.applicationContext
        sharedPreferences = if (prefFilename == null) {
            appContext.getHarmonySharedPreferences(appContext.packageName + "_preference")
        } else {
            appContext.getHarmonySharedPreferences(prefFilename)
        }
    }

    constructor(keysetName: String, sharedPreferences: SharedPreferences) {
        this.keysetName = keysetName
        this.sharedPreferences = sharedPreferences
    }

    @Throws(IOException::class)
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

    @Throws(IOException::class)
    override fun read(): Keyset {
        return Keyset.parseFrom(readPref(), ExtensionRegistryLite.getEmptyRegistry())
    }

    @Throws(IOException::class)
    override fun readEncrypted(): EncryptedKeyset {
        return EncryptedKeyset.parseFrom(readPref(), ExtensionRegistryLite.getEmptyRegistry())
    }
}
