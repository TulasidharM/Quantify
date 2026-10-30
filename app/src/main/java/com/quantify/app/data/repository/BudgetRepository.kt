package com.quantify.app.data.repository

import com.quantify.app.data.local.dao.BudgetDao
import com.quantify.app.data.local.dao.TransactionDao
import com.quantify.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) {
    fun getByMonth(year: Int, month: Int): Flow<List<BudgetEntity>> =
        budgetDao.getByMonth(year, month)

    suspend fun getByCategory(year: Int, month: Int, categoryId: String) =
        budgetDao.getByCategory(year, month, categoryId)

    suspend fun insert(budget: BudgetEntity) = budgetDao.insert(budget)
    suspend fun delete(budget: BudgetEntity) = budgetDao.delete(budget)
    suspend fun deleteAll() = budgetDao.deleteAll()

    data class BudgetWithSpending(
        val budget: BudgetEntity,
        val spent: Double
    )

    fun getBudgetsWithSpending(year: Int, month: Int): Flow<List<BudgetWithSpending>> {
        val budgets = budgetDao.getByMonth(year, month)
        val range = getMonthRange(year, month)
        val transactions = transactionDao.getByDateRange(range.first.epochSeconds, range.second.epochSeconds)
        return combine(budgets, transactions) { bList, tList ->
            bList.map { budget ->
                val spent = tList
                    .filter { it.type == "EXPENSE" && it.categoryId == budget.categoryId }
                    .sumOf { it.amount }
                BudgetWithSpending(budget, spent)
            }
        }
    }

    private fun getMonthRange(year: Int, month: Int): Pair<kotlinx.datetime.Instant, kotlinx.datetime.Instant> {
        val start = LocalDateTime(year, month, 1, 0, 0)
            .toInstant(TimeZone.currentSystemDefault())
        val end = LocalDateTime(year, month + 1.coerceAtMost(12), 1, 0, 0)
            .let { if (month == 12) LocalDateTime(year + 1, 1, 1, 0, 0) else it }
            .toInstant(TimeZone.currentSystemDefault())
        return Pair(start, end)
    }
}
