package com.frybits.harmony.internal

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock

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
 *
 * FileLock tool
 */

private const val LOG_TAG = "HarmonyFileUtils"

@JvmSynthetic
internal inline fun <T> File.withFileLock(shared: Boolean = false, block: () -> T): T? {
    return synchronized(this) {
        var accessFile: RandomAccessFile? = null
        // Lock the file to prevent other process from writing to it while this read is occurring. This is reentrant
        var lock: FileLock? = null
        try {
            accessFile = RandomAccessFile(this, "rw")
            // File Channel must be write enabled for exclusive file locking
            // This should block the thread, and prevent misuse of CPU cycles
            lock = accessFile.channel.lock(0L, Long.MAX_VALUE, shared)
            return@synchronized block()
        } catch (e: IOException) {
            _InternalHarmonyLog.w(LOG_TAG, "IOException while obtaining file lock", e)
        } catch (e: Error) {
            _InternalHarmonyLog.w(LOG_TAG, "Error while obtaining file lock", e)
        } finally {
            lock?.release()
            accessFile?.close()
        }
        return@synchronized null
    }
}
