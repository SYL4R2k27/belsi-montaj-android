package com.belsi.work.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.belsi.work.data.local.dao.PhotoDao
import com.belsi.work.data.local.dao.ShiftDao
import com.belsi.work.data.local.dao.TicketDao
import com.belsi.work.data.local.entities.*

@Database(
    entities = [
        ShiftEntity::class,
        ShiftSlotEntity::class,
        PhotoEntity::class,
        TicketEntity::class,
        TicketMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun photoDao(): PhotoDao
    abstract fun ticketDao(): TicketDao
    
    companion object {
        const val DATABASE_NAME = "belsi_work_db"
    }
}
