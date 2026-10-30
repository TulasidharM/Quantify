package com.quantify.app.data.repository

import com.quantify.app.data.local.dao.TransactionDao
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.data.local.dao.MonthlyTrendRow
import com.quantify.app.data.local.dao.MonthlyTotalsRow
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.days
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun getAll(): Flow<List<TransactionEntity>> = transactionDao.getAll()
    fun getRecent(): Flow<List<TransactionEntity>> = transactionDao.getRecent()
    suspend fun getById(id: String) = transactionDao.getById(id)
    suspend fun insert(transaction: TransactionEntity) = transactionDao.insert(transaction)
    suspend fun update(transaction: TransactionEntity) = transactionDao.update(transaction)
    suspend fun delete(transaction: TransactionEntity) = transactionDao.delete(transaction)
    suspend fun getByCategoryId(categoryId: String) = transactionDao.getByCategoryId(categoryId)
    suspend fun updateCategoryId(oldCategoryId: String, newCategoryId: String) =
        transactionDao.updateCategoryId(oldCategoryId, newCategoryId)

    fun getByDateRange(start: Long, end: Long): Flow<List<TransactionEntity>> =
        transactionDao.getByDateRange(start, end)

    // Reactive flow: only expense rows in range, filtered by SQLite
    fun getExpensesForRangeFlow(start: Long, end: Long): Flow<List<TransactionEntity>> =
        transactionDao.getExpensesForRangeFlow(start, end)

    // Reactive flow: yearly trend aggregated in SQLite
    fun getYearlyTrendFlow(year: Int): Flow<List<MonthlyTrendRow>> =
        transactionDao.getYearlyTrendFlow(year.toString())

    // Reactive flow: monthly totals aggregated in SQLite
    fun getMonthlyTotalsFlow(start: Long, end: Long): Flow<List<MonthlyTotalsRow>> =
        transactionDao.getMonthlyTotalsFlow(start, end)

    suspend fun getMonthlyTotals(year: Int, month: Int): Pair<Double, Double> {
        val range = getMonthRange(year, month)
        val income = transactionDao.getTotalIncome(range.first, range.second) ?: 0.0
        val expense = transactionDao.getTotalExpense(range.first, range.second) ?: 0.0
        return Pair(income, expense)
    }

    suspend fun getTransactionsForMonth(year: Int, month: Int): List<TransactionEntity> {
        val range = getMonthRange(year, month)
        return transactionDao.getByTypeAndRange("EXPENSE", range.first, range.second) +
                transactionDao.getByTypeAndRange("INCOME", range.first, range.second)
    }

    suspend fun deleteAll() = transactionDao.deleteAll()

    fun getMonthRangeEpoch(year: Int, month: Int): Pair<Long, Long> {
        val tz = TimeZone.currentSystemDefault()
        val start = LocalDateTime(year, month, 1, 0, 0).toInstant(tz).epochSeconds
        val endMonth = if (month == 12) 1 else month + 1
        val endYear = if (month == 12) year + 1 else year
        val end = LocalDateTime(endYear, endMonth, 1, 0, 0).toInstant(tz).epochSeconds
        return Pair(start, end)
    }

    private fun getMonthRange(year: Int, month: Int): Pair<Long, Long> = getMonthRangeEpoch(year, month)
}
