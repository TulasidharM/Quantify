package com.quantify.app.ui.screens.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.quantify.app.data.local.entity.BudgetEntity
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.PaymentMethodEntity
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.data.local.entity.WishlistItemEntity
import com.quantify.app.data.repository.BudgetRepository
import com.quantify.app.data.repository.CategoryRepository
import com.quantify.app.data.repository.PaymentMethodRepository
import com.quantify.app.data.repository.TransactionRepository
import com.quantify.app.data.repository.WishlistItemRepository
import com.quantify.app.ui.screens.csvimport.CsvImportPreferences
import com.quantify.app.ui.screens.csvimport.findPossibleDuplicates
import com.quantify.app.ui.screens.csvimport.parsePhonePeCsv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class LocalWebState(val url: String? = null, val error: String? = null)

@Serializable
data class LocalWebPayload(
    val year: Int,
    val month: Int,
    val income: Double,
    val expenses: Double,
    val investmentSpending: Double,
    val budgetedTotal: Double,
    val budgetedSpending: Double,
    val investmentBudgetedSpending: Double,
    val unbudgetedSpending: Double,
    val transactions: List<LocalWebTransaction>,
    val categories: List<CategoryEntity>,
    val paymentMethods: List<PaymentMethodEntity>,
    val budgets: List<LocalWebBudget>,
    val wishlist: List<WishlistItemEntity>,
    val lastPhonePeImportEpoch: Long? = null
)

@Serializable
data class LocalWebTransaction(val id: String, val date: String, val type: String, val categoryId: String, val category: String, val paymentMethodId: String? = null, val description: String = "", val amount: Double)

@Serializable
data class LocalWebBudget(val id: String, val categoryId: String, val amount: Double, val spent: Double)

@Serializable
data class WebTransactionInput(val id: String? = null, val amount: Double, val type: String, val categoryId: String, val paymentMethodId: String? = null, val description: String? = null, val date: String)

@Serializable
data class WebCategoryInput(val id: String? = null, val name: String, val type: String, val parentId: String? = null)

@Serializable
data class WebBudgetInput(val categoryId: String, val amount: Double)

@Serializable
data class WebWishlistInput(val id: String? = null, val name: String, val targetAmount: Double)

@Serializable
data class WebPaymentMethodInput(val id: String? = null, val name: String, val isDefault: Boolean = false)

@Serializable
data class WebPhonePeCsvInput(val csv: String)

@Serializable
data class WebPhonePeClassification(val index: Int, val categoryId: String? = null, val paymentMethodId: String? = null, val label: String = "", val ignored: Boolean = false, val duplicateReviewed: Boolean = false)

@Serializable
data class WebPhonePeImportInput(val csv: String, val items: List<WebPhonePeClassification>)

@Serializable
data class WebPhonePeDuplicate(val date: String, val type: String, val category: String, val description: String, val amount: Double)

@Serializable
data class WebPhonePePreviewRow(val index: Int, val date: String, val time: String, val vendor: String, val amount: Double, val direction: String, val rawDetail: String, val duplicates: List<WebPhonePeDuplicate>)

@Serializable
data class WebPhonePePreview(val rows: List<WebPhonePePreviewRow>)

@HiltViewModel
class LocalWebViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val wishlistRepository: WishlistItemRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {
    private val _state = MutableStateFlow(LocalWebState())
    val state = _state.asStateFlow()
    private var server: QuantifyLanServer? = null
    private var starting = false

    fun start() {
        if (server != null || starting) return
        starting = true
        viewModelScope.launch {
            try {
                val lanServer = QuantifyLanServer({ query -> loadPayload(query) }, { method, path, body -> handleAction(method, path, body) })
                withContext(Dispatchers.IO) { lanServer.start() }
                server = lanServer
                _state.value = LocalWebState(url = "http://${lanServer.ipAddress}:${lanServer.port}/")
            } catch (error: Exception) {
                _state.value = LocalWebState(error = error.message ?: "Could not start local access")
            } finally {
                starting = false
            }
        }
    }

    fun stop() {
        server?.stop()
        server = null
        starting = false
        _state.value = LocalWebState()
    }

