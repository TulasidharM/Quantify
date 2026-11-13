package com.quantify.app.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.Icons
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.R
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.data.local.entity.WishlistItemEntity
import com.quantify.app.ui.theme.categoryColor
import com.quantify.app.ui.theme.LocalCurrencyCode
import com.quantify.app.ui.theme.localeForCurrencyCode
import com.quantify.app.ui.viewmodel.DashboardViewModel
import com.quantify.app.ui.viewmodel.WishlistViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onSharePdf: () -> Unit = {},
    onAddTransaction: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToUnbudgetedTransactions: () -> Unit = {},
    onNavigateToWishlist: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    wishlistViewModel: WishlistViewModel = hiltViewModel()
) {
    val recent by viewModel.recentTransactions.collectAsState()
    val income by viewModel.monthlyIncome.collectAsState()
    val expense by viewModel.monthlyExpense.collectAsState()
    val budgetAmount by viewModel.monthlyBudgetedTotal.collectAsState()
    val budgetUsed by viewModel.monthlyBudgetSpentTotal.collectAsState()
    val investmentSpending by viewModel.investmentSpending.collectAsState()
    val investmentBudgetUsed by viewModel.monthlyInvestmentBudgetSpentTotal.collectAsState()
    val parentCategorySpending by viewModel.parentCategorySpending.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedYear by viewModel.selectedYearFlow.collectAsState()
    val selectedMonth by viewModel.selectedMonthFlow.collectAsState()
    val savings = income - expense
    val displayedExpense = (expense - investmentSpending).coerceAtLeast(0.0)
    val displayedBudgetUsed = (budgetUsed - investmentBudgetUsed).coerceIn(0.0, displayedExpense)
    val displayedUnbudgeted = (displayedExpense - displayedBudgetUsed).coerceAtLeast(0.0)
    val wishlistItems by wishlistViewModel.items.collectAsState()

    val categoryMap = remember(categories) {
        categories.associateBy({ it.id }, { it.name })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onAddTransaction) {
                        Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                    }
                    IconButton(onClick = onSharePdf) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            com.quantify.app.ui.components.MonthPicker(
                selectedYear = selectedYear,
                selectedMonth = selectedMonth,
                onMonthSelect = { year, month -> viewModel.selectMonth(year, month) }
            )
            LazyColumn {
            // Summary cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Income", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                BoxWithConstraints(Modifier.weight(1f)) {
                                    val value = formatCurrency(income)
                                    val size = (maxWidth.value / (value.length.coerceAtLeast(1) * 0.54f)).coerceIn(16f, 24f).sp
                                    Text(value, modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = size),
                                        textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Investment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                BoxWithConstraints(Modifier.weight(1f)) {
                                    val value = formatCurrency(investmentSpending)
                                    val size = (maxWidth.value / (value.length.coerceAtLeast(1) * 0.54f)).coerceIn(16f, 24f).sp
                                    Text(value, modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = size),
                                        textAlign = TextAlign.End, color = Color(0xFF43A047),
                                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Remaining", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                BoxWithConstraints(Modifier.weight(1f)) {
                                    val value = formatCurrency(savings)
                                    val size = (maxWidth.value / (value.length.coerceAtLeast(1) * 0.54f)).coerceIn(16f, 24f).sp
                                    Text(value, modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = size),
                                        textAlign = TextAlign.End,
                                        color = if (savings >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                    Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Expense", style = MaterialTheme.typography.labelMedium)
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val expenseText = formatCurrency(displayedExpense)
                                val expenseFontSize = (
                                    maxWidth.value * 0.9f / (expenseText.length.coerceAtLeast(1) * 0.56f)
                                ).coerceIn(32f, 64f).sp
                                Text(
                                    expenseText,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = expenseFontSize),
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Budgeted", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatCurrency(displayedBudgetUsed), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToUnbudgetedTransactions),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Unbudgeted", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    formatCurrency(displayedUnbudgeted),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Quick actions: Budget & Wishlist cards
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToBudget)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoGraph, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Budget", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Set monthly budgets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${formatCurrency(budgetUsed)} / ${formatCurrency(budgetAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToWishlist)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wishlist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Manage Wishlist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${wishlistItems.size} ${if (wishlistItems.size == 1) "item" else "items"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Category spending
            if (parentCategorySpending.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Spending by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        val totalSpending = parentCategorySpending.sumOf { it.amount }
                        parentCategorySpending.forEachIndexed { index, pcs ->
                            val percentage = if (totalSpending > 0) ((pcs.amount / totalSpending) * 100).toFloat() else 0f
                            val sliceColor = categoryColor(index)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { onNavigateToCategory(pcs.parentCategory.id) })
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Canvas(modifier = Modifier.size(10.dp)) {
                                                drawCircle(sliceColor)
                                            }
                                            Text(pcs.parentCategory.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        }
                                        Text(formatCurrency(pcs.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { percentage.coerceIn(0f, 100f) / 100f },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(4.dp),
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            color = sliceColor
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "${"%.1f".format(percentage)}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Recent transactions
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onNavigateToTransactions) { Text("View All") }
                }
            }

            items(recent.take(5)) { txn ->
                TransactionRow(txn, categoryMap[txn.categoryId])
            }

            if (recent.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No transactions yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("Tap + to add your first transaction", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun TransactionRow(txn: TransactionEntity, categoryName: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dateStr = txn.date.toLocalDateTime(TimeZone.currentSystemDefault())
        Column(modifier = Modifier.weight(1f)) {
            Text(txn.description ?: "No description", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${dateStr.dayOfMonth}/${dateStr.monthNumber}/${dateStr.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                categoryName?.let { cat ->
                    Text(
                        cat,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            if (txn.type == "INCOME") "+${formatCurrency(txn.amount)}" else "-${formatCurrency(txn.amount)}",
            color = if (txn.type == "INCOME") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
    HorizontalDivider()
}

@Composable
fun formatCurrency(amount: Double): String {
    val currencyCode = LocalCurrencyCode.current
    return NumberFormat.getCurrencyInstance(localeForCurrencyCode(currencyCode)).apply {
        currency = java.util.Currency.getInstance(currencyCode)
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }.format(amount)
}
