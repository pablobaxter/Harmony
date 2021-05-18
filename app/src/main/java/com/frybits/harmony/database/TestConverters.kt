package com.frybits.harmony.database

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.util.UUID

class TestConverters {
    @TypeConverter
    fun fromBytes(byteArray: ByteArray): LongArray {
        val array = arrayListOf<Long>()
        val buffer = ByteBuffer.wrap(byteArray)
        while (buffer.hasRemaining()) {
            array.add(buffer.long)
        }
        return array.toLongArray()
    }

    @TypeConverter
    fun toBytes(longArray: LongArray): ByteArray {
        val buffer = ByteBuffer.allocate(Long.SIZE_BYTES * longArray.size)
        longArray.forEach {
            buffer.putLong(it)
        }
        return buffer.array()
    }

    @TypeConverter
    fun fromUUID(uuid: UUID): String {
        return uuid.toString()
    }

    @TypeConverter
    fun toUUID(name: String): UUID {
        return UUID.fromString(name)
    }
}
