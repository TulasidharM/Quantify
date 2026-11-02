package com.quantify.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.PaymentMethodEntity
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.data.repository.CategoryRepository
import com.quantify.app.data.repository.PaymentMethodRepository
import com.quantify.app.data.repository.TransactionRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        private val NOW = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    // Category filter from dashboard navigation
    private val _filterCategoryId = MutableStateFlow<String?>(null)
    val filterCategoryIdFlow = _filterCategoryId.asStateFlow()
    val filterCategoryId: String? get() = _filterCategoryId.value
    fun setFilterCategoryId(id: String) { _filterCategoryId.value = id }
    fun clearFilterCategory() { _filterCategoryId.value = null }

    val selectedYearFlow = dataStore.data.map { prefs ->
        (prefs[stringPreferencesKey("selectedYear")] ?: NOW.year.toString()).toInt()
    }.stateIn(viewModelScope, SharingStarted.Lazily, NOW.year)

    val selectedMonthFlow = dataStore.data.map { prefs ->
        (prefs[stringPreferencesKey("selectedMonth")] ?: NOW.monthNumber.toString()).toInt()
    }.stateIn(viewModelScope, SharingStarted.Lazily, NOW.monthNumber)

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

    // — Data flows —
    // All transactions (used by screens that need the full list, e.g. phonePe import)
    val allTransactions = transactionRepository.getAll().stateIn(
        viewModelScope, SharingStarted.Lazily, emptyList<TransactionEntity>()
    )

    // Month-filtered transactions: range computed from year/month, filtered by SQLite
    val transactionsByMonth = combine(
        selectedYearFlow, selectedMonthFlow
    ) { year, month ->
        transactionRepository.getMonthRangeEpoch(year, month)
    }.flatMapLatest { (start, end) ->
        transactionRepository.getByDateRange(start, end)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList<TransactionEntity>())

    val recentTransactions = transactionRepository.getRecent().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val categories = categoryRepository.getAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val paymentMethods = paymentMethodRepository.getAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Default payment method — loaded once, updated reactively via paymentMethods flow
    val defaultPaymentMethod = paymentMethods.map { list ->
        list.firstOrNull { it.isDefault }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _loadedTransaction = kotlinx.coroutines.flow.MutableStateFlow<TransactionEntity?>(null)
    val loadedTransaction: kotlinx.coroutines.flow.StateFlow<TransactionEntity?> = _loadedTransaction

    fun loadTransaction(id: String) {
        viewModelScope.launch {
            _loadedTransaction.value = transactionRepository.getById(id)
        }
    }

    fun clearLoadedTransaction() {
        _loadedTransaction.value = null
    }

    fun addTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.insert(transaction)
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.update(transaction)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.delete(transaction)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            transactionRepository.deleteAll()
        }
    }

    // Category CRUD
    fun addCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryRepository.insert(category)
        }
    }

    private val _deleteCategoryMessage = MutableStateFlow<String?>(null)
    val deleteCategoryMessage: StateFlow<String?> = _deleteCategoryMessage.asStateFlow()

    fun clearDeleteCategoryMessage() {
        _deleteCategoryMessage.value = null
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            // Skip deletion of system (isDefault) categories
            if (category.isDefault) {
                _deleteCategoryMessage.value = "Cannot delete default category"
                return@launch
            }

            // Find a fallback: prefer "Other", then any other category of the same type
            val allCategories = categories.value
            val otherFallback = allCategories.find { it.type == category.type && it.isDefault && it.parentId == null }
            val anyFallback = allCategories.find { it.type == category.type && it.parentId == null && it.id != category.id }
            val fallbackCategory = otherFallback ?: anyFallback

            if (fallbackCategory == null) {
                // Last category of this type — create "Other" on the fly
                val newFallback = CategoryEntity(
                    id = if (category.type == "EXPENSE") "other" else "inc_other",
                    name = if (category.type == "EXPENSE") "Other" else "Other Income",
                    type = category.type,
                    color = 0xFF607D8BL,
                    icon = "more_horiz",
                    isDefault = true
                )
                categoryRepository.insert(newFallback)

                // Collect all category IDs and reassign transactions
                val subCategories = categoryRepository.getByParentId(category.id)
                val categoryIds = listOf(category.id) + subCategories.map { it.id }
                for (cid in categoryIds) {
                    transactionRepository.updateCategoryId(cid, newFallback.id)
                }
                categoryRepository.delete(category)
                for (sub in subCategories) {
                    categoryRepository.delete(sub)
                }
                _deleteCategoryMessage.value = "\"${category.name}\" deleted"
                return@launch
            }

            // Collect all category IDs: the category itself + its subcategories
            val subCategories = categoryRepository.getByParentId(category.id)
            val categoryIds = listOf(category.id) + subCategories.map { it.id }

            // Reassign all transactions to the fallback category
            for (cid in categoryIds) {
                transactionRepository.updateCategoryId(cid, fallbackCategory.id)
            }

            // Delete the category and its subcategories
            categoryRepository.delete(category)
            for (sub in subCategories) {
                categoryRepository.delete(sub)
            }

            _deleteCategoryMessage.value = "\"${category.name}\" deleted"
        }
    }

    // Payment Method CRUD
    fun addPaymentMethod(method: PaymentMethodEntity) {
        viewModelScope.launch {
            paymentMethodRepository.insert(method)
        }
    }

    fun deletePaymentMethod(method: PaymentMethodEntity) {
        viewModelScope.launch {
            paymentMethodRepository.delete(method)
        }
    }

    fun setDefaultPaymentMethod(id: String) {
        viewModelScope.launch {
            paymentMethodRepository.setDefault(id)
        }
    }
}
