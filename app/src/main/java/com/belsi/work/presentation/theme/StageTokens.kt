package com.belsi.work.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Belsi.Монтаж — Design Tokens v2 (2.1.0, May 2026) — домен «окна / этапы / готовность».
 *
 * Расширяет Design System v2 (Color.kt / Theme.kt) под модель данных v3:
 *   Object → Floor → Zone → Cabinet → Window (+ 3 этапа монтажа).
 *
 * Ничего из существующей темы не меняет — только добавляет доменные токены.
 * Источник модели: docs/plans/2026-05-20-2.1.0-object-schema-v3-real-data.md
 */

// ──────────────────────────────────────────────────────────────────────
// 3 ЭТАПА МОНТАЖА окна
// ──────────────────────────────────────────────────────────────────────

/** Этап монтажа окна. dbValue совпадает со значением в site_object_windows.status_*. */
enum class StageType(val dbValue: String, val label: String, val emoji: String) {
    KARKAS("karkas", "Каркас", "🔨"),       // 🔨
    PODOKONNIK("podokonnik", "Подоконник", "🪵"), // 🪵
    EKRAN("ekran", "Экран радиатора", "🛡"); // 🛡

    companion object {
        fun fromDb(value: String?): StageType? = entries.firstOrNull { it.dbValue == value }
    }
}

// ──────────────────────────────────────────────────────────────────────
// 6 СТАТУСОВ этапа (один на каждый из 3 этапов окна)
// ──────────────────────────────────────────────────────────────────────

/**
 * Статус одного этапа. dbValue совпадает с CHECK-констрейнтом в БД.
 * Прогресс монтажа: not_started → in_progress → done → accepted → approved (или rework).
 */
enum class StageStatus(val dbValue: String, val label: String) {
    NOT_STARTED("not_started", "Не начато"),
    IN_PROGRESS("in_progress", "В работе"),
    DONE("done", "Готово"),
    ACCEPTED("accepted", "Принято"),
    APPROVED("approved", "Подтверждено"),
    REWORK("rework", "На переделку");

    companion object {
        fun fromDb(value: String?): StageStatus = entries.firstOrNull { it.dbValue == value } ?: NOT_STARTED
    }
}

/**
 * Статичный маппинг статус → цвет (на основе primitive-палитры).
 * Использовать в @Preview / non-composable контекстах. В @Composable предпочесть [stageStatusColor].
 */
object StageStatusColors {
    val notStarted: Color = Slate300
    val inProgress: Color = Amber500
    val done: Color = Emerald500
    val accepted: Color = Sky500
    val approved: Color = Violet600
    val rework: Color = Rose500

    fun of(status: StageStatus): Color = when (status) {
        StageStatus.NOT_STARTED -> notStarted
        StageStatus.IN_PROGRESS -> inProgress
        StageStatus.DONE -> done
        StageStatus.ACCEPTED -> accepted
        StageStatus.APPROVED -> approved
        StageStatus.REWORK -> rework
    }
}

/** Цвет статуса этапа из текущей темы (light/dark-aware через belsiColors). */
@Composable
fun stageStatusColor(status: StageStatus): Color {
    val belsi = MaterialTheme.belsiColors
    return when (status) {
        StageStatus.NOT_STARTED -> MaterialTheme.colorScheme.outline
        StageStatus.IN_PROGRESS -> belsi.warning
        StageStatus.DONE -> belsi.success
        StageStatus.ACCEPTED -> belsi.info
        StageStatus.APPROVED -> belsi.ai
        StageStatus.REWORK -> MaterialTheme.colorScheme.error
    }
}

@Composable
fun stageStatusColor(dbValue: String?): Color = stageStatusColor(StageStatus.fromDb(dbValue))

/**
 * Computed overall-статус окна по 3 этапам (зеркало SQL-комментария в схеме v3):
 *  rework если хоть один rework; approved/accepted/done если ВСЕ такие;
 *  in_progress если хоть один начат; иначе not_started.
 */
