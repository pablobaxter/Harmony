package com.frybits.harmony.core

import android.os.ParcelFileDescriptor
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileLock
import java.util.zip.Adler32
import java.util.zip.Checksum

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

// String source: https://github.com/aosp-mirror/platform_prebuilt/blob/master/ndk/android-ndk-r7/platforms/android-14/arch-arm/usr/include/sys/_errdefs.h#L73
private const val RESOURCE_DEADLOCK_ERROR = "Resource deadlock would occur"

@JvmSynthetic
internal inline fun <T> File.withFileLock(shared: Boolean = false, block: (currPrefCheckSumValue: Long, checkSumObj: Checksum?) -> T): T {
    val pfd = ParcelFileDescriptor.open(
        this,
        ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
    )
    // File Channel must be write enabled for exclusive file locking
    val lockFileChannel =
        if (shared) ParcelFileDescriptor.AutoCloseInputStream(pfd).channel else ParcelFileDescriptor.AutoCloseOutputStream(pfd).channel
    // Lock the file to prevent other process from writing to it while this read is occurring. This is reentrant
    var lock: FileLock? = null
    try {
        // Keep retrying to get the lock
        while (lock == null) {
            try {
                // This should block the thread, and prevent misuse of CPU cycles
                lock = lockFileChannel.lock(0L, Long.MAX_VALUE, shared)
            } catch (e: IOException) {
                // This would not actually cause a deadlock
                // Ignore this specific error and throw all others
                if (e.message != RESOURCE_DEADLOCK_ERROR) {
                    throw e
                }
            }
        }
        val checkSum = if (length() >= Long.SIZE_BYTES) DataInputStream(FileInputStream(pfd.fileDescriptor)).use { it.readLong() } else 0L
        return if (shared) {
            block(checkSum, null)
        } else {
            val adler32 = Adler32()
            val result = block(checkSum, adler32)
            DataOutputStream(FileOutputStream(pfd.fileDescriptor)).use { it.writeLong(adler32.value) }
            writeText("${adler32.value}")
            result
        }
    } finally {
        lock?.release()
        lockFileChannel.close()
    }
}
