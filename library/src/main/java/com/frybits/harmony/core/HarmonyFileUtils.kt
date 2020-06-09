package com.frybits.harmony.core

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.Channel
import java.nio.channels.FileLock

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private const val LOG_TAG = "HarmonyFileUtils"

@JvmSynthetic
internal inline fun <T> File.withFileLock(shared: Boolean = false, block: () -> T): T? {
    return synchronized(this) {
        var lockFileChannel: Channel? = null
        // Lock the file to prevent other process from writing to it while this read is occurring. This is reentrant
        var lock: FileLock? = null
        try {
            // File Channel must be write enabled for exclusive file locking
            lockFileChannel = RandomAccessFile(this, "rw").channel
            // This should block the thread, and prevent misuse of CPU cycles
            lock = lockFileChannel.lock(0L, Long.MAX_VALUE, shared)
            return@synchronized block()
        } catch (e: IOException) {
            _InternalHarmonyLog.w(LOG_TAG, "IOException while obtaining file lock", e)
        } catch (e: Error) {
            _InternalHarmonyLog.w(LOG_TAG, "Error while obtaining file lock", e)
        } finally {
            _InternalHarmonyLog.w(LOG_TAG, "Unlocking file lock! $name")
            lock?.release()
            lockFileChannel?.close()
        }
        return@synchronized null
    }
}
