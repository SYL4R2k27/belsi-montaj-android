package com.belsi.work.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Обёртка над Android SpeechRecognizer для конвертации голоса в текст.
 * Используется для голосовых комментариев к фото.
 */
class SpeechToTextHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    /**
     * Проверяет доступность распознавания речи на устройстве
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Начать распознавание речи
     * @param locale Язык распознавания (по умолчанию русский)
     * @param onResult Колбэк с распознанным текстом
     * @param onError Колбэк при ошибке (код ошибки)
     * @param onListeningStateChanged Колбэк при изменении состояния прослушивания
     */
    fun startListening(
        locale: Locale = Locale("ru", "RU"),
        onResult: (String) -> Unit,
        onError: (Int) -> Unit,
        onListeningStateChanged: (Boolean) -> Unit = {}
    ) {
        if (isListening) {
            stopListening()
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        onListeningStateChanged(true)
                        Log.d(TAG, "Ready for speech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech started")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Можно использовать для анимации громкости
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        onListeningStateChanged(false)
                        Log.d(TAG, "Speech ended")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        onListeningStateChanged(false)
                        Log.e(TAG, "Recognition error: $error")
                        onError(error)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        onListeningStateChanged(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        Log.d(TAG, "Recognized: $text")
                        if (text.isNotBlank()) {
                            onResult(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        // Можно использовать для промежуточных результатов
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    /**
     * Остановить распознавание
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        isListening = false
    }

    /**
     * Освободить ресурсы
     */
    fun destroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    companion object {
        private const val TAG = "SpeechToTextHelper"

        /**
         * Описание ошибки по коду
         */
        fun getErrorMessage(errorCode: Int): String {
            return when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи аудио"
                SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на запись"
                SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут сети"
                SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
                SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Нет голосового ввода"
                else -> "Неизвестная ошибка ($errorCode)"
            }
        }
    }
}
