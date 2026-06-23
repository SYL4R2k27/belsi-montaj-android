package com.belsi.work.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.belsi.work.data.local.database.dao.*
import com.belsi.work.data.local.database.entities.*

/**
 * Локальная база данных приложения для offline режима
 *
 * Хранит критичные данные:
 * - Смены (активная смена должна быть доступна offline)
 * - Фото (для отложенной отправки)
 * - Сообщения чата (последние 100)
 * - Кэш пользователя
 */
@Database(
    entities = [
        ShiftEntity::class,
        PhotoEntity::class,
        ChatMessageEntity::class,
        UserCacheEntity::class,
        ToolEntity::class,
        TaskEntity::class,
        MessengerThreadEntity::class,
        MessengerMessageEntity::class,
        // FIX(2026-05-05): offline-first очередь для шатдаунов мобильного интернета.
        // При обновлении схемы — fallbackToDestructiveMigration в DI стирает старую БД.
        PendingActionEntity::class,
    ],
    // v7: PhotoEntity + windowId/stage/isFinal (v3 привязка фото к окну/этапу).
    // DI использует fallbackToDestructiveMigration — локальный кэш пересоздаётся.
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shiftDao(): ShiftDao
    abstract fun photoDao(): PhotoDao
    abstract fun chatDao(): ChatDao
    abstract fun userDao(): UserCacheDao
    abstract fun toolDao(): ToolDao
    abstract fun taskDao(): TaskDao
    abstract fun messengerThreadDao(): MessengerThreadDao
    abstract fun messengerMessageDao(): MessengerMessageDao
    // FIX(2026-05-05): offline-first очередь
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        const val DATABASE_NAME = "belsi_work_db"
    }
}
