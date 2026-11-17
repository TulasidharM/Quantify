package com.quantify.app.data.repository

import com.quantify.app.data.local.dao.BudgetDao
import com.quantify.app.data.local.dao.TransactionDao
import com.quantify.app.data.local.entity.BudgetEntity
import com.quantify.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryRepository: CategoryRepository
) {
    fun getAll(): Flow<List<BudgetEntity>> = budgetDao.getAll()

    suspend fun getByCategory(categoryId: String) =
        budgetDao.getByCategory(categoryId)

    suspend fun insert(budget: BudgetEntity) = budgetDao.insert(budget)
    suspend fun delete(budget: BudgetEntity) = budgetDao.delete(budget)

    /** Delete this budget and all budgets for descendant subcategories. */
    suspend fun deleteWithChildren(budget: BudgetEntity) {
        val allCategories = categoryRepository.getAll().first()
        val childrenOf = allCategories.filter { it.parentId != null }
            .groupBy({ it.parentId!! }, { it.id })
            .mapValues { it.value.toSet() }
        val descendants = collectAllDescendants(budget.categoryId, childrenOf)
        budgetDao.delete(budget)
        for (subId in descendants) {
            val subBudget = budgetDao.getByCategory(subId)
            if (subBudget != null) budgetDao.delete(subBudget)
        }
    }

    suspend fun deleteAll() = budgetDao.deleteAll()

    data class BudgetWithSpending(
        val budget: BudgetEntity,
        val spent: Double
    )

    fun getBudgetsWithSpending(year: Int, month: Int): Flow<List<BudgetWithSpending>> {
        val budgets = budgetDao.getAll()
        val range = getMonthRange(year, month)
        val transactions = transactionDao.getByDateRange(range.first.epochSeconds, range.second.epochSeconds)
        val categories = categoryRepository.getAll()
        return combine(budgets, transactions, categories) { bList, tList, catList ->
            // Build map: parentId -> set of child IDs
            val childrenOf = catList.filter { it.parentId != null }
                .groupBy({ it.parentId!! }, { it.id })
                .mapValues { it.value.toSet() }

            val spentByCategory = tList.asSequence()
                .filter { it.type == "EXPENSE" }
                .groupingBy { it.categoryId }
                .fold(0.0) { total, transaction -> total + transaction.amount }

            bList.map { budget ->
                // Collect all subcategory IDs under this budget's category
                val subIds = collectAllDescendants(budget.categoryId, childrenOf)
                val spent = (subIds + budget.categoryId).sumOf { spentByCategory[it] ?: 0.0 }
                BudgetWithSpending(budget, spent)
            }
        }
    }

    // Recursively collect all descendant subcategory IDs
    private fun collectAllDescendants(parentId: String, childrenMap: Map<String, Set<String>>): Set<String> {
        val result = mutableSetOf<String>()
        val directChildren = childrenMap[parentId] ?: return result
        for (childId in directChildren) {
            result.add(childId)
            result.addAll(collectAllDescendants(childId, childrenMap))
        }
        return result
    }

    private fun getMonthRange(year: Int, month: Int): Pair<kotlinx.datetime.Instant, kotlinx.datetime.Instant> {
        val start = LocalDateTime(year, month, 1, 0, 0)
            .toInstant(TimeZone.currentSystemDefault())
        val endDate = if (month == 12) {
            LocalDateTime(year + 1, 1, 1, 0, 0)
        } else {
            LocalDateTime(year, month + 1, 1, 0, 0)
        }
        val end = endDate.toInstant(TimeZone.currentSystemDefault())
        return Pair(start, end)
    }
}
