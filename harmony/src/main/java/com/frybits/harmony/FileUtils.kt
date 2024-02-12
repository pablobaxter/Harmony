package com.frybits.harmony

import android.system.Os
import com.frybits.harmony.internal.FILE_UTILS_LOG_TAG
import com.frybits.harmony.internal._InternalHarmonyLog
import com.frybits.harmony.internal.withFileLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

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
 * Helper function for obtaining and holding a file lock until the given lambda completes
 *
 * @param shared Flag to indicate if the lock is shared. Defaults to false.
 */
inline fun <T> File.withFileLock(shared: Boolean = false, block: () -> T): T? {
    return synchronized(this) {
        var randomAccessFile: RandomAccessFile? = null
        try {
            randomAccessFile = RandomAccessFile(this, if (shared) "r" else "rw")
            return@synchronized randomAccessFile.withFileLock(shared, block)
        } catch (e: IOException) {
            _InternalHarmonyLog.w(FILE_UTILS_LOG_TAG, "IOException while obtaining file lock", e)
        } catch (e: Error) {
            _InternalHarmonyLog.w(FILE_UTILS_LOG_TAG, "Error while obtaining file lock", e)
        } finally {
            // The object may have a bad file descriptor, but if we don't call "close()", this will lead to a crash when GC cleans this object up
            try {
                randomAccessFile?.close()
            } catch (e: IOException) {
                _InternalHarmonyLog.w(FILE_UTILS_LOG_TAG, "Exception thrown while closing the RandomAccessFile", e)
            }
        }
        return@synchronized null
    }
}

/**
 * Helper function for performing an fsync() on the given [FileOutputStream]
 */
fun FileOutputStream.sync() {
    Os.fsync(fd)
}
