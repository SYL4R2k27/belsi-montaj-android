package com.belsi.work.data.local.database.dao

import androidx.room.*
import com.belsi.work.data.local.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE assignedTo = :userId AND status != 'done' AND status != 'cancelled' ORDER BY createdAt DESC")
    fun getMyTasksFlow(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE assignedTo = :userId ORDER BY createdAt DESC")
    suspend fun getMyTasks(userId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE createdBy = :userId ORDER BY createdAt DESC")
    suspend fun getCreatedTasks(userId: String): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE cachedAt < :timestampMillis")
    suspend fun deleteOldCache(timestampMillis: Long)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}