    private suspend fun loadPayload(query: Map<String, String>): String {
        val prefs = dataStore.data.first()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val year = query["year"]?.toIntOrNull() ?: prefs[stringPreferencesKey("selectedYear")]?.toIntOrNull() ?: now.year
        val month = query["month"]?.toIntOrNull()?.takeIf { it in 1..12 }
            ?: prefs[stringPreferencesKey("selectedMonth")]?.toIntOrNull()?.takeIf { it in 1..12 } ?: now.monthNumber
        val txns = transactionRepository.getTransactionsForMonth(year, month)
        val categories = categoryRepository.getAll().first().associateBy { it.id }
        val budgetRows = budgetRepository.getBudgetsWithSpending(year, month).first()
        val budgetRowsByCategory = budgetRows.associateBy { it.budget.categoryId }
        val summaryBudgetRows = budgetRows.filter { row ->
            val parentId = categories[row.budget.categoryId]?.parentId
            parentId == null || parentId !in budgetRowsByCategory
        }
        val budgetedTotal = summaryBudgetRows.sumOf { it.budget.amount }
        val budgetedSpending = summaryBudgetRows.sumOf { it.spent }
        val expensesTotal = txns.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val investmentRoot = categories.values.firstOrNull {
            it.parentId == null && it.type == "EXPENSE" && it.name.equals("Investment", ignoreCase = true)
        }
        val investmentCategoryIds = mutableSetOf<String>()
        investmentRoot?.let { investmentCategoryIds.add(it.id) }
        var addedCategory: Boolean
        do {
            val previousSize = investmentCategoryIds.size
            categories.values.filter { it.parentId in investmentCategoryIds }
                .forEach { investmentCategoryIds.add(it.id) }
            addedCategory = investmentCategoryIds.size > previousSize
        } while (addedCategory)
        val investmentSpending = txns.filter {
            it.type == "EXPENSE" && it.categoryId in investmentCategoryIds
        }.sumOf { it.amount }
        val investmentBudgetedSpending = summaryBudgetRows.filter {
            it.budget.categoryId in investmentCategoryIds
        }.sumOf { it.spent }
        val wishlist = wishlistRepository.getAll().first()
        val paymentMethods = paymentMethodRepository.getAll().first()
        val data = LocalWebPayload(
            year = year,
            month = month,
            income = txns.filter { it.type == "INCOME" }.sumOf { it.amount },
            expenses = expensesTotal,
            investmentSpending = investmentSpending,
            budgetedTotal = budgetedTotal,
            budgetedSpending = budgetedSpending,
            investmentBudgetedSpending = investmentBudgetedSpending,
            unbudgetedSpending = (expensesTotal - budgetedSpending).coerceAtLeast(0.0),
            transactions = txns.sortedByDescending { it.dateEpochSeconds }.map { transaction ->
                LocalWebTransaction(
                    id = transaction.id,
                    date = Instant.ofEpochSecond(transaction.dateEpochSeconds).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                    type = transaction.type,
                    categoryId = transaction.categoryId,
                    category = categories[transaction.categoryId]?.name ?: "Uncategorized",
                    paymentMethodId = transaction.paymentMethodId,
                    description = transaction.description.orEmpty(),
                    amount = transaction.amount
                )
            },
            categories = categories.values.toList(),
            paymentMethods = paymentMethods,
            budgets = budgetRows.map { row -> LocalWebBudget(row.budget.id, row.budget.categoryId, row.budget.amount, row.spent) },
            wishlist = wishlist,
            lastPhonePeImportEpoch = prefs[CsvImportPreferences.lastPhonePeImportEpoch]
        )
        return Json.encodeToString(data)
    }

