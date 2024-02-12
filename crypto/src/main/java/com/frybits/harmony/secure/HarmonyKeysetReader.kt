package com.frybits.harmony.secure

import com.frybits.harmony.withFileLock
import com.google.crypto.tink.KeysetReader
import com.google.crypto.tink.proto.EncryptedKeyset
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.shaded.protobuf.ExtensionRegistryLite
import java.io.File
import java.io.FileNotFoundException

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
 *
 */

/**
 * A [KeysetReader] that can read keysets from a private harmony shared preferences folder on Android.
 *
 * Creates a [KeysetReader] that reads and hex-decodes keysets from the file passed into the constructor.
*/
internal class HarmonyKeysetReader(private val keysetFile: File, private val keysetFileLock: File) : KeysetReader {

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
