@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package com.google.crypto.tink.integration.android

import android.content.Context
import java.io.File

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

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"
internal fun Context.harmonyPrefsFolder() = File(filesDir, HARMONY_PREFS_FOLDER).apply { if (!exists()) mkdirs() }

internal fun Context.keysetFile(prefFileName: String, type: String): File {
    // Folder containing all harmony preference files
    val harmonyPrefsFolder = File(harmonyPrefsFolder(), prefFileName).apply { if (!exists()) mkdirs() }
    return File(harmonyPrefsFolder, "$type.harmony.keyset")
}

internal fun Context.keysetFileLock(prefFileName: String, type: String): File {
    // Folder containing all harmony preference files
    val harmonyPrefsFolder = File(harmonyPrefsFolder(), prefFileName).apply { if (!exists()) mkdirs() }
    return File(harmonyPrefsFolder, "$type.harmony.keyset.lock")
}