    private suspend fun handleAction(method: String, path: String, body: String): LanApiResponse {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 2 || parts[0] != "api") return LanApiResponse(404, "{\"error\":\"Not found\"}")
        val group = parts[1]
        val id = parts.getOrNull(2)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        return try {
            when (group) {
                "phonepe-preview" -> {
                    if (method != "POST") return methodError()
                    val input = Json.decodeFromString<WebPhonePeCsvInput>(body)
                    val rows = parsePhonePeCsv(input.csv)
                    require(rows.isNotEmpty()) { "No valid PhonePe transactions found in this CSV." }
                    val categories = categoryRepository.getAll().first().associateBy { it.id }
                    val existing = transactionRepository.getAll().first()
                    val preview = WebPhonePePreview(rows.map { row ->
                        val duplicates = findPossibleDuplicates(row, existing).map { transaction ->
                            val transactionDate = Instant.ofEpochSecond(transaction.dateEpochSeconds)
                                .atZone(ZoneId.systemDefault()).toLocalDate().toString()
                            WebPhonePeDuplicate(
                                transactionDate,
                                transaction.type,
                                categories[transaction.categoryId]?.name ?: "Uncategorized",
                                transaction.description.orEmpty(),
                                transaction.amount
                            )
                        }
                        WebPhonePePreviewRow(
                            row.index, row.date.toString(), row.time.toString(), row.vendor, row.amount,
                            if (row.direction.name == "PAID") "EXPENSE" else "INCOME", row.rawDetail, duplicates
                        )
                    })
                    return LanApiResponse(body = Json.encodeToString<WebPhonePePreview>(preview))
                }
                "phonepe-import" -> {
                    if (method != "POST") return methodError()
                    val input = Json.decodeFromString<WebPhonePeImportInput>(body)
                    val rows = parsePhonePeCsv(input.csv)
                    require(rows.isNotEmpty() && input.items.size == rows.size) {
                        "The import list no longer matches this CSV. Please select the file again."
                    }
                    val itemsByIndex = input.items.associateBy { it.index }
                    require(itemsByIndex.size == rows.size && rows.indices.all(itemsByIndex::containsKey)) { "Invalid import rows." }
                    val categories = categoryRepository.getAll().first()
                    val existing = transactionRepository.getAll().first()
                    rows.forEach { row ->
                        val item = itemsByIndex.getValue(row.index)
                        if (item.ignored) return@forEach
                        val type = if (row.direction.name == "PAID") "EXPENSE" else "INCOME"
                        require(item.categoryId != null && categories.any { it.id == item.categoryId && it.type == type }) {
                            "Choose a valid category for every transaction you want to import."
                        }
                        if (findPossibleDuplicates(row, existing).isNotEmpty()) {
                            require(item.duplicateReviewed) { "Review the possible duplicate transactions before importing." }
                        }
                    }
                    var saved = 0
                    rows.forEach { row ->
                        val item = itemsByIndex.getValue(row.index)
                        if (item.ignored) return@forEach
                        val type = if (row.direction.name == "PAID") "EXPENSE" else "INCOME"
                        transactionRepository.insert(
                            TransactionEntity(
                                amount = row.amount,
                                type = type,
                                categoryId = item.categoryId!!,
                                paymentMethodId = item.paymentMethodId,
                                description = item.label.ifBlank { row.vendor },
                                dateEpochSeconds = row.epochSeconds
                            )
                        )
                        saved++
                    }
                    dataStore.edit { prefs -> prefs[CsvImportPreferences.lastPhonePeImportEpoch] = Clock.System.now().epochSeconds }
                    return LanApiResponse(body = Json.encodeToString(mapOf("saved" to saved)))
                }
                "transactions" -> {
                    if (method == "DELETE" && id != null) {
                        transactionRepository.getById(id)?.let { transactionRepository.delete(it) }
                    } else if (method == "POST" || method == "PUT") {
                        val input = Json.decodeFromString<WebTransactionInput>(body)
                        require(input.amount > 0 && input.type in setOf("INCOME", "EXPENSE"))
                        require(categoryRepository.getAll().first().any { it.id == input.categoryId && it.type == input.type }) { "Choose a category that matches the transaction type" }
                        val epoch = java.time.LocalDate.parse(input.date).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                        val existing = input.id?.let { transactionRepository.getById(it) }
                        val txn = TransactionEntity(id = input.id ?: TransactionEntity.generateId(), amount = input.amount, type = input.type,
                            categoryId = input.categoryId, paymentMethodId = input.paymentMethodId, description = input.description,
                            dateEpochSeconds = epoch, createdAtEpochSeconds = existing?.createdAtEpochSeconds ?: System.currentTimeMillis() / 1000)
                        if (method == "PUT") transactionRepository.update(txn) else transactionRepository.insert(txn)
                    } else return methodError()
                }
                "categories" -> {
                    if (method == "DELETE" && id != null) {
                        val categories = categoryRepository.getAll().first()
                        val category = categories.firstOrNull { it.id == id } ?: return LanApiResponse(404, "{\"error\":\"Category not found\"}")
                        require(!category.isDefault) { "Default categories cannot be deleted" }
                        val fallback = categories.firstOrNull {
                            it.type == category.type && it.isDefault && it.parentId == null &&
                                it.name.equals(if (category.type == "EXPENSE") "Other" else "Other Income", ignoreCase = true)
                        } ?: categories.firstOrNull { it.type == category.type && it.isDefault && it.parentId == null }
                            ?: categories.firstOrNull { it.type == category.type && it.parentId == null && it.id != id }
                            ?: return LanApiResponse(400, "{\"error\":\"Add another top-level ${category.type.lowercase()} category before deleting this one.\"}")
                        val children = categoryRepository.getByParentId(id)
                        (listOf(category) + children).forEach { old ->
                            transactionRepository.updateCategoryId(old.id, fallback.id)
                        }
                        budgetRepository.getAll().first().filter { budget -> budget.categoryId == id || children.any { it.id == budget.categoryId } }
                            .forEach { budgetRepository.delete(it) }
                        categoryRepository.delete(category)
                        children.forEach { categoryRepository.delete(it) }
                    } else if (method == "POST" || method == "PUT") {
                        val input = Json.decodeFromString<WebCategoryInput>(body)
                        require(input.name.isNotBlank() && input.type in setOf("INCOME", "EXPENSE"))
                        val existing = input.id?.let { wanted -> categoryRepository.getAll().first().firstOrNull { it.id == wanted } }
                        if (input.parentId != null) require(input.parentId != input.id && categoryRepository.getAll().first().any { it.id == input.parentId && it.type == input.type && it.parentId == null }) { "Choose a valid parent category" }
                        categoryRepository.insert(CategoryEntity(input.id ?: java.util.UUID.randomUUID().toString(), input.name.trim(), input.type,
                            color = existing?.color ?: 0xFF607D8BL, icon = existing?.icon.orEmpty(), isDefault = existing?.isDefault ?: false, parentId = input.parentId))
                    } else return methodError()
                }
                "budgets" -> {
                    if (method == "DELETE" && id != null) {
                        budgetRepository.getAll().first().firstOrNull { it.id == id }?.let { budgetRepository.deleteWithChildren(it) }
                    } else if (method == "POST" || method == "PUT") {
                        val input = Json.decodeFromString<WebBudgetInput>(body)
                        require(input.amount > 0)
                        require(categoryRepository.getAll().first().any { it.id == input.categoryId && it.type == "EXPENSE" }) { "Choose an expense category" }
                        budgetRepository.insert(BudgetEntity(input.categoryId, input.categoryId, input.amount, 0, 0))
                    } else return methodError()
                }
                "wishlist" -> {
                    if (method == "DELETE" && id != null) wishlistRepository.getAll().first().firstOrNull { it.id == id }?.let { wishlistRepository.delete(it) }
                    else if (method == "POST" || method == "PUT") {
                        val input = Json.decodeFromString<WebWishlistInput>(body)
                        require(input.name.isNotBlank() && input.targetAmount > 0)
                        val old = input.id?.let { wanted -> wishlistRepository.getAll().first().firstOrNull { it.id == wanted } }
                        wishlistRepository.insert(WishlistItemEntity(input.id ?: java.util.UUID.randomUUID().toString(), input.name.trim(), input.targetAmount, old?.createdAt ?: System.currentTimeMillis()))
                    } else return methodError()
                }
                "payment-methods" -> {
                    if (method == "DELETE" && id != null) paymentMethodRepository.getAll().first().firstOrNull { it.id == id }?.let { paymentMethodRepository.delete(it) }
                    else if (method == "POST" || method == "PUT") {
                        val input = Json.decodeFromString<WebPaymentMethodInput>(body)
                        require(input.name.isNotBlank())
                        val item = PaymentMethodEntity(input.id ?: java.util.UUID.randomUUID().toString(), input.name.trim(), isDefault = false)
                        paymentMethodRepository.insert(item)
                        if (input.isDefault) paymentMethodRepository.setDefault(item.id)
                    } else return methodError()
                }
                else -> return LanApiResponse(404, "{\"error\":\"Not found\"}")
            }
            LanApiResponse()
        } catch (error: Exception) {
            LanApiResponse(400, Json.encodeToString(mapOf("error" to (error.message ?: "Invalid request"))))
        }
    }

    private fun methodError() = LanApiResponse(405, "{\"error\":\"Unsupported method\"}")

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
