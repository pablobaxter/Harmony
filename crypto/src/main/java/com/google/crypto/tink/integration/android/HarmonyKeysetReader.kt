package com.google.crypto.tink.integration.android

import com.google.crypto.tink.KeysetReader
import com.google.crypto.tink.proto.EncryptedKeyset
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.shaded.protobuf.ExtensionRegistryLite
import java.io.File
import java.io.FileNotFoundException

/**
 * A [KeysetReader] that can read keysets from a private harmony shared preferences folder on Android.
 *
 * Creates a [KeysetReader] that reads and hex-decodes keysets from the file passed into the constructor.
*/
class HarmonyKeysetReader(private val keysetFile: File, private val keysetFileLock: File) : KeysetReader {

    override fun read(): Keyset {
        return keysetFileLock.withFileLock(shared = true) {
            return@withFileLock keysetFile.inputStream().use {
                return@use Keyset.parseFrom(it, ExtensionRegistryLite.getEmptyRegistry())
            }
        } ?: throw FileNotFoundException("can't read keyset; file lock failed")
    }

    override fun readEncrypted(): EncryptedKeyset {
        return keysetFileLock.withFileLock(shared = true) {
            return@withFileLock keysetFile.inputStream().use {
                return@use EncryptedKeyset.parseFrom(it, ExtensionRegistryLite.getEmptyRegistry())
            }
        } ?: throw FileNotFoundException("can't read keyset; file lock failed")
    }
}
