package com.belsi.work.data.local.dao

import androidx.room.*
import com.belsi.work.data.local.entities.ShiftEntity
import com.belsi.work.data.local.entities.ShiftSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    
    @Query("SELECT * FROM shifts WHERE id = :shiftId")
    suspend fun getShiftById(shiftId: String): ShiftEntity?
    
    @Query("SELECT * FROM shifts WHERE status = 'IN_PROGRESS' OR status = 'ON_PAUSE' LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?
    
    @Query("SELECT * FROM shifts WHERE status = 'IN_PROGRESS' OR status = 'ON_PAUSE' LIMIT 1")
    fun observeActiveShift(): Flow<ShiftEntity?>
    
    @Query("SELECT * FROM shifts ORDER BY createdAt DESC")
    fun observeAllShifts(): Flow<List<ShiftEntity>>
    
    @Query("SELECT * FROM shifts WHERE status = 'COMPLETED' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getCompletedShifts(limit: Int = 20): List<ShiftEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)
    
    @Update
    suspend fun updateShift(shift: ShiftEntity)
    
    @Delete
    suspend fun deleteShift(shift: ShiftEntity)
    
    @Query("DELETE FROM shifts WHERE id = :shiftId")
    suspend fun deleteShiftById(shiftId: String)
    
    // Shift Slots
    @Query("SELECT * FROM shift_slots WHERE shiftId = :shiftId ORDER BY `index` ASC")
    suspend fun getSlotsByShiftId(shiftId: String): List<ShiftSlotEntity>
    
    @Query("SELECT * FROM shift_slots WHERE shiftId = :shiftId ORDER BY `index` ASC")
    fun observeSlotsByShiftId(shiftId: String): Flow<List<ShiftSlotEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: ShiftSlotEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<ShiftSlotEntity>)
    
    @Update
    suspend fun updateSlot(slot: ShiftSlotEntity)
    
    @Query("DELETE FROM shift_slots WHERE shiftId = :shiftId")
    suspend fun deleteSlotsByShiftId(shiftId: String)
}
