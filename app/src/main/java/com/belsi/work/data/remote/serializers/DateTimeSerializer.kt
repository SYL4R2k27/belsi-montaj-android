package com.belsi.work.data.remote.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Кастомный сериализатор для ISO8601 datetime с timezone и дробными секундами
 * Пример: "2025-12-22T12:52:47.403977+03:00"
 *
 * Поддерживает:
 * - С дробными секундами: 2025-12-22T12:52:47.403977+03:00
 * - Без дробных секунд: 2025-12-22T12:52:47+03:00
 * - Zulu time: 2025-12-22T09:52:47.403977Z
 */
object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }

    override fun deserialize(decoder: Decoder): OffsetDateTime {
        val dateTimeString = decoder.decodeString()
        return try {
            // Пробуем стандартный ISO парсер (поддерживает и с дробными и без)
            OffsetDateTime.parse(dateTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (e: DateTimeParseException) {
            try {
                // Fallback на Instant если не получилось
                val instant = Instant.parse(dateTimeString)
                OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
            } catch (e2: Exception) {
                android.util.Log.e("DateTimeSerializer", "Failed to parse: $dateTimeString", e2)
                // Возвращаем текущее время как fallback
                OffsetDateTime.now()
            }
        }
    }
}

/**
 * Сериализатор для Instant (альтернатива если используется Instant вместо OffsetDateTime)
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val dateTimeString = decoder.decodeString()
        return try {
            Instant.parse(dateTimeString)
        } catch (e: DateTimeParseException) {
            try {
                // Пробуем через OffsetDateTime если есть timezone
                OffsetDateTime.parse(dateTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
            } catch (e2: Exception) {
                android.util.Log.e("InstantSerializer", "Failed to parse: $dateTimeString", e2)
                Instant.now()
            }
        }
    }
}
