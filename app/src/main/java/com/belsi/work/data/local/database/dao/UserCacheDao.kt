package com.belsi.work.data.local.database.dao

import androidx.room.*
import com.belsi.work.data.local.database.entities.UserCacheEntity

@Dao
interface UserCacheDao {

    @Query("SELECT * FROM user_cache WHERE id = :id")
    suspend fun getUserById(id: String): UserCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserCacheEntity)

    @Query("DELETE FROM user_cache WHERE id = :id")
    suspend fun deleteUser(id: String)

    @Query("DELETE FROM user_cache")
    suspend fun clearAll()
}
