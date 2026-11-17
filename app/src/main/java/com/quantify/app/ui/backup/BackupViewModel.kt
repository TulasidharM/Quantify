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
import com.quantify.app.ui.screens.csvimport.CsvImportPreferences
import com.quantify.app.ui.theme.localeForCurrencyCode
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
    val wishlistItems: List<WishlistItemEntity> = emptyList()
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val budgetRepository: BudgetRepository,
    private val wishlistItemRepository: WishlistItemRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    val currency = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("currency")] ?: "USD"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    val lastPhonePeImportEpoch = dataStore.data.map { prefs ->
        prefs[CsvImportPreferences.lastPhonePeImportEpoch]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val lastBankImportEpoch = dataStore.data.map { prefs ->
        prefs[CsvImportPreferences.lastBankImportEpoch]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    private suspend fun createBackupFile(): BackupFile = withContext(Dispatchers.IO) {
        BackupFile(
            exportedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            transactions = transactionRepository.getAll().first(),
            categories = categoryRepository.getAll().first(),
            paymentMethods = paymentMethodRepository.getAll().first(),
            budgets = budgetRepository.getAll().first(),
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

        // Wishlist items
        wishlistItemRepository.deleteAll()
        backup.wishlistItems.forEach { wishlistItemRepository.insert(it) }
    }

    fun generatePdf(
        year: Int,
        month: Int,
        currency: String,
        file: java.io.File,
        onComplete: (Result<java.io.File>) -> Unit
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

            val locale = localeForCurrencyCode(currency)
            val currencyFormat = java.text.NumberFormat.getCurrencyInstance(locale).apply {
                this.currency = java.util.Currency.getInstance(currency)
                maximumFractionDigits = 0
                minimumFractionDigits = 0
            }

            val monthName = java.time.Month.of(month)
                .getDisplayName(java.time.format.TextStyle.FULL, locale)
            val reportPeriod = "$monthName $year"

            // StateFlow can still hold its initial empty value when sharing is tapped
            // before the month query has emitted. Read the selected month directly so
            // the report always reflects the database contents.
            val transactions = transactionRepository.getTransactionsForMonth(year, month)

            val categoryMap = categoryRepository.getAll().first().associateBy({ it.id }, { it })

            val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
            val totalExpenses = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val investmentRoot = categoryMap.values.firstOrNull {
                it.parentId == null && it.type == "EXPENSE" && it.name.equals("Investment", ignoreCase = true)
            }
            val investmentCategoryIds = mutableSetOf<String>()
            investmentRoot?.let { investmentCategoryIds.add(it.id) }
            var addedInvestmentCategory: Boolean
            do {
                val previousSize = investmentCategoryIds.size
                categoryMap.values.filter { it.parentId in investmentCategoryIds }
                    .forEach { investmentCategoryIds.add(it.id) }
                addedInvestmentCategory = investmentCategoryIds.size > previousSize
            } while (addedInvestmentCategory)
            val investmentSpending = transactions
                .filter { it.type == "EXPENSE" && it.categoryId in investmentCategoryIds }
                .sumOf { it.amount }
            val expensesExcludingInvestment = (totalExpenses - investmentSpending).coerceAtLeast(0.0)
            val netBalance = totalIncome - totalExpenses

            val monthlyBudgetRows = budgetRepository.getBudgetsWithSpending(year, month).first()
            val budgetRowsByCategory = monthlyBudgetRows.associateBy { it.budget.categoryId }
            val parentBudgetRows = monthlyBudgetRows.filter { row ->
                categoryMap[row.budget.categoryId]?.parentId == null
            }
            val orphanSubcategoryBudgetRows = monthlyBudgetRows.filter { row ->
                val parentId = categoryMap[row.budget.categoryId]?.parentId
                parentId != null && parentId !in budgetRowsByCategory
            }
            val summaryBudgetRows = (parentBudgetRows + orphanSubcategoryBudgetRows)
                .sortedByDescending { it.budget.amount }
            val totalBudgeted = summaryBudgetRows.sumOf { it.budget.amount }
            val budgetedSpending = summaryBudgetRows.sumOf { it.spent }
            val unbudgetedSpending = (totalExpenses - budgetedSpending).coerceAtLeast(0.0)
            val remainingBudget = totalBudgeted - budgetedSpending

            val expenseTransactions = transactions.filter { it.type == "EXPENSE" }
            val categoryTotals = expenseTransactions
                .groupBy { it.categoryId }
                .filterKeys { it !in investmentCategoryIds }
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

            fun drawMetricCard(
                canvas: Canvas,
                left: Float,
                top: Float,
                width: Float,
                label: String,
                amount: Double,
                valueColor: Int = TEXT_PRIMARY
            ) {
                drawRoundedRect(canvas, left, top, left + width, top + 62f, 10f, WHITE)
                drawTextOnCanvas(canvas, label.uppercase(locale), left + 12f, top + 11f,
                    paint(TEXT_SECONDARY, 8f, true))
                val amountText = (if (amount < 0) "-" else "") + currencyFormat.format(kotlin.math.abs(amount))
                drawTextOnCanvas(canvas, amountText, left + 12f, top + 32f,
                    paint(valueColor, 14f, true))
            }

            fun formatAmount(amount: Double): String {
                return currencyFormat.format(kotlin.math.abs(amount))
            }

            fun formatDate(epochSeconds: Long): String {
                return try {
                    val instant = java.time.Instant.ofEpochSecond(epochSeconds)
                    val date = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                    date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm", locale))
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
            val totalPages = 2 + transactionsPerPage.size

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
                val cardWidth = (CONTENT_WIDTH - gap * 2f) / 3f

                // Income
                drawRoundedRect(canvas, MARGIN, summaryTop, MARGIN + cardWidth, summaryBottom, 12f, LIGHT_GREEN)
                drawTextOnCanvas(canvas, "TOTAL INCOME", MARGIN + 15f, summaryTop + 17f, paint(GREEN, 9f, true))
                drawTextOnCanvas(canvas, formatAmount(totalIncome), MARGIN + 15f, summaryTop + 44f, paint(TEXT_PRIMARY, 13f, true))

                // Expenses
                val expenseLeft = MARGIN + cardWidth + gap
                drawRoundedRect(canvas, expenseLeft, summaryTop, expenseLeft + cardWidth, summaryBottom, 12f, LIGHT_RED)
                drawTextOnCanvas(canvas, "TOTAL EXPENSES", expenseLeft + 15f, summaryTop + 17f, paint(RED, 9f, true))
                drawTextOnCanvas(canvas, formatAmount(expensesExcludingInvestment), expenseLeft + 15f, summaryTop + 44f, paint(TEXT_PRIMARY, 13f, true))

                val investmentLeft = MARGIN + (cardWidth + gap) * 2f
                drawRoundedRect(canvas, investmentLeft, summaryTop, investmentLeft + cardWidth, summaryBottom, 12f, LIGHT_GREEN)
                drawTextOnCanvas(canvas, "INVESTMENT", investmentLeft + 15f, summaryTop + 17f, paint(GREEN, 9f, true))
                drawTextOnCanvas(canvas, formatAmount(investmentSpending), investmentLeft + 15f, summaryTop + 44f, paint(GREEN, 13f, true))

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
                        val percentage = if (expensesExcludingInvestment > 0) (amount / expensesExcludingInvestment) * 100.0 else 0.0

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

            fun drawBudgetSummaryPage(canvas: Canvas) {
                canvas.drawColor(BACKGROUND)
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 130f, paint(NAVY))
                drawTextOnCanvas(canvas, "QUANTIFY", MARGIN, 38f, paint(WHITE, 11f, true))
                drawTextOnCanvas(canvas, "Budget summary", MARGIN, 68f, paint(WHITE, 23f, true))
                drawTextOnCanvas(canvas, "Universal monthly limits · $reportPeriod",
                    MARGIN, 101f, paint(android.graphics.Color.rgb(190, 202, 222), 10f))

                val metricGap = 12f
                val metricWidth = (CONTENT_WIDTH - metricGap) / 2f
                drawMetricCard(canvas, MARGIN, 152f, metricWidth, "Total budgeted", totalBudgeted)
                drawMetricCard(canvas, MARGIN + metricWidth + metricGap, 152f, metricWidth,
                    "Budgeted spending", budgetedSpending, GREEN)
                drawMetricCard(canvas, MARGIN, 226f, metricWidth,
                    "Unbudgeted spending", unbudgetedSpending, RED)
                drawMetricCard(canvas, MARGIN + metricWidth + metricGap, 226f, metricWidth,
                    "Budget remaining", remainingBudget,
                    if (remainingBudget < 0) RED else GREEN)

                drawTextOnCanvas(canvas, "Budget coverage", MARGIN, 322f, paint(TEXT_PRIMARY, 15f, true))
                drawTextOnCanvas(canvas, "Category", MARGIN, 350f, paint(TEXT_SECONDARY, 8f, true))
                drawRightText(canvas, "SPENT / LIMIT", PAGE_WIDTH.toFloat() - MARGIN, 350f,
                    paint(TEXT_SECONDARY, 8f, true))
                canvas.drawLine(MARGIN, 365f, PAGE_WIDTH.toFloat() - MARGIN, 365f, paint(BORDER, 1f))

                val displayedBudgets = summaryBudgetRows.take(7)
                if (displayedBudgets.isEmpty()) {
                    drawTextOnCanvas(canvas, "No budgets set for this month.", MARGIN, 382f,
                        paint(TEXT_SECONDARY, 9f))
                } else {
                    var rowY = 375f
                    displayedBudgets.forEach { row ->
                        val name = categoryName(row.budget.categoryId)
                        val namePaint = paint(TEXT_PRIMARY, 9f)
                        var displayName = name
                        while (displayName.isNotEmpty() && namePaint.measureText(displayName) > CONTENT_WIDTH * 0.54f) {
                            displayName = displayName.dropLast(1)
                        }
                        if (displayName != name) displayName = displayName.trimEnd() + "…"
                        drawTextOnCanvas(canvas, displayName, MARGIN, rowY, namePaint)
                        drawRightText(canvas,
                            "${formatAmount(row.spent)} / ${formatAmount(row.budget.amount)}",
                            PAGE_WIDTH.toFloat() - MARGIN, rowY, paint(TEXT_PRIMARY, 9f, true))
                        canvas.drawLine(MARGIN, rowY + 15f, PAGE_WIDTH.toFloat() - MARGIN,
                            rowY + 15f, paint(BORDER, 0.6f))
                        rowY += 27f
                    }
                    if (summaryBudgetRows.size > displayedBudgets.size) {
                        drawTextOnCanvas(canvas,
                            "+ ${summaryBudgetRows.size - displayedBudgets.size} more budget categories",
                            MARGIN, 570f, paint(TEXT_SECONDARY, 8f))
                    }
                }

                drawFooter(canvas, 2, totalPages)
            }

            // ---------------------------------------------------------
            // TRANSACTION TABLE PAGE
            // ---------------------------------------------------------
            val dateWidth = 86f
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
                drawTextOnCanvas(canvas, "DATE / TIME", colDate + 5f, headerTop, paint(WHITE, 8f, true))
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

                val planInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
                val planPage = document.startPage(planInfo)
                drawBudgetSummaryPage(planPage.canvas)
                document.finishPage(planPage)

                // Transaction pages
                transactionsPerPage.forEachIndexed { index, pageTransactions ->
                    val pageNumber = index + 3
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
                onComplete(Result.success(file))
            } catch (error: Exception) {
                onComplete(Result.failure(error))
            } finally {
                document.close()
            }
        }
    }
}