fun windowOverallStatus(
    karkas: StageStatus,
    podokonnik: StageStatus,
    ekran: StageStatus,
): StageStatus {
    val all = listOf(karkas, podokonnik, ekran)
    return when {
        all.any { it == StageStatus.REWORK } -> StageStatus.REWORK
        all.all { it == StageStatus.APPROVED } -> StageStatus.APPROVED
        all.all { it == StageStatus.ACCEPTED } -> StageStatus.ACCEPTED
        all.all { it == StageStatus.DONE } -> StageStatus.DONE
        all.any { it != StageStatus.NOT_STARTED } -> StageStatus.IN_PROGRESS
        else -> StageStatus.NOT_STARTED
    }
}

/** Прогресс окна 0f..1f: каждый этап = done/accepted/approved → вклад 1/3. */
fun windowProgress(
    karkas: StageStatus,
    podokonnik: StageStatus,
    ekran: StageStatus,
): Float {
    val complete = setOf(StageStatus.DONE, StageStatus.ACCEPTED, StageStatus.APPROVED)
    return listOf(karkas, podokonnik, ekran).count { it in complete } / 3f
}

// ──────────────────────────────────────────────────────────────────────
// ТИП ПЛАНИРОВКИ ШКОЛЫ (building_type)
// ──────────────────────────────────────────────────────────────────────

enum class BuildingType(val dbValue: String, val label: String, val emoji: String) {
    KARE("kare", "КАРЕ", "◻"),                 // ◻
    SAMOLETIK("samoletik", "Самолётик", "✈"),  // ✈
    VERTOLETIK("vertoletik", "Вертолётик", "🚁"), // 🚁
    CHEREPASHKA("cherepashka", "Черепашка", "🐢"), // 🐢
    CUSTOM("custom", "Индивидуальный", "🏫"); // 🏫

    companion object {
        fun fromDb(value: String?): BuildingType = entries.firstOrNull { it.dbValue == value } ?: CUSTOM
    }
}

// ──────────────────────────────────────────────────────────────────────
// 7-ПУНКТОВЫЙ ЧЕКЛИСТ ГОТОВНОСТИ ПОМЕЩЕНИЯ
// ──────────────────────────────────────────────────────────────────────

/** Пункт готовности помещения. key совпадает с колонкой ready_* в site_object_cabinets. */
data class ReadinessItem(val key: String, val label: String)

/** Канонический порядок 7 пунктов (как в PDF «Готовность помещения»). */
val ReadinessChecklistItems: List<ReadinessItem> = listOf(
    ReadinessItem("ready_windows", "Окна"),
    ReadinessItem("ready_otkosy", "Откосы"),
    ReadinessItem("ready_radiators", "Радиаторы"),
    ReadinessItem("ready_pipes", "Трубы"),
    ReadinessItem("ready_plinth", "Плинтус"),
    ReadinessItem("ready_walls", "Стены"),
    ReadinessItem("ready_floor", "Пол"),
)

const val READINESS_TOTAL = 7

// ──────────────────────────────────────────────────────────────────────
// СТАТУС КАБИНЕТА (упрощённая модель 2.1.0 — без детализации по окнам)
// ──────────────────────────────────────────────────────────────────────

/**
 * Статус кабинета целиком (зашли → делают полностью). dbValue = site_object_cabinets.status.
 * не начат (без цвета) → начат (🟡) → закончен (🟢); проблема (🔴) — не готов к монтажу и т.п.
 */
enum class CabinetStatus(val dbValue: String, val label: String) {
    NOT_STARTED("not_started", "Не начат"),
    IN_PROGRESS("in_progress", "Начат"),
    DONE("done", "Закончен"),
    PROBLEM("problem", "Проблема");

    companion object {
        fun fromDb(value: String?): CabinetStatus = entries.firstOrNull { it.dbValue == value } ?: NOT_STARTED
    }
}

/** Цвет статуса кабинета (not_started → null = «без цвета», красим только активные). */
@Composable
fun cabinetStatusColor(status: CabinetStatus): Color? {
    val belsi = MaterialTheme.belsiColors
    return when (status) {
        CabinetStatus.NOT_STARTED -> null
        CabinetStatus.IN_PROGRESS -> belsi.warning
        CabinetStatus.DONE -> belsi.success
        CabinetStatus.PROBLEM -> MaterialTheme.colorScheme.error
    }
}
