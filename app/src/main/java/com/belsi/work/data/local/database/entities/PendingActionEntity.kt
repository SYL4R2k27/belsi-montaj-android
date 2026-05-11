package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FIX(2026-05-05): Offline-first очередь — действие ждёт момента когда появится сеть.
 *
 * При шатдауне мобильного интернета в Москве (2-12 часов) пользователь продолжает
 * работать: смена идёт, фото добавляются, задачи закрываются. Всё это пишется
 * в эту таблицу с status=pending. WorkManager в фоне делает retry с exponential
 * backoff. Когда сеть появилась — отправляет на сервер и удаляет запись.
 *
 * Backward compat 1.2.5: существующая логика Repository НЕ меняется. Просто добавляется
 * fallback: если network call fail → save в pending_actions. Существующие юзеры
 * НИЧЕГО не заметят кроме того что приложение перестало показывать ошибки сети.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Тип действия — соответствует sealed class PendingAction */
    val actionType: String,

    /** JSON-сериализованный payload (параметры запроса) */
    val payloadJson: String,

    /** Когда юзер создал действие (локальное время устройства) */
    val createdAt: Long = System.currentTimeMillis(),

    /** Сколько раз пробовали отправить */
    val retries: Int = 0,

    /** Последняя ошибка (для дебага и отображения юзеру) */
    val lastError: String? = null,

    /** Когда последний раз пробовали (для exponential backoff) */
    val lastAttemptAt: Long? = null,

    /**
     * pending — ждёт сеть
     * sending — прямо сейчас отправляется
     * failed — превышено max_retries (>10), не пытаемся, но не удаляем — юзер может вручную запустить
     */
    val status: String = "pending",
)
