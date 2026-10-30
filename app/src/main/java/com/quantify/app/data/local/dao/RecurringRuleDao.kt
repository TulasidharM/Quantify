package com.quantify.app.data.local.dao

import androidx.room.*
import com.quantify.app.data.local.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY createdAtEpochSeconds DESC")
    fun getAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1")
    fun getActive(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 AND nextRunDateEpochSeconds <= :now")
    suspend fun getDue(now: Long = Clock.System.now().epochSeconds): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}
