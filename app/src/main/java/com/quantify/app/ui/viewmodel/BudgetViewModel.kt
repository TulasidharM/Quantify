package com.quantify.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantify.app.data.local.entity.BudgetEntity
import com.quantify.app.data.repository.BudgetRepository
import com.quantify.app.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
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

    fun upsertBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            budgetRepository.insert(budget)
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            budgetRepository.delete(budget)
        }
    }
}
