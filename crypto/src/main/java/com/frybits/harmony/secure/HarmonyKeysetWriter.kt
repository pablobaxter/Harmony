package com.frybits.harmony.secure

import android.annotation.SuppressLint
import com.frybits.harmony.sync
import com.frybits.harmony.withFileLock
import com.google.crypto.tink.KeysetWriter
import com.google.crypto.tink.proto.EncryptedKeyset
import com.google.crypto.tink.proto.Keyset
import java.io.File

/**
 * A [KeysetWriter] that can write keysets to private harmony shared preferences folder on Android.
 *
 * Creates a [KeysetWriter] that hex-encodes and writes keysets to the file
 * passed into the constructor.
 *
 */
@SuppressLint("CommitPrefEdits")
internal class HarmonyKeysetWriter(private val keysetFile: File, private val keysetFileLock: File) : KeysetWriter {

    override fun write(keyset: EncryptedKeyset) {
        keysetFileLock.withFileLock {
            keysetFile.outputStream().use {
                keyset.toBuilder().clearKeysetInfo().build().writeTo(it)
                it.sync()
            }
        }
    }

    override fun write(keyset: Keyset) {
        keysetFileLock.withFileLock {
            keysetFile.outputStream().use {
                keyset.writeTo(it)
                it.sync()
            }
        }
    }
}
