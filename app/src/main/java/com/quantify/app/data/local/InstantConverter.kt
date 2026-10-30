package com.quantify.app.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

class InstantConverter {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.epochSeconds

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.fromEpochSeconds(it) }
}
