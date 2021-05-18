package com.frybits.harmony.test

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class LogEvent(
    val uuid: UUID = UUID.randomUUID(),
    val priority: Int,
    val tag: String,
    val message: String
) : Parcelable
