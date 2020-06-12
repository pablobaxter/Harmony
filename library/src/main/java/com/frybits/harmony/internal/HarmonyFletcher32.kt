@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package com.frybits.harmony.internal

import java.util.zip.Checksum

@JvmSynthetic
internal fun harmonyChecksum(): Checksum = HarmonyFletcher32()

private const val BASE = 65535L

private class HarmonyFletcher32 : Checksum {

    private var c0 = 1L
    private var c1 = 0L

    override fun update(b: Int) {
        c0 += b
        if (c0 >= BASE) c0 -= BASE
        c1 += c0
        if (c1 >= BASE) c1 -= BASE
    }

    override fun update(b: ByteArray, off: Int, len: Int) {
        for (i in 0 until len) {
            update(b[off+i].toInt())
        }
    }

    override fun getValue(): Long {
        return c1 shl 16 or c0
    }

    override fun reset() {
        c0 = 1L
        c1 = 0L
    }
}
