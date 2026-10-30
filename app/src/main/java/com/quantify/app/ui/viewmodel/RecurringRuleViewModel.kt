package com.quantify.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantify.app.data.local.entity.RecurringRuleEntity
import com.quantify.app.data.repository.RecurringRuleRepository
import com.quantify.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecurringRuleViewModel @Inject constructor(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val rules = recurringRuleRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addRule(rule: RecurringRuleEntity) {
        viewModelScope.launch {
            recurringRuleRepository.insert(rule)
        }
    }

    fun updateRule(rule: RecurringRuleEntity) {
        viewModelScope.launch {
            recurringRuleRepository.update(rule)
        }
    }

    fun deleteRule(rule: RecurringRuleEntity) {
        viewModelScope.launch {
            recurringRuleRepository.delete(rule)
        }
    }

    fun processDueRules() {
        viewModelScope.launch {
            recurringRuleRepository.processDueRules()
        }
    }
}
