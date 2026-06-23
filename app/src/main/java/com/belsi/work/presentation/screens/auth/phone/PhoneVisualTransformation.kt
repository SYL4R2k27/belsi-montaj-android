package com.belsi.work.presentation.screens.auth.phone

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation для форматирования телефонных номеров
 * Формат: +7 (XXX) XXX-XX-XX
 *
 * Преимущество: не меняет реальный текст, только отображение,
 * что решает проблему прыгающего курсора
 */
class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Берём только цифры из введённого текста
        val digits = text.text.filter { it.isDigit() }.take(11)

        // Форматируем для отображения
        val formatted = formatPhoneNumber(digits)

        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = PhoneOffsetMapping(digits, formatted)
        )
    }

    private fun formatPhoneNumber(digits: String): String {
        return when {
            digits.isEmpty() -> ""
            digits.length == 1 -> "+7 ($digits"
            digits.length <= 4 -> "+7 (${digits.substring(1)}"
            digits.length <= 7 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4)}"
            digits.length <= 9 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            else -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits.substring(9)}"
        }
    }
}

/**
 * Маппинг позиций курсора между реальным текстом (только цифры)
 * и отформатированным текстом (с символами форматирования)
 */
private class PhoneOffsetMapping(
    private val digits: String,
    private val formatted: String
) : OffsetMapping {

    override fun originalToTransformed(offset: Int): Int {
        // Позиция в цифрах -> позиция в отформатированном тексте
        if (offset == 0) return 0
        if (offset > digits.length) return formatted.length

        // Подсчитываем, сколько цифр до позиции курсора
        val digitsBeforeCursor = digits.take(offset).count()

        // Находим позицию в отформатированном тексте
        var count = 0
        for (i in formatted.indices) {
            if (formatted[i].isDigit()) {
                count++
                if (count == digitsBeforeCursor) {
                    return i + 1
                }
            }
        }

        return formatted.length
    }

    override fun transformedToOriginal(offset: Int): Int {
        // Позиция в отформатированном тексте -> позиция в цифрах
        if (offset == 0) return 0
        if (offset >= formatted.length) return digits.length

        // Подсчитываем количество цифр до позиции курсора
        var count = 0
        for (i in 0 until offset.coerceAtMost(formatted.length)) {
            if (formatted[i].isDigit()) {
                count++
            }
        }

        return count
    }
}
