package com.belsi.work.data.local.database.dao

import androidx.room.*
import com.belsi.work.data.local.database.entities.ToolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {

    @Query("SELECT * FROM tools WHERE status = 'available' ORDER BY name ASC")
    fun getAvailableToolsFlow(): Flow<List<ToolEntity>>

    @Query("SELECT * FROM tools WHERE status = 'available' ORDER BY name ASC")
    suspend fun getAvailableTools(): List<ToolEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<ToolEntity>)

    @Query("DELETE FROM tools")
    suspend fun clearAll()

    @Query("DELETE FROM tools WHERE cachedAt < :timestampMillis")
    suspend fun deleteOldCache(timestampMillis: Long)
}
