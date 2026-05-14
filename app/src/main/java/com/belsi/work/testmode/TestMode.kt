package com.belsi.work.testmode

/**
 * FIX(2026-05-14) BELSI 2.0.1: глобальный singleton для UI-test mode.
 *
 * Активируется через intent extras при старте MainActivity:
 *   adb shell am start -n com.belsi.work.debug/.MainActivity \
 *     --es ui-test true \
 *     [--es clear-test-data true]
 *
 * При активном test-mode:
 *  - PrefsManager очищается (logout всех сессий)
 *  - SMS-код возвращается детерминированно из последних 4 цифр номера
 *  - AuthRepository добавляет header `X-Belsi-Test: 1` к каждому запросу
 *    → backend знает что это test-request и НЕ дёргает sms.ru
 *
 * Test-mode читается из BuildConfig.DEBUG-only — release-сборка вообще не активирует.
 *
 * См. docs/ui-testing.md для подробностей.
 */
object TestMode {
    /** True если приложение запущено с --es ui-test true. */
    @Volatile
    var isTestRun: Boolean = false
        private set

    /** True если пользователь явно попросил очистить test-data. */
    @Volatile
    var shouldClearTestData: Boolean = false
        private set

    /** Test-номер с которого пришёл запрос (для логирования). */
    @Volatile
    var lastTestPhone: String? = null

    /**
     * Активирует test-mode. Вызывать только из MainActivity.onCreate(),
     * и только если BuildConfig.DEBUG.
     */
    fun activate(clearData: Boolean) {
        isTestRun = true
        shouldClearTestData = clearData
    }

    /** Сброс — для безопасности (но обычно весь процесс убивается). */
    fun reset() {
        isTestRun = false
        shouldClearTestData = false
        lastTestPhone = null
    }

    /**
     * Генерация детерминированного SMS-кода из номера телефона.
     *
     * Контракт с бэкендом:
     * - Для номеров `+79991234567` (формат +7999...) → код = последние 4 цифры (`4567`)
     * - Для `+7XXXYYY1234` → `1234`
     * - Минимум 4 цифры (если номер короче — паддится нулями)
     */
    fun testCodeFor(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return digits.takeLast(4).padStart(4, '0')
    }
}
