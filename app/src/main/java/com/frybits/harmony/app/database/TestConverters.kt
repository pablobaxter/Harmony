package com.frybits.harmony.app.database

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.util.UUID

/*
 *  Copyright 2021 Pablo Baxter
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
 */

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
