package com.quantify.app.data.local.dao

import androidx.room.*
import com.quantify.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateEpochSeconds DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY dateEpochSeconds DESC LIMIT 10")
    fun getRecent(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE dateEpochSeconds >= :start AND dateEpochSeconds < :end ORDER BY dateEpochSeconds DESC")
    fun getByDateRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND dateEpochSeconds >= :start AND dateEpochSeconds < :end")
    suspend fun getTotalIncome(start: Long, end: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND dateEpochSeconds >= :start AND dateEpochSeconds < :end")
    suspend fun getTotalExpense(start: Long, end: Long): Double?

    @Query("SELECT * FROM transactions WHERE type = :type AND dateEpochSeconds >= :start AND dateEpochSeconds < :end")
    suspend fun getByTypeAndRange(type: String, start: Long, end: Long): List<TransactionEntity>

    // — Reactive month-scoped flows (replaces in-memory filtering) —

    @Query("SELECT * FROM transactions WHERE type = 'EXPENSE' AND dateEpochSeconds >= :start AND dateEpochSeconds < :end")
    fun getExpensesForRangeFlow(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT strftime('%m', dateEpochSeconds, 'unixepoch') as monthNum, " +
        "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) as income, " +
        "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) as expense " +
        "FROM transactions " +
        "WHERE strftime('%Y', dateEpochSeconds, 'unixepoch') = :yearStr " +
        "GROUP BY monthNum " +
        "ORDER BY monthNum")
    fun getYearlyTrendFlow(yearStr: String): Flow<List<MonthlyTrendRow>>

    @Query("SELECT " +
        "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) as income, " +
        "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) as expense " +
        "FROM transactions " +
        "WHERE dateEpochSeconds >= :start AND dateEpochSeconds < :end")
    fun getMonthlyTotalsFlow(start: Long, end: Long): Flow<List<MonthlyTotalsRow>>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId")
    suspend fun getByCategoryId(categoryId: String): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun updateCategoryId(oldCategoryId: String, newCategoryId: String)
}

data class MonthlyTrendRow(val monthNum: String, val income: Double, val expense: Double)

data class MonthlyTotalsRow(val income: Double, val expense: Double)
