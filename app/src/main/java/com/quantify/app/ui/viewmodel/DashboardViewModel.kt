package com.quantify.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantify.app.data.local.dao.MonthlyTrendRow
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.data.repository.CategoryRepository
import com.quantify.app.data.repository.BudgetRepository
import com.quantify.app.data.repository.TransactionRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        private val NOW = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    val selectedYearFlow = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("selectedYear")] ?: NOW.year.toString()
    }.map { it.toInt() }
        .stateIn(viewModelScope, SharingStarted.Lazily, NOW.year)

    val selectedMonthFlow = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("selectedMonth")] ?: NOW.monthNumber.toString()
    }.map { it.toInt() }
        .stateIn(viewModelScope, SharingStarted.Lazily, NOW.monthNumber)

    private val allCategories: StateFlow<List<CategoryEntity>> =
        categoryRepository.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransactions = transactionRepository.getRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<TransactionEntity>())
    val categories = allCategories

    val selectedYear: Int get() = selectedYearFlow.value
    val selectedMonth: Int get() = selectedMonthFlow.value

    fun selectMonth(year: Int, month: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("selectedYear")] = year.toString()
                prefs[stringPreferencesKey("selectedMonth")] = month.toString()
            }
        }
    }

    // — Monthly totals as reactive flow (SQLite aggregation, no suspend call) —
    private val monthlyTotals = combine(
        selectedYearFlow, selectedMonthFlow
    ) { year, month ->
        transactionRepository.getMonthRangeEpoch(year, month)
    }.flatMapLatest { (start, end) ->
        transactionRepository.getMonthlyTotalsFlow(start, end).map { rows ->
            if (rows.isNotEmpty()) rows[0] else com.quantify.app.data.local.dao.MonthlyTotalsRow(0.0, 0.0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.quantify.app.data.local.dao.MonthlyTotalsRow(0.0, 0.0))

    val monthlyIncome: StateFlow<Double> = monthlyTotals.map { it.income }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val monthlyExpense: StateFlow<Double> = monthlyTotals.map { it.expense }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyBudgetedTotal: StateFlow<Double> = combine(
        selectedYearFlow, selectedMonthFlow, allCategories
    ) { year, month, categories -> Triple(year, month, categories) }
        .flatMapLatest { (year, month, categories) ->
            budgetRepository.getByMonth(year, month).map { budgets ->
                val budgetByCategory = budgets.associateBy { it.categoryId }
                budgets.sumOf { budget ->
                    val parentId = categories.firstOrNull { it.id == budget.categoryId }?.parentId
                    if (parentId == null || parentId !in budgetByCategory) budget.amount else 0.0
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    data class CategorySpending(val category: CategoryEntity, val amount: Double)

    data class ParentCategorySpending(val parentCategory: CategoryEntity, val amount: Double, val subCategories: List<CategoryEntity>)

    // — Single combined flow for all category-level spending (avoids duplicate upstream collections) —
    private data class MonthSummary(
        val categorySpending: List<CategorySpending>,
        val parentCategorySpending: List<ParentCategorySpending>,
        val spendingById: Map<String, Double>
    )

    private val monthExpenses: StateFlow<List<TransactionEntity>> = combine(
        selectedYearFlow, selectedMonthFlow
    ) { year, month ->
        transactionRepository.getMonthRangeEpoch(year, month)
    }.flatMapLatest { (start, end) ->
        transactionRepository.getExpensesForRangeFlow(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val monthSummary = combine(
        monthExpenses,
        allCategories
    ) { monthTxns, cats ->
        val spendingById = monthTxns.groupBy { it.categoryId }
            .mapValues { it.value.sumOf { t -> t.amount } }

        val allExpenseCats = cats.filter { it.type == "EXPENSE" }

        val catSpending = allExpenseCats
            .map { cat -> CategorySpending(cat, spendingById[cat.id] ?: 0.0) }
            .filter { it.amount > 0 }
            .sortedByDescending { it.amount }

        val parents = allExpenseCats.filter { it.parentId == null }
        val parentSpending = parents.map { parent ->
            val subs = allExpenseCats.filter { it.parentId == parent.id }
            val total = (spendingById[parent.id] ?: 0.0) + subs.sumOf { spendingById[it.id] ?: 0.0 }
            ParentCategorySpending(parent, total, subs)
        }.filter { it.amount > 0 }
            .sortedByDescending { it.amount }

        MonthSummary(catSpending, parentSpending, spendingById)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthSummary(emptyList(), emptyList(), emptyMap()))

    val categorySpending: StateFlow<List<CategorySpending>> = monthSummary.map { it.categorySpending }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parentCategorySpending: StateFlow<List<ParentCategorySpending>> = monthSummary.map { it.parentCategorySpending }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun subcategorySpendingFor(parentId: String) = combine(
        monthSummary,
        allCategories
    ) { summary, cats ->
        val spendingById = summary.spendingById
        val parent = cats.find { it.id == parentId } ?: return@combine emptyList<CategorySpending>()
        val subs = cats.filter { it.parentId == parentId }

        val parentDirect = spendingById[parent.id] ?: 0.0
        val results = mutableListOf<CategorySpending>()
        if (parentDirect > 0) {
            results.add(CategorySpending(parent, parentDirect))
        }
        subs.forEach { sub ->
            val amount = spendingById[sub.id] ?: 0.0
            if (amount > 0) results.add(CategorySpending(sub, amount))
        }
        results.sortedByDescending { it.amount }
    }

    data class MonthlyData(val month: Int, val income: Double, val expense: Double)

    // — Yearly trend aggregated in SQLite, converted to MonthlyData —
    val yearlyTrend = selectedYearFlow
        .flatMapLatest { year ->
            transactionRepository.getYearlyTrendFlow(year).map { rows ->
                rows.map { row ->
                    MonthlyData(row.monthNum.toIntOrNull() ?: 0, row.income, row.expense)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<MonthlyData>())
}
