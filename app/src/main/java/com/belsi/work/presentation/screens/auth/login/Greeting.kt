package com.belsi.work.presentation.screens.auth.login

/**
 * FIX(2026-05-12) build19+: динамические приветствия на экране логина.
 *
 * 3 состояния (см. LoginViewModel.GreetingState):
 *   FirstTime         → один-единственный «Добро пожаловать»
 *   Returning(anonymous) → ротация 4 фраз по дню
 *   Personal(named)   → ротация 7 фраз с подстановкой имени
 *
 * Pseudo-random: индекс выбирается по `currentTimeMillis() / DAY_MS`,
 * чтобы в течение одного дня greeting НЕ менялся между запусками
 * (иначе раздражает). На следующий день — другая фраза.
 */
object Greeting {

    private const val DAY_MS = 1000L * 60 * 60 * 24

    /** Тут считаем «утро» / «день» / «вечер» — для extra-context greeting. */
    private fun timeOfDayHint(): String {
        val now = java.util.Calendar.getInstance()
        return when (now.get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
    }

    private val RETURNING_ANON = listOf(
        "С возвращением",
        "Снова в работе",
        "Снова на смене",
        "Рады видеть",
    )

    private val RETURNING_NAMED = listOf<(String) -> String>(
        { name -> "С возвращением,\n$name" },
        { name -> "Снова в деле,\n$name" },
        { name -> "Привет,\n$name" },
        { name -> "Снова на смене,\n$name" },
        { name -> "Рады видеть,\n$name" },
        { name -> "${timeOfDayHint()},\n$name" },
        { name -> "С возвращением\nв команду, $name" },
    )

    private val SUBHEAD_FIRST = "Создайте аккаунт или войдите\nв BELSI.Команда"
    private val SUBHEAD_RETURNING_ANON = listOf(
        "Войдите чтобы продолжить",
        "Войдите в BELSI.Команда",
        "Готовы к работе?",
    )
    private val SUBHEAD_RETURNING_NAMED = listOf(
        "Введите пароль чтобы продолжить",
        "Ваш аккаунт ждёт",
        "Готовы вернуться в команду?",
        "Введите пароль и поехали",
    )

    fun headlineFirst(): String = "Добро\nпожаловать"
    fun subheadFirst(): String = SUBHEAD_FIRST

    fun headlineReturning(): String = pickByDay(RETURNING_ANON)
    fun subheadReturning(): String = pickByDay(SUBHEAD_RETURNING_ANON)

    fun headlinePersonal(name: String): String = pickByDay(RETURNING_NAMED).invoke(name)
    fun subheadPersonal(): String = pickByDay(SUBHEAD_RETURNING_NAMED)

    /**
     * CTA-текст по состоянию:
     *   FirstTime → «Создать аккаунт»
     *   Returning → «Войти»
     *   Personal  → «Войти»
     */
    fun ctaText(isFirstTime: Boolean): String =
        if (isFirstTime) "Создать аккаунт" else "Войти"

    fun firstName(fullName: String?): String {
        if (fullName.isNullOrBlank()) return "друг"
        // «Тирских Владимир Игоревич» → «Владимир» (среднее слово часто = имя)
        // «Геннадий Т» → «Геннадий» (одно слово или ФИО)
        val parts = fullName.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> fullName
            parts.size == 1 -> parts[0]
            parts.size >= 2 -> parts[1]  // ФИО: Тирских [Владимир] Игоревич → Владимир
            else -> parts[0]
        }
    }

    fun initials(fullName: String?): String {
        if (fullName.isNullOrBlank()) return "?"
        val parts = fullName.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
        }
    }

    private fun <T> pickByDay(items: List<T>): T {
        val day = System.currentTimeMillis() / DAY_MS
        return items[(day.mod(items.size.toLong())).toInt()]
    }
}
