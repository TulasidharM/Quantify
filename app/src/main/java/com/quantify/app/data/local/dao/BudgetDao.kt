package com.quantify.app.data.local.dao

import androidx.room.*
import com.quantify.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month")
    fun getByMonth(year: Int, month: Int): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month AND categoryId = :categoryId")
    suspend fun getByCategory(year: Int, month: Int, categoryId: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE year = :year AND month = :month")
    suspend fun deleteByMonth(year: Int, month: Int)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
