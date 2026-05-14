package com.belsi.work.audio

/**
 * FIX(2026-05-14) BELSI 2.0.1: порт Telegram-iOS AudioWaveform.swift на Kotlin.
 *
 * Source: https://github.com/TelegramMessenger/Telegram-iOS/blob/master/submodules/AudioWaveform/Sources/AudioWaveform.swift
 *
 * Сжатое битовое представление аудио-волны для отображения waveform у голосовых
 * сообщений в messenger. 5 бит на сэмпл — компактно (100 сэмплов = 63 байта).
 *
 * Формат:
 *   - samples: ByteArray = последовательность Int16 little-endian (по 2 байта на сэмпл)
 *   - peak: Int = максимальная амплитуда (для нормализации при рендере)
 *   - bitstream-репрезентация: 5 бит на сэмпл, упакованы плотно
 *
 * Использование:
 *   1. После записи голосовой → вычислить waveform из PCM-сэмплов (через AudioMath.computeWaveform)
 *   2. Сериализовать через makeBitstream() → 5-битный поток (компактно для сети)
 *   3. На приёме: AudioWaveform(bitstream, bitsPerSample=5) → восстановить samples
 *   4. Рендер в Compose: AudioWaveformView (см. AudioWaveformView.kt)
 */
class AudioWaveform(
    val samples: ByteArray,
    val peak: Int,
) {

    // Восстановление samples из bitstream — через статический фабричный метод
    // (см. companion fromBitstream). Раньше был secondary constructor, но он
    // конфликтовал по сигнатуре с primary (ByteArray + Int).

    /**
     * Сжатие samples в bitstream (5 бит на сэмпл).
     * Аналог Swift `makeBitstream() -> Data`.
     */
    fun makeBitstream(): ByteArray {
        val numSamples = samples.size / 2
        val bitstreamLength = (numSamples * 5) / 8 + if ((numSamples * 5) % 8 == 0) 0 else 1
        // +4 для safe-area при заполнении (как в оригинале)
        val result = ByteArray(bitstreamLength + 4)
        val maxSample = peak

        val sampleArray = ShortArray(numSamples)
        java.nio.ByteBuffer.wrap(samples)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(sampleArray)

        for (i in 0 until numSamples) {
            // value ∈ [0..31] — нормализованная амплитуда
            val absValue = kotlin.math.abs(sampleArray[i].toInt())
            val value = if (maxSample > 0) minOf(31, absValue * 31 / maxSample) else 0
            setBits(result, bitOffset = i * 5, numBits = 5, value = value and 0x1F)
        }

        // Trim до реальной длины
        return result.copyOf(bitstreamLength)
    }

    /**
     * Выделение под-волны (от относительной позиции `from` к `to`, [0..1]).
     * Используется для прогресса проигрывания — частичная подсветка.
     */
    fun subwaveform(from: Double, to: Double): AudioWaveform {
        val normalizedStart = from.coerceIn(0.0, 1.0)
        val normalizedEnd = to.coerceIn(normalizedStart, 1.0)

        val numSamples = samples.size / 2
        val startIndex = (numSamples * normalizedStart).toInt() * 2
        val endIndex = (numSamples * normalizedEnd).toInt() * 2

        val rangeLength = endIndex - startIndex
        val subData = if (rangeLength > 0) {
            samples.copyOfRange(startIndex, endIndex)
        } else {
            ByteArray(0)
        }
        return AudioWaveform(samples = subData, peak = peak)
    }

    /** Кол-во сэмплов в волне. */
    val sampleCount: Int get() = samples.size / 2

    /** Возвращает сэмпл по индексу (Int16 → Int для удобства). */
    fun sampleAt(index: Int): Int {
        val byteIndex = index * 2
        if (byteIndex + 1 >= samples.size) return 0
        return java.nio.ByteBuffer.wrap(samples, byteIndex, 2)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .short.toInt()
    }

    /** Нормализованная амплитуда [0..1] для рендера. */
    fun normalizedAmplitude(index: Int): Float {
        if (peak <= 0) return 0f
        val value = kotlin.math.abs(sampleAt(index))
        return (value.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioWaveform) return false
        return peak == other.peak && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int = 31 * peak + samples.contentHashCode()

    companion object {
        /** Стандартное кол-во баров для отображения voice-сообщения. */
        const val DEFAULT_BAR_COUNT = 100

        /** Стандартное кол-во бит на сэмпл при сериализации. */
        const val DEFAULT_BITS_PER_SAMPLE = 5

        /**
         * Извлечь N битов из произвольной позиции в bitstream.
         * Аналог Swift `private func getBits`.
         */
        private fun getBits(data: ByteArray, bitOffset: Int, numBits: Int): Int {
            val normalizedNumBits = (1 shl numBits) - 1
            val byteOffset = bitOffset / 8
            val normalizedBitOffset = bitOffset % 8

            // Читаем 4 байта начиная с byteOffset (или меньше если не хватает)
            var value = 0
            val available = data.size - byteOffset
            val toRead = minOf(4, available)
            for (i in 0 until toRead) {
                value = value or ((data[byteOffset + i].toInt() and 0xFF) shl (i * 8))
            }
            return (value shr normalizedBitOffset) and normalizedNumBits
        }

        /**
         * Установить N битов на произвольной позиции в bitstream.
         * Аналог Swift `private func setBits`.
         */
        private fun setBits(data: ByteArray, bitOffset: Int, numBits: Int, value: Int) {
            val byteOffset = bitOffset / 8
            val normalizedBitOffset = bitOffset % 8
            // Запись 4 байт через ByteBuffer (LE) — value уже маскирован до numBits
            val shifted = value shl normalizedBitOffset
            val available = data.size - byteOffset
            val toWrite = minOf(4, available)
            for (i in 0 until toWrite) {
                val current = data[byteOffset + i].toInt() and 0xFF
                val byteVal = (shifted ushr (i * 8)) and 0xFF
                data[byteOffset + i] = (current or byteVal).toByte()
            }
        }

        private fun decodeBitstream(bitstream: ByteArray, bitsPerSample: Int): ByteArray {
            val numSamples = (bitstream.size * 8) / bitsPerSample
            val result = ByteArray(numSamples * 2)
            val norm = (1L shl bitsPerSample) - 1L  // peak в bitstream-формате (31 для 5 бит)
            val buffer = java.nio.ByteBuffer.wrap(result).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numSamples) {
                val raw = getBits(bitstream, i * bitsPerSample, bitsPerSample)
                // Восстановление амплитуды (как в Swift: raw * norm / norm = raw, но peak=31 фиксированный)
                val sample = (raw.toLong() * norm / norm).toShort()
                buffer.putShort(i * 2, sample)
            }
            return result
        }

        /**
         * Утилита: вычислить waveform из массива PCM-сэмплов.
         *
         * @param pcmSamples — линейные Int16 sample'ы аудио (например после декодирования WAV/OGG)
         * @param targetBars — желаемое кол-во баров (обычно 100 для voice-сообщения)
         */
        /**
         * Восстановление AudioWaveform из сжатого bitstream'а.
         * Аналог Swift `convenience init(bitstream: Data, bitsPerSample: Int)`.
         */
        fun fromBitstream(bitstream: ByteArray, bitsPerSample: Int = DEFAULT_BITS_PER_SAMPLE): AudioWaveform =
            AudioWaveform(
                samples = decodeBitstream(bitstream, bitsPerSample),
                peak = 31,
            )

        fun fromPcmSamples(pcmSamples: ShortArray, targetBars: Int = DEFAULT_BAR_COUNT): AudioWaveform {
            if (pcmSamples.isEmpty()) return AudioWaveform(ByteArray(0), peak = 31)

            val samplesPerBar = maxOf(1, pcmSamples.size / targetBars)
            val bars = ShortArray(targetBars)
            var maxBarAmplitude = 0
            for (b in 0 until targetBars) {
                val start = b * samplesPerBar
                val end = minOf(pcmSamples.size, start + samplesPerBar)
                if (start >= end) {
                    bars[b] = 0
                    continue
                }
                // RMS (root mean square) лучше передаёт человеческое восприятие громкости
                var sumSquares = 0.0
                for (j in start until end) {
                    sumSquares += pcmSamples[j].toDouble() * pcmSamples[j].toDouble()
                }
                val rms = kotlin.math.sqrt(sumSquares / (end - start))
                val rmsInt = rms.toInt().coerceIn(0, Short.MAX_VALUE.toInt())
                bars[b] = rmsInt.toShort()
                if (rmsInt > maxBarAmplitude) maxBarAmplitude = rmsInt
            }

            val data = ByteArray(targetBars * 2)
            val buffer = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until targetBars) buffer.putShort(i * 2, bars[i])

            return AudioWaveform(samples = data, peak = maxBarAmplitude.coerceAtLeast(1))
        }
    }
}
