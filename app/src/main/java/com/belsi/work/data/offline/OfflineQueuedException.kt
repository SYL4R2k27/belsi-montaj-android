package com.belsi.work.data.offline

/**
 * FIX(2026-05-05): Особое исключение для случая когда действие сохранено в offline-очередь.
 *
 * Repository возвращает Result.failure(OfflineQueuedException(...)) — это НЕ настоящая
 * ошибка для юзера. ViewModel/UI могут это распознать по типу и показать
 * мягкое сообщение «📤 будет отправлено когда появится связь» вместо «Ошибка сети».
 *
 * Для existing UI который не делает разбор по типу — это всё равно failure,
 * показывает текст message. Это лучше чем «Unknown host» или «timeout».
 *
 * Использование в UI:
 *
 *   .onFailure { e ->
 *       if (e is OfflineQueuedException) {
 *           snackbar("📤 ${'$'}{e.message}")
 *       } else {
 *           snackbar("⚠️ ${'$'}{e.message}")
 *       }
 *   }
 */
class OfflineQueuedException(
    message: String = "Действие сохранено в очередь, отправится когда появится связь",
    cause: Throwable? = null,
) : Exception(message, cause)
