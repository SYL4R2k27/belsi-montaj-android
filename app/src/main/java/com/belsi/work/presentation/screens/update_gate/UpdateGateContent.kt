package com.belsi.work.presentation.screens.update_gate

import com.belsi.work.data.legal.LegalTexts

/**
 * FIX(2026-05-11) BELSI 2.0.0: контент Update Gate в одном месте.
 *
 * Тексты блоков и чекбоксов взяты из утверждённого брендбука
 * (docs/brandbook-belsi-komanda/index.html → секция 15).
 *
 * При смене редакции — менять ЗДЕСЬ. Если поменять текст чекбокса
 * после релиза — старые записи в update_consents остаются с
 * историческим текстом (он сохраняется внутри items JSONB).
 */
object UpdateGateContent {

    data class Feature(
        val emoji: String,
        val text: String,
    )

    data class FeatureBlock(
        val title: String,
        val features: List<Feature>,
    )

    data class Checkbox(
        val id: String,   // стабильный технический id для аудита
        val text: String, // текст как видит пользователь
    )

    /** 5 блоков по 4 строки — финальная редакция из брендбука. */
    val blocks: List<FeatureBlock> = listOf(
        FeatureBlock(
            title = "🏭 Производство — новые роли",
            features = listOf(
                Feature("👷", "Добавлены 5 ролей фабрики: Начальник, Старший, Снабжение, Технолог, Сборщик"),
                Feature("📦", "Партии заказа — видно весь путь от заявки до отгрузки"),
                Feature("⏱", "Своя пауза и причины простоя для фабрики, монтажа, логистики"),
                Feature("🔁", "Один телефон — до 3 ролей сразу (например, бригадир и монтажник)"),
            ),
        ),
        FeatureBlock(
            title = "📡 Работа без интернета",
            features = listOf(
                Feature("📥", "Пауза, задача и фото работают даже когда сеть пропала"),
                Feature("🔄", "Связь появится — всё уйдёт на сервер само"),
                Feature("🖼", "Фото хранятся на телефоне до отправки, ничего не теряется"),
                Feature("📋", "Видно что ещё не ушло — экран «Ожидают отправки»"),
            ),
        ),
        FeatureBlock(
            title = "👤 Профиль и вход",
            features = listOf(
                Feature("🆔", "Вход через Яндекс — имя, телефон и аватар подгружаются сами"),
                Feature("📝", "Регистрация без звонка в офис — пошаговый мастер"),
                Feature("🔐", "Сменить пароль теперь можно прямо в настройках"),
                Feature("📲", "Новая иконка приложения на экране телефона"),
            ),
        ),
        FeatureBlock(
            title = "🛠 Что стало стабильнее",
            features = listOf(
                Feature("♻️", "«Вечная пауза» больше не зависает — починили"),
                Feature("✅", "Случайный двойной тап на кнопку не задваивает действие"),
                Feature("🔔", "Уведомление открывает сразу нужный экран, а не главный"),
                Feature("📐", "Удобно на любом телефоне — складные, планшеты, обычные"),
            ),
        ),
        FeatureBlock(
            title = "🤖 AI-помощник (8 функций)",
            features = listOf(
                Feature("📷", "AI оценивает каждое фото — баллы, шлем, работник в кадре"),
                Feature("🎙", "Голосом — причина простоя и сообщения в чате"),
                Feature("📋", "Сводка дня для куратора + проверка простоя по фото"),
                Feature("💬", "Быстрые ответы, поиск фото словами, приоритет тикетов, прогноз материалов"),
            ),
        ),
    )

    val aiHelperNote: String =
        "AI — это помощник. Окончательное решение по фото, тикетам и простою " +
        "остаётся за куратором. Если AI недоступен, приложение работает в обычном " +
        "режиме. Если интернета нет — оффлайн-режим продолжает писать в очередь."

    /**
     * 9 чекбоксов согласия — финальная редакция.
     *
     * Тексты — единый источник из [LegalTexts.Checkboxes]. Это same-source
     * с экраном регистрации (TermsScreen.kt) и с реквизитами оператора —
     * меняешь в одном месте, обновляется везде.
     *
     * 6 пунктов про апгрейд + 3 пункта общих согласий (TOS/Privacy/EULA).
     * Юзеры 1.2.5 прошлых регистраций могли не подписывать — собираем
     * один раз при first-launch 2.0.0.
     */
    val checkboxes: List<Checkbox> = listOf(
        // --- Про обновление ---
        Checkbox("features",  LegalTexts.Checkboxes.FEATURES),
        Checkbox("ai_helper", LegalTexts.Checkboxes.AI_HELPER),
        Checkbox("xerocode",  LegalTexts.Checkboxes.XEROCODE),
        Checkbox("deprecate", LegalTexts.Checkboxes.DEPRECATE),
        Checkbox("ready",     LegalTexts.Checkboxes.READY),
        Checkbox("age_18",    LegalTexts.Checkboxes.AGE_18),
        // --- Общие согласия (153-ФЗ + бывший экран регистрации) ---
        Checkbox("tos",       LegalTexts.Checkboxes.TOS),
        Checkbox("privacy",   LegalTexts.Checkboxes.PRIVACY),
        Checkbox("eula",      LegalTexts.Checkboxes.EULA),
    )
}
