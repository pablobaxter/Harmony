package com.frybits.harmony.internal

import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import kotlin.jvm.Throws

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
 * FileLock tool
 */

private const val LOG_TAG = "HarmonyFileUtils"

@JvmSynthetic
internal inline fun <T> File.withFileLock(shared: Boolean = false, block: () -> T): T? {
    return synchronized(this) {
        var randomAccessFile: RandomAccessFile? = null
        try {
            randomAccessFile = RandomAccessFile(this, if (shared) "r" else "rw")
            return@synchronized randomAccessFile.withFileLock(shared, block)
        } catch (e: IOException) {
            _InternalHarmonyLog.w(LOG_TAG, "IOException while obtaining file lock", e)
        } catch (e: Error) {
            _InternalHarmonyLog.w(LOG_TAG, "Error while obtaining file lock", e)
        } finally {
            // The object may have a bad file descriptor, but if we don't call "close()", this will lead to a crash when GC cleans this object up
            try {
                randomAccessFile?.close()
            } catch (e: IOException) {
                _InternalHarmonyLog.w(LOG_TAG, "Exception thrown while closing the RandomAccessFile", e)
            }
        }
        return@synchronized null
    }
}

@Throws(IOException::class)
@JvmSynthetic
@VisibleForTesting
internal inline fun <T> RandomAccessFile.withFileLock(shared: Boolean, block: () -> T): T {
    // Lock the file to prevent other process from writing to it while this read is occurring. This is reentrant
    var lock: FileLock? = null
    try {
        // File Channel must be write enabled for exclusive file locking
        // This should block the thread, and prevent misuse of CPU cycles
        lock = channel.lock(0L, Long.MAX_VALUE, shared)
        return block()
    } finally {
        try {
            lock?.release()
        } catch (e: IOException) {
            throw IOException("Unable to release FileLock!", e)
        }
    }
}
