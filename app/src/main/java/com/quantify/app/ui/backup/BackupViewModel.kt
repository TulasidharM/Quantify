package com.quantify.app.ui.backup

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantify.app.data.local.entity.*
import com.quantify.app.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

@Serializable
data class BackupFile(
    val version: Int = 1,
    val format: String = "quantify_backup",
    val exportedAt: Long,
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val paymentMethods: List<PaymentMethodEntity>,
    val budgets: List<BudgetEntity>,
    val recurringRules: List<RecurringRuleEntity>,
    val tags: List<TagEntity>
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val budgetRepository: BudgetRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val tagRepository: TagRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    val currency = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("currency")] ?: "USD"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    fun setCurrency(currency: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("currency")] = currency
            }
        }
    }

    val themeKey = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("theme")] ?: "forest"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "forest")

    fun setThemeKey(key: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("theme")] = key
            }
        }
    }

    // — Shared month selection (persisted, shared across all pages) —
    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val selectedYear = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("selectedYear")] ?: now.year.toString()
    }.map { it.toInt() }
        .stateIn(viewModelScope, SharingStarted.Lazily, now.year)

    val selectedMonth = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("selectedMonth")] ?: now.monthNumber.toString()
    }.map { it.toInt() }
        .stateIn(viewModelScope, SharingStarted.Lazily, now.monthNumber)

    fun selectMonth(year: Int, month: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("selectedYear")] = year.toString()
                prefs[stringPreferencesKey("selectedMonth")] = month.toString()
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val backup = createBackupFile()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(json.encodeToString(BackupFile.serializer(), backup))
                }
            }
        }
    }

    fun importBackup(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val backup = context.contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input).use { reader ->
                        json.decodeFromString(BackupFile.serializer(), reader.readText())
                    }
                } ?: run { onResult("Could not read file"); return@launch }
                // Validate
                if (backup.format != "quantify_backup") {
                    onResult("Invalid backup file format")
                    return@launch
                }
                restoreBackup(backup)
                onResult("Backup restored successfully")
            } catch (e: Exception) {
                onResult("Error: ${e.message ?: "Invalid backup file"}")
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write("Date,Type,Amount,Category,Description,Tags\n")
                    val txns = transactionRepository.getAll().first()
                    for (txn in txns) {
                        val date = txn.date.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                        writer.write("${date.year}-${date.monthNumber}-${date.dayOfMonth},${txn.type},${txn.amount},${txn.categoryId},${txn.description ?: ""},${txn.tags}\n")
                    }
                }
            }
        }
    }

    fun exportCategories(uri: Uri) {
        viewModelScope.launch {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write("Name,Type,Parent Name,Color,Is Default\n")
                    val cats = categoryRepository.getAll().first()
                    for (cat in cats) {
                        val parentName = cat.parentId?.let { pid ->
                            cats.find { it.id == pid }?.name ?: ""
                        } ?: ""
                        writer.write("${cat.name},${cat.type},${parentName},#${java.lang.Long.toHexString(cat.color).uppercase()},${cat.isDefault}\n")
                    }
                }
            }
        }
    }

    fun importCategories(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val lines = context.contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input).use { reader ->
                        reader.readLines()
                    }
                } ?: return@launch
                if (lines.isEmpty()) {
                    onResult("File is empty")
                    return@launch
                }

                // Parse header by splitting into columns
                val headerCols = lines[0].lowercase().split(",")
                val nameIdx = headerCols.indexOfFirst { it.trim() == "name" }
                val typeIdx = headerCols.indexOfFirst { it.trim() == "type" }
                val parentIdx = headerCols.indexOfFirst { it.trim().contains("parent") }
                val colorIdx = headerCols.indexOfFirst { it.trim() == "color" }
                val defaultIdx = headerCols.indexOfFirst { it.trim().contains("default") }
                if (nameIdx < 0 || typeIdx < 0) {
                    onResult("Invalid CSV format (missing Name or Type column)")
                    return@launch
                }

                // Collect existing categories to resolve parent names to IDs
                val existingCats = categoryRepository.getAll().first()

                // Build map: (type, name) -> id  for parent lookup
                val parentIdByNameAndType = mutableMapOf<Pair<String, String>, String>()
                for (cat in existingCats) {
                    if (cat.parentId == null) {
                        parentIdByNameAndType[Pair(cat.type, cat.name)] = cat.id
                    }
                }

                // Track newly inserted parents so we can link subs to them
                val newParentIds = mutableMapOf<Pair<String, String>, String>()
                var count = 0

                for (i in 1 until lines.size) {
                    val cols = lines[i].split(",")
                    if (cols.size < 2) continue

                    val name = cols.getOrElse(nameIdx) { "" }.trim()
                    val type = cols.getOrElse(typeIdx) { "" }.trim().uppercase()
                    val parentName = cols.getOrElse(parentIdx) { "" }.trim()
                    val colorStr = cols.getOrElse(colorIdx) { "" }.trim()
                    val isDefaultStr = cols.getOrElse(defaultIdx) { "false" }.trim().lowercase()

                    if (name.isEmpty() || type !in listOf("EXPENSE", "INCOME")) continue

                    // Parse color
                    var color = 0xFF000000L
                    try {
                        color = java.lang.Long.parseUnsignedLong(
                            colorStr.removePrefix("#"), 16
                        )
                    } catch (_: Exception) {}

                    // Resolve parent ID
                    val parentId = if (parentName.isNotBlank()) {
                        parentIdByNameAndType[Pair(type, parentName)]
                            ?: newParentIds[Pair(type, parentName)]
                            ?: null
                    } else null

                    // If no parent found and parentName is set, skip subcategory
                    if (parentName.isNotBlank() && parentId == null) continue

                    val id = if (parentId != null) {
                        parentId + "_" + name.lowercase().replace(" ", "_")
                    } else {
                        name.lowercase().replace(" ", "_")
                    }

                    val isDefault = isDefaultStr == "true"

                    categoryRepository.insert(CategoryEntity(
                        id = id,
                        name = name,
                        type = type,
                        color = color,
                        icon = "",
                        isDefault = isDefault,
                        parentId = parentId
                    ))
                    count++

                    // Register new parent for subsequent subcategory resolution
                    if (parentId == null) {
                        newParentIds[Pair(type, name)] = id
                    }
                }

                onResult("Imported $count categories")
            } catch (e: Exception) {
                onResult("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private suspend fun createBackupFile(): BackupFile = withContext(Dispatchers.IO) {
        BackupFile(
            exportedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            transactions = transactionRepository.getAll().first(),
            categories = categoryRepository.getAll().first(),
            paymentMethods = paymentMethodRepository.getAll().first(),
            budgets = budgetRepository.getByMonth(
                kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).year,
                kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).monthNumber
            ).first(),
            recurringRules = recurringRuleRepository.getAll().first(),
            tags = tagRepository.getAll().first()
        )
    }

    private suspend fun restoreBackup(backup: BackupFile) = withContext(Dispatchers.IO) {
        // Transactions
        transactionRepository.deleteAll()
        backup.transactions.forEach { transactionRepository.insert(it) }

        // Categories
        categoryRepository.deleteAll()
        backup.categories.forEach { categoryRepository.insert(it) }

        // Payment methods
        paymentMethodRepository.deleteAll()
        backup.paymentMethods.forEach { paymentMethodRepository.insert(it) }

        // Tags
        tagRepository.deleteAll()
        backup.tags.forEach { tagRepository.insert(it) }

        // Budgets
        budgetRepository.deleteAll()
        backup.budgets.forEach { budgetRepository.insert(it) }

        // Recurring rules
        recurringRuleRepository.deleteAll()
        backup.recurringRules.forEach { recurringRuleRepository.insert(it) }
    }
}
