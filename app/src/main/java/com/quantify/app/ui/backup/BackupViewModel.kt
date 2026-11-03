package com.quantify.app.ui.backup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
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
    val wishlistItems: List<WishlistItemEntity> = emptyList()
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val budgetRepository: BudgetRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val wishlistItemRepository: WishlistItemRepository,
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
                    writer.write("Date,Type,Amount,Category,Description\n")
                    val txns = transactionRepository.getAll().first()
                    for (txn in txns) {
                        val date = txn.date.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                        writer.write("${date.year}-${date.monthNumber}-${date.dayOfMonth},${txn.type},${txn.amount},${txn.categoryId},${txn.description ?: ""}\n")
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
            wishlistItems = wishlistItemRepository.getAll().first()
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

        // Budgets
        budgetRepository.deleteAll()
        backup.budgets.forEach { budgetRepository.insert(it) }

        // Recurring rules
        recurringRuleRepository.deleteAll()
        backup.recurringRules.forEach { recurringRuleRepository.insert(it) }

        // Wishlist items
        wishlistItemRepository.deleteAll()
        backup.wishlistItems.forEach { wishlistItemRepository.insert(it) }
    }

    fun generatePdf(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        year: Int,
        month: Int,
        currency: String,
        file: java.io.File
    ) {
        viewModelScope.launch {
            val document = PdfDocument()

            // A4 dimensions in points
            val PAGE_WIDTH = 595
            val PAGE_HEIGHT = 842

            // Page layout
            val MARGIN = 72f
            val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)

            // Palette
            val NAVY = android.graphics.Color.rgb(20, 31, 52)
            val NAVY_LIGHT = android.graphics.Color.rgb(35, 49, 73)
            val BACKGROUND = android.graphics.Color.rgb(247, 249, 252)
            val WHITE = android.graphics.Color.WHITE
            val TEXT_PRIMARY = android.graphics.Color.rgb(35, 45, 62)
            val TEXT_SECONDARY = android.graphics.Color.rgb(115, 125, 140)
            val BORDER = android.graphics.Color.rgb(225, 230, 237)
            val GREEN = android.graphics.Color.rgb(25, 155, 105)
            val RED = android.graphics.Color.rgb(220, 75, 85)
            val LIGHT_GREEN = android.graphics.Color.rgb(232, 247, 240)
            val LIGHT_RED = android.graphics.Color.rgb(253, 237, 238)
            val ROW_ALT = android.graphics.Color.rgb(246, 248, 251)

            val locale = java.util.Locale("en", "IN")
            val currencyFormat = java.text.NumberFormat.getCurrencyInstance(locale).apply {
                maximumFractionDigits = 0
                minimumFractionDigits = 0
            }

            val monthName = java.time.Month.of(month)
                .getDisplayName(java.time.format.TextStyle.FULL, locale)
            val reportPeriod = "$monthName $year"

            val categoryMap = categories.associateBy({ it.id }, { it })

            val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
            val totalExpenses = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val netBalance = totalIncome - totalExpenses

            val expenseTransactions = transactions.filter { it.type == "EXPENSE" }
            val categoryTotals = expenseTransactions
                .groupBy { it.categoryId }
                .mapValues { (_, items) -> items.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }

            val transactionCount = transactions.size

            // ---------------------------------------------------------
            // PAINT HELPERS
            // ---------------------------------------------------------
            fun paint(
                color: Int,
                textSize: Float = 12f,
                bold: Boolean = false
            ): Paint {
                return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    this.textSize = textSize
                    typeface = if (bold) {
                        android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    } else {
                        android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                    }
                    style = Paint.Style.FILL
                }
            }

            fun drawTextOnCanvas(
                canvas: Canvas,
                text: String,
                x: Float,
                top: Float,
                p: Paint
            ) {
                val fm = p.fontMetrics
                val baseline = top - fm.ascent
                canvas.drawText(text, x, baseline, p)
            }

            fun drawRightText(
                canvas: Canvas,
                text: String,
                right: Float,
                top: Float,
                p: Paint
            ) {
                val fm = p.fontMetrics
                val baseline = top - fm.ascent
                canvas.drawText(text, right - p.measureText(text), baseline, p)
            }

            fun drawRoundedRect(
                canvas: Canvas,
                left: Float,
                top: Float,
                right: Float,
                bottom: Float,
                radius: Float,
                color: Int
            ) {
                val p = paint(color)
                canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, p)
            }

            fun formatAmount(amount: Double): String {
                return currencyFormat.format(kotlin.math.abs(amount))
            }

            fun formatDate(epochSeconds: Long): String {
                return try {
                    val instant = java.time.Instant.ofEpochSecond(epochSeconds)
                    val date = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                    date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", locale))
                } catch (e: Exception) {
                    "-"
                }
            }

            fun categoryColor(categoryId: String): Int {
                val storedColor = categoryMap[categoryId]?.color ?: 0xFF94A3B8L
                return storedColor.toInt()
            }

            fun categoryName(categoryId: String): String {
                return categoryMap[categoryId]?.name ?: "Uncategorized"
            }

            // ---------------------------------------------------------
            // PAGE FOOTER
            // ---------------------------------------------------------
            fun drawFooter(canvas: Canvas, pageNumber: Int, totalPages: Int) {
                val y = 785f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH.toFloat() - MARGIN, y, paint(BORDER, 1f))
                drawTextOnCanvas(canvas, "QUANTIFY  •  FINANCIAL REPORT", MARGIN, 800f, paint(TEXT_SECONDARY, 8f, true))
                drawRightText(canvas, "$pageNumber / $totalPages", PAGE_WIDTH.toFloat() - MARGIN, 800f, paint(TEXT_SECONDARY, 9f, true))
            }

            // ---------------------------------------------------------
            // COVER PAGE
            // ---------------------------------------------------------
            val sortedTransactions = transactions.sortedByDescending { it.amount }
            val rowsPerPage = 18 // estimated, calculated below
            val tableHeaderHeight = 32f
            val rowHeight = 34f
            val tableTop = 145f
            val footerLimit = 765f
            val availableTableHeight = footerLimit - tableTop - tableHeaderHeight
            val actualRowsPerPage = (availableTableHeight / rowHeight).toInt().coerceAtLeast(1)
            val transactionsPerPage = sortedTransactions.chunked(actualRowsPerPage)
            val totalPages = 1 + transactionsPerPage.size

            fun drawCoverPage(canvas: Canvas) {
                canvas.drawColor(BACKGROUND)

                // Header background
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 285f, paint(NAVY))

                // Brand
                drawTextOnCanvas(canvas, "QUANTIFY", MARGIN, 48f, paint(WHITE, 12f, true))
                drawRightText(canvas, "PERSONAL FINANCE", PAGE_WIDTH.toFloat() - MARGIN, 50f, paint(android.graphics.Color.rgb(165, 180, 205), 8f, true))

                // Title
                drawTextOnCanvas(canvas, "Monthly Financial", MARGIN, 105f, paint(WHITE, 29f, true))
                drawTextOnCanvas(canvas, "Report", MARGIN, 143f, paint(WHITE, 29f, true))
                drawTextOnCanvas(canvas, reportPeriod, MARGIN, 194f, paint(android.graphics.Color.rgb(190, 202, 222), 15f))
                drawTextOnCanvas(canvas, "$transactionCount transactions recorded", MARGIN, 225f, paint(android.graphics.Color.rgb(165, 180, 205), 10f))

                // Net balance card
                val cardTop = 255f
                val cardBottom = 370f
                drawRoundedRect(canvas, MARGIN, cardTop, PAGE_WIDTH.toFloat() - MARGIN, cardBottom, 14f, WHITE)
                drawTextOnCanvas(canvas, "NET BALANCE", MARGIN + 22f, cardTop + 20f, paint(TEXT_SECONDARY, 9f, true))
                val netColor = if (netBalance >= 0) GREEN else RED
                drawTextOnCanvas(
                    canvas,
                    (if (netBalance < 0) "- " else "") + formatAmount(netBalance),
                    MARGIN + 22f, cardTop + 46f, paint(netColor, 27f, true)
                )
                drawTextOnCanvas(canvas, "Income minus expenses", MARGIN + 22f, cardTop + 84f, paint(TEXT_SECONDARY, 9f))

                // Income / Expense cards
                val summaryTop = 390f
                val summaryBottom = 485f
                val gap = 14f
                val cardWidth = (CONTENT_WIDTH - gap) / 2f

                // Income
                drawRoundedRect(canvas, MARGIN, summaryTop, MARGIN + cardWidth, summaryBottom, 12f, LIGHT_GREEN)
                drawTextOnCanvas(canvas, "TOTAL INCOME", MARGIN + 15f, summaryTop + 17f, paint(GREEN, 9f, true))
                drawTextOnCanvas(canvas, formatAmount(totalIncome), MARGIN + 15f, summaryTop + 44f, paint(TEXT_PRIMARY, 16f, true))

                // Expenses
                val expenseLeft = MARGIN + cardWidth + gap
                drawRoundedRect(canvas, expenseLeft, summaryTop, expenseLeft + cardWidth, summaryBottom, 12f, LIGHT_RED)
                drawTextOnCanvas(canvas, "TOTAL EXPENSES", expenseLeft + 15f, summaryTop + 17f, paint(RED, 9f, true))
                drawTextOnCanvas(canvas, formatAmount(totalExpenses), expenseLeft + 15f, summaryTop + 44f, paint(TEXT_PRIMARY, 16f, true))

                // Category Breakdown
                val sectionTop = 515f
                drawTextOnCanvas(canvas, "Expense Breakdown", MARGIN, sectionTop, paint(TEXT_PRIMARY, 17f, true))
                drawTextOnCanvas(canvas, "Spending by category", MARGIN, sectionTop + 26f, paint(TEXT_SECONDARY, 9f))

                if (categoryTotals.isEmpty()) {
                    drawTextOnCanvas(canvas, "No expense data available for this period.", MARGIN, sectionTop + 65f, paint(TEXT_SECONDARY, 10f))
                } else {
                    val maxCategories = 5
                    val displayedCategories = categoryTotals.take(maxCategories)
                    val barStartX = MARGIN
                    val barWidth = CONTENT_WIDTH
                    val barHeight = 7f
                    var currentY = sectionTop + 65f

                    for (entry in displayedCategories) {
                        val name = categoryName(entry.key)
                        val amount = entry.value
                        val percentage = if (totalExpenses > 0) (amount / totalExpenses) * 100.0 else 0.0

                        drawTextOnCanvas(canvas, name, barStartX, currentY, paint(TEXT_PRIMARY, 10f, true))
                        drawRightText(canvas, String.format(locale, "%.1f", percentage) + "%", barStartX + barWidth, currentY, paint(TEXT_SECONDARY, 9f, true))
                        currentY += 19f

                        // Bar background
                        drawRoundedRect(canvas, barStartX, currentY, barStartX + barWidth, currentY + barHeight, 4f, BORDER)

                        // Bar fill
                        val filledWidth = (barWidth * percentage / 100.0).toFloat().coerceIn(0f, barWidth)
                        if (filledWidth > 0f) {
                            drawRoundedRect(canvas, barStartX, currentY, barStartX + filledWidth, currentY + barHeight, 4f, categoryColor(entry.key))
                        }
                        currentY += 24f
                    }

                    if (categoryTotals.size > maxCategories) {
                        drawTextOnCanvas(canvas, "+ ${categoryTotals.size - maxCategories} more categories", MARGIN, currentY + 2f, paint(TEXT_SECONDARY, 9f))
                    }
                }

                drawFooter(canvas, 1, totalPages)
            }

            // ---------------------------------------------------------
            // TRANSACTION TABLE PAGE
            // ---------------------------------------------------------
            val dateWidth = 75f
            val categoryWidth = 100f
            val amountWidth = 105f
            val descriptionWidth = CONTENT_WIDTH - dateWidth - categoryWidth - amountWidth
            val colDate = MARGIN
            val colDescription = colDate + dateWidth
            val colCategory = colDescription + descriptionWidth

            fun drawTransactionPage(canvas: Canvas, pageNumber: Int, pageTransactions: List<TransactionEntity>) {
                canvas.drawColor(WHITE)

                // Running header
                drawTextOnCanvas(canvas, "QUANTIFY", MARGIN, 42f, paint(NAVY, 11f, true))
                drawRightText(canvas, reportPeriod.uppercase(locale), PAGE_WIDTH.toFloat() - MARGIN, 43f, paint(TEXT_SECONDARY, 9f, true))
                canvas.drawLine(MARGIN, 66f, PAGE_WIDTH.toFloat() - MARGIN, 66f, paint(BORDER, 1f))
                drawTextOnCanvas(canvas, "Transaction History", MARGIN, 89f, paint(TEXT_PRIMARY, 19f, true))
                drawTextOnCanvas(canvas, "${pageTransactions.size} transactions", MARGIN, 117f, paint(TEXT_SECONDARY, 9f))

                // Header background
                drawRoundedRect(canvas, MARGIN, tableTop.toFloat(), PAGE_WIDTH.toFloat() - MARGIN, tableTop + tableHeaderHeight, 5f, NAVY)
                val headerTop = tableTop + 9f
                drawTextOnCanvas(canvas, "DATE", colDate + 5f, headerTop, paint(WHITE, 8f, true))
                drawTextOnCanvas(canvas, "DESCRIPTION", colDescription + 5f, headerTop, paint(WHITE, 8f, true))
                drawTextOnCanvas(canvas, "CATEGORY", colCategory + 5f, headerTop, paint(WHITE, 8f, true))
                drawRightText(canvas, "AMOUNT", PAGE_WIDTH.toFloat() - MARGIN - 5f, headerTop, paint(WHITE, 8f, true))

                // Rows
                var currentY = tableTop + tableHeaderHeight
                pageTransactions.forEachIndexed { index, transaction ->
                    val rowTop = currentY
                    val rowBottom = rowTop + rowHeight

                    // Alternating backgrounds
                    if (index % 2 == 1) {
                        canvas.drawRect(MARGIN, rowTop, PAGE_WIDTH.toFloat() - MARGIN, rowBottom, paint(ROW_ALT))
                    }

                    // Bottom divider
                    canvas.drawLine(MARGIN, rowBottom, PAGE_WIDTH.toFloat() - MARGIN, rowBottom, paint(BORDER, 0.6f))

                    val textTop = rowTop + 10f

                    // Date
                    drawTextOnCanvas(canvas, formatDate(transaction.dateEpochSeconds), colDate + 5f, textTop, paint(TEXT_SECONDARY, 8f))

                    // Description
                    val description = transaction.description?.takeIf { it.isNotBlank() } ?: "No description"
                    val maxDescriptionWidth = descriptionWidth - 14f
                    val descriptionPaint = paint(TEXT_PRIMARY, 9f, true)
                    val displayDescription = if (descriptionPaint.measureText(description) > maxDescriptionWidth) {
                        var shortened = description
                        while (shortened.isNotEmpty() && descriptionPaint.measureText("$shortened\u2026") > maxDescriptionWidth) {
                            shortened = shortened.dropLast(1)
                        }
                        "$shortened\u2026"
                    } else {
                        description
                    }
                    drawTextOnCanvas(canvas, displayDescription, colDescription + 5f, textTop, descriptionPaint)

                    // Category
                    val category = categoryName(transaction.categoryId)
                    val dotP = paint(categoryColor(transaction.categoryId))
                    canvas.drawCircle(colCategory + 7f, rowTop + rowHeight / 2f, 3f, dotP)
                    val categoryPaint = paint(TEXT_SECONDARY, 8f)
                    val maxCategoryWidth = categoryWidth - 22f
                    var displayCategory = category
                    while (displayCategory.isNotEmpty() && categoryPaint.measureText(displayCategory) > maxCategoryWidth) {
                        displayCategory = displayCategory.dropLast(1)
                    }
                    if (displayCategory != category) {
                        displayCategory = displayCategory.trimEnd() + "\u2026"
                    }
                    drawTextOnCanvas(canvas, displayCategory, colCategory + 16f, textTop, categoryPaint)

                    // Amount
                    val isIncome = transaction.type == "INCOME"
                    val amountColor = if (isIncome) GREEN else RED
                    val amountText = (if (isIncome) "+" else "-") + formatAmount(transaction.amount)
                    drawRightText(canvas, amountText, PAGE_WIDTH.toFloat() - MARGIN - 5f, textTop, paint(amountColor, 9f, true))

                    currentY = rowBottom
                }

                drawFooter(canvas, pageNumber, totalPages)
            }

            // ---------------------------------------------------------
            // CREATE PDF PAGES
            // ---------------------------------------------------------
            try {
                // Cover page
                val coverInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val coverPage = document.startPage(coverInfo)
                drawCoverPage(coverPage.canvas)
                document.finishPage(coverPage)

                // Transaction pages
                transactionsPerPage.forEachIndexed { index, pageTransactions ->
                    val pageNumber = index + 2
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    val page = document.startPage(pageInfo)
                    drawTransactionPage(page.canvas, pageNumber, pageTransactions)
                    document.finishPage(page)
                }

                // Write to file
                file.outputStream().use { output ->
                    document.writeTo(output)
                    output.flush()
                }
            } finally {
                document.close()
            }
        }
    }
}
