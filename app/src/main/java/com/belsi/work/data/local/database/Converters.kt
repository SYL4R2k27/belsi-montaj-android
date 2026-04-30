package com.belsi.work.data.local.database

import androidx.room.TypeConverter

/**
 * Конвертеры для Room Database
 */
class Converters {

    @TypeConverter
    fun fromString(value: String?): List<String>? {
        return value?.split(",")?.filter { it.isNotBlank() }
    }

    @TypeConverter
    fun toString(list: List<String>?): String? {
        return list?.joinToString(",")
    }
}
