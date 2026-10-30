package com.quantify.app.ui.screens.csvimport

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.PaymentMethodEntity
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.data.repository.CategoryRepository
import com.quantify.app.data.repository.PaymentMethodRepository
import com.quantify.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** User's classification choice for one CSV row */
data class RowClassification(
    val categoryId: String? = null,
    val paymentMethodId: String? = null,
    val label: String = "",
    val ignored: Boolean = false
)

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        private val KEY_SESSION = stringPreferencesKey("csvimport_session_json")
        private val KEY_HAS_PENDING = booleanPreferencesKey("csvimport_has_pending")
    }

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(CsvImportState())
    val state: StateFlow<CsvImportState> = _state.asStateFlow()

    private val _hasPendingSession = MutableStateFlow(false)
    val hasPendingSession: StateFlow<Boolean> = _hasPendingSession.asStateFlow()

    private fun isClassifying(): Boolean = _state.value.status == CsvImportStatus.CLASSIFYING

    private fun saveSession() {
        if (!isClassifying()) return
        val s = _state.value
        if (s.parsedRows.isEmpty()) return
        viewModelScope.launch {
            val session = CsvImportSession(
                rows = s.parsedRows.map { it.toSnapshot() },
                classifications = s.classifications.map { it.toSnapshot() },
                currentRow = s.currentRow
            )
            dataStore.edit { prefs ->
                prefs[KEY_SESSION] = json.encodeToString(session)
                prefs[KEY_HAS_PENDING] = true
            }
        }
    }

    fun restoreSession() {
        val s = _state.value
        if (s.parsedRows.isNotEmpty()) return // already have data
        viewModelScope.launch {
            try {
                val prefs = dataStore.data.first()
                val jsonStr = prefs[KEY_SESSION] ?: return@launch
                val session = json.decodeFromString<CsvImportSession>(jsonStr)
                val rows = session.rows.mapIndexed { i, snap -> snap.fromSnapshot(i) }
                val classifications = session.classifications.map { it.toClassification() }.toMutableList()
                _state.update {
                    it.copy(
                        parsedRows = rows,
                        classifications = classifications,
                        currentRow = session.currentRow.coerceIn(0, rows.size.coerceAtLeast(1) - 1),
                        status = CsvImportStatus.CLASSIFYING
                    )
                }
                clearSession()
            } catch (e: Exception) {
                Log.e("CsvImport", "Failed to restore session", e)
                clearSession()
            }
        }
    }

    private fun clearSession() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(KEY_SESSION)
                prefs[KEY_HAS_PENDING] = false
            }
            _hasPendingSession.value = false
        }
    }

    fun checkPendingSession() {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _hasPendingSession.value = prefs[KEY_HAS_PENDING] ?: false
            }
        }
    }

    init {
        viewModelScope.launch {
            val (cats, paymentMethods, defaultPmId) = withContext(Dispatchers.IO) {
                val cats = categoryRepository.getAll().first()
                val paymentMethods = paymentMethodRepository.getAll().first()
                val defaultPmId = paymentMethodRepository.getDefault()?.id
                Triple(cats, paymentMethods, defaultPmId)
            }
            _state.update { it.copy(categories = cats, paymentMethods = paymentMethods, defaultPaymentMethodId = defaultPmId) }
        }
    }

    fun loadCsv(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
            }
            Log.d("CsvImport", "loadCsv: text length=${text.length}")
            if (text.isBlank()) {
                Log.w("CsvImport", "loadCsv: file is blank")
                _state.update { it.copy(status = CsvImportStatus.ERROR, errorMessage = "Could not read the file. Please try again.") }
                return@launch
            }
            Log.d("CsvImport", "loadCsv: first 300 chars: ${text.take(300).replace("\n", "\\n")}")
            val rows = withContext(Dispatchers.Default) { parsePhonePeCsv(text) }
            Log.d("CsvImport", "loadCsv: parsed ${rows.size} rows")
            if (rows.isEmpty()) {
                val lineCount = text.lines().size
                val firstLine = text.lines().firstOrNull()?.take(200) ?: "none"
                Log.w("CsvImport", "loadCsv: 0 rows parsed from $lineCount lines, firstLine=$firstLine")
                _state.update {
                    it.copy(
                        status = CsvImportStatus.ERROR,
                        errorMessage = "No valid transactions found (${lineCount} lines, 0 matched). Make sure this is a PhonePe CSV statement."
                    )
                }
                return@launch
            }
            val classifications = rows.map {
                RowClassification(paymentMethodId = _state.value.defaultPaymentMethodId)
            }
            _state.update {
                it.copy(
                    parsedRows = rows,
                    classifications = classifications.toMutableList(),
                    currentRow = 0,
                    status = CsvImportStatus.CLASSIFYING
                )
            }
            // Overwrite any pending session with the newly loaded CSV
            saveSession()
        }
    }

    fun setCategory(index: Int, categoryId: String) {
        _state.update {
            val cl = it.classifications[index]
            it.copy(classifications = it.classifications.toMutableList().apply { this[index] = cl.copy(categoryId = categoryId) })
        }
        saveSession()
    }

    fun setPaymentMethod(index: Int, paymentMethodId: String?) {
        _state.update {
            val cl = it.classifications[index]
            it.copy(classifications = it.classifications.toMutableList().apply { this[index] = cl.copy(paymentMethodId = paymentMethodId) })
        }
        saveSession()
    }

    fun setLabel(index: Int, label: String) {
        _state.update {
            val cl = it.classifications[index]
            it.copy(classifications = it.classifications.toMutableList().apply { this[index] = cl.copy(label = label) })
        }
        saveSession()
    }

    fun setIgnored(index: Int, ignored: Boolean) {
        _state.update {
            val cl = it.classifications[index]
            it.copy(classifications = it.classifications.toMutableList().apply { this[index] = cl.copy(ignored = ignored) })
        }
        saveSession()
    }

    /** Remove the row entirely and stay on the same index (which is now the next row) */
    fun ignoreAndNext(index: Int) {
        _state.update {
            val newRows = it.parsedRows.toMutableList().apply { removeAt(index) }
            val newClassifications = it.classifications.toMutableList().apply { removeAt(index) }
            // Stay at [index] which now points to the next row.
            // If we removed the last row, back up by one.
            val newRow = if (index < newRows.size) index else maxOf(0, index - 1)
            it.copy(parsedRows = newRows, classifications = newClassifications, currentRow = newRow)
        }
        saveSession()
    }

    /**
     * Split the current row into two: the current row keeps [firstAmount],
     * a new row with [remainingAmount] is inserted right after.
     * Stays on the current row index so the user can classify the first part.
     */
    fun splitRow(index: Int, firstAmount: Double) {
        _state.update {
            val remaining = it.parsedRows[index].amount - firstAmount
            val newRow = it.parsedRows[index].copy(amount = firstAmount)
            val newRemainderRow = it.parsedRows[index].copy(amount = remaining)
            val newRows = it.parsedRows.toMutableList().apply {
                set(index, newRow)
                add(index + 1, newRemainderRow)
            }
            val newClassifications = it.classifications.toMutableList().apply {
                // Keep the existing classification for the first part (index unchanged)
                add(index + 1, RowClassification(paymentMethodId = it.defaultPaymentMethodId))
            }
            it.copy(parsedRows = newRows, classifications = newClassifications)
        }
        saveSession()
    }

    fun nextRow() {
        _state.update {
            if (it.currentRow < it.parsedRows.size - 1) it.copy(currentRow = it.currentRow + 1)
            else it
        }
        saveSession()
    }

    fun prevRow() {
        _state.update {
            if (it.currentRow > 0) it.copy(currentRow = it.currentRow - 1)
            else it
        }
        saveSession()
    }

    fun saveAllTransactions(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val rows = _state.value.parsedRows
                val classifications = _state.value.classifications
                var saved = 0
                Log.d("CsvImport", "Starting save: ${rows.size} rows, ${classifications.size} classifications")

                for (i in rows.indices) {
                    val row = rows[i]
                    val cl = classifications[i]
                    if (cl.ignored) continue

                    val type = when (row.direction) {
                        Direction.PAID -> "EXPENSE"
                        Direction.RECEIVED, Direction.REFUND -> "INCOME"
                    }

                    // Resolve epoch seconds once per row
                    val epoch: Long = try { row.epochSeconds } catch (e: Exception) {
                        Log.w("CsvImport", "Skipping row $i: invalid datetime (${e.message})")
                        continue
                    }

                    val categoryId = cl.categoryId
                    if (categoryId == null) continue

                    val txn = TransactionEntity(
                        amount = row.amount,
                        type = type,
                        categoryId = categoryId,
                        paymentMethodId = cl.paymentMethodId,
                        description = if (cl.label.isNotBlank()) cl.label else row.vendor,
                        dateEpochSeconds = epoch,
                        tags = ""
                    )
                    Log.d("CsvImport", "Inserting txn row=$i amount=${row.amount} catId=$categoryId epoch=$epoch")
                    transactionRepository.insert(txn)
                    saved++
                }

                Log.d("CsvImport", "All inserted, saved=$saved")
                _state.update { it.copy(status = CsvImportStatus.DONE, savedCount = saved) }
                clearSession()
                onComplete()
            } catch (e: Exception) {
                Log.e("CsvImport", "Failed to save transactions", e)
                _state.update {
                    it.copy(
                        status = CsvImportStatus.ERROR,
                        errorMessage = "Failed to save transactions: ${e.message}"
                    )
                }
            }
        }
    }

    fun reset() {
        viewModelScope.launch {
            clearSession()
            val (cats, paymentMethods, defaultPmId) = withContext(Dispatchers.IO) {
                val cats = categoryRepository.getAll().first()
                val paymentMethods = paymentMethodRepository.getAll().first()
                val defaultPmId = paymentMethodRepository.getDefault()?.id
                Triple(cats, paymentMethods, defaultPmId)
            }
            _state.update {
                it.copy(
                    parsedRows = emptyList(),
                    classifications = mutableListOf(),
                    currentRow = 0,
                    status = CsvImportStatus.IDLE,
                    savedCount = 0,
                    errorMessage = null,
                    categories = cats,
                    paymentMethods = paymentMethods,
                    defaultPaymentMethodId = defaultPmId
                )
            }
        }
    }
}

data class CsvImportState(
    val parsedRows: List<ParsedCsvRow> = emptyList(),
    val classifications: MutableList<RowClassification> = mutableListOf(),
    val currentRow: Int = 0,
    val categories: List<CategoryEntity> = emptyList(),
    val paymentMethods: List<PaymentMethodEntity> = emptyList(),
    val defaultPaymentMethodId: String? = null,
    val status: CsvImportStatus = CsvImportStatus.IDLE,
    val savedCount: Int = 0,
    val errorMessage: String? = null
)

enum class CsvImportStatus {
    IDLE, CLASSIFYING, DONE, ERROR
}
