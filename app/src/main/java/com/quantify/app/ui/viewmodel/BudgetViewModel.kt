package com.quantify.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantify.app.data.local.entity.BudgetEntity
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.repository.BudgetRepository
import com.quantify.app.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    private val currentMonth = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber

    val budgets = budgetRepository.getBudgetsWithSpending(currentYear, currentMonth)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categories = categoryRepository.getByType("EXPENSE")
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val topLevelCategories = categories
        .map { list -> list.filter { it.parentId == null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<CategoryEntity>())

    /**
     * Returns the budget amount for a category (if any exists for current month),
     * and the sum of all subcategory budget amounts under that parent.
     */
    fun getParentBudgetInfo(parentId: String): Pair<Double, Double> {
        val budgetList = budgets.value
        val parentBudget = budgetList.find { it.budget.categoryId == parentId }?.budget?.amount ?: 0.0
        val parentSubs = categories.value.filter { it.parentId == parentId }.map { it.id }.toSet()
        var subBudgetTotal = 0.0
        for (bs in budgetList) {
            if (parentSubs.contains(bs.budget.categoryId)) {
                subBudgetTotal += bs.budget.amount
            }
        }
        return Pair(parentBudget, subBudgetTotal)
    }

    fun upsertBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            budgetRepository.insert(budget)
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            budgetRepository.deleteWithChildren(budget)
        }
    }
}
