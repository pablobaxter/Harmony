package com.frybits.harmony.secure

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
