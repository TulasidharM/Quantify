package com.quantify.app.ui.screens.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.BudgetEntity
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.repository.BudgetRepository
import com.quantify.app.ui.screens.dashboard.formatCurrency
import com.quantify.app.ui.viewmodel.BudgetViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onClose: () -> Unit = {},
    selectedYear: Int = 0,
    selectedMonth: Int = 0,
    onAddBudget: () -> Unit = {},
    onAddSubcategoryBudget: (String) -> Unit = {},
    onEditBudget: (BudgetEntity) -> Unit = {},
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val currentPeriod = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val periodYear = selectedYear.takeIf { it > 0 } ?: currentPeriod.year
    val periodMonth = selectedMonth.takeIf { it in 1..12 } ?: currentPeriod.monthNumber
    val budgetsFlow = remember(viewModel, periodYear, periodMonth) {
        viewModel.budgetsForPeriod(periodYear, periodMonth)
    }
    val budgets by budgetsFlow.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState()

    val topLevelCategories = categories.filter { it.parentId == null }

    // Group budgets by parent category
    val budgetMap = budgets.associateBy { it.budget.categoryId }
    val subCategoryIds = categories.filter { it.parentId != null }.associate { it.id to it.parentId!! }

    // Parent budget entries (categories that have a budget)
    val parentBudgetEntries = topLevelCategories
        .filter { budgetMap.containsKey(it.id) }
        .sortedBy { it.name }

    // Orphan subcategory budgets (subcategory has budget but parent doesn't)
    val orphanSubBudgets = budgets.filter { bs ->
        val parentId = subCategoryIds[bs.budget.categoryId]
        parentId != null && !budgetMap.containsKey(parentId)
    }

    // Summary totals
    // A parent's spending already includes its subcategories, so count either the parent
    // budget or standalone subcategory budgets in the overall totals, never both.
    val summaryBudgets = parentBudgetEntries.map { budgetMap.getValue(it.id) } + orphanSubBudgets
    val totalBudget = summaryBudgets.sumOf { it.budget.amount }
    val totalSpent = summaryBudgets.sumOf { it.spent }
    val totalRemaining = totalBudget - totalSpent
    val overallRatio = if (totalBudget > 0) (totalSpent / totalBudget).coerceIn(0.0, 1.0) else 0.0
    val overallOverBudget = totalSpent > totalBudget
    val monthLabel = remember(periodYear, periodMonth) {
        java.time.Month.of(periodMonth).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Budgets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$monthLabel $periodYear · Limits repeat monthly",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBudget) {
                Icon(Icons.Default.Add, contentDescription = "Add budget")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(top = 10.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (budgets.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 42.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Savings, contentDescription = null,
                                        modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text("Give your spending a plan", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text("Set category limits once. They’ll apply every month, and you can track spending here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(22.dp))
                            Button(onClick = onAddBudget) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create a budget")
                            }
                        }
                    }
                }
            } else {
                item("summary") {
                    BudgetOverviewCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        totalBudget = totalBudget,
                        totalSpent = totalSpent,
                        totalRemaining = totalRemaining,
                        ratio = overallRatio,
                        isOverBudget = overallOverBudget
                    )
                }

                item("budget-section-title") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Category limits", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${budgets.size} ${if (budgets.size == 1) "limit" else "limits"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Parent budget entries
                parentBudgetEntries.forEach { parentCat ->
                    val parentBudgetWs = budgetMap[parentCat.id]!!
                    val childIds = categories.filter { it.parentId == parentCat.id }.map { it.id }
                    val childBudgetsWs = budgets.filter { bs -> bs.budget.categoryId in childIds }
                        .sortedBy { bs -> categories.find { c -> c.id == bs.budget.categoryId }?.name ?: "" }

                    item("parent-${parentCat.id}") {
                        val childList: List<Pair<CategoryEntity, BudgetRepository.BudgetWithSpending>> =
                            childBudgetsWs.mapNotNull { childWs ->
                                categories.find { c -> c.id == childWs.budget.categoryId }?.let { cat ->
                                    cat to childWs
                                }
                            }
                        BudgetCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            parentBudget = parentBudgetWs,
                            categoryName = parentCat.name,
                            childBudgets = childList,
                            canAddSubcategoryBudget = categories.any { it.parentId == parentCat.id },
                            onAddSubcategoryBudget = { onAddSubcategoryBudget(parentCat.id) },
                            onDeleteParent = { viewModel.deleteBudget(parentBudgetWs.budget) },
                            onDeleteChild = { budget: BudgetEntity -> viewModel.deleteBudget(budget) },
                            onEditParent = { onEditBudget(parentBudgetWs.budget) },
                            onEditChild = { budget: BudgetEntity -> onEditBudget(budget) }
                        )
                    }
                }

                // Orphan subcategory budgets
                orphanSubBudgets.forEach { orphanWs ->
                    item("orphan-${orphanWs.budget.categoryId}") {
                        OrphanBudgetCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            budgetWithSpending = orphanWs,
                            categoryName = categories.find { it.id == orphanWs.budget.categoryId }?.name ?: orphanWs.budget.categoryId,
                            onDelete = { viewModel.deleteBudget(orphanWs.budget) },
                            onEdit = { onEditBudget(orphanWs.budget) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetOverviewCard(
    modifier: Modifier = Modifier,
    totalBudget: Double,
    totalSpent: Double,
    totalRemaining: Double,
    ratio: Double,
    isOverBudget: Boolean
) {
    val progressColor = when {
        isOverBudget -> MaterialTheme.colorScheme.error
        ratio >= 0.8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Savings, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Monthly plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Your combined category limits", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("REPEATS MONTHLY", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(formatCurrency(totalBudget), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text("total budgeted", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(18.dp))
            BudgetProgressBar(ratio = ratio, color = progressColor, height = 10.dp)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${String.format(java.util.Locale.getDefault(), "%.0f", ratio * 100)}% used",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (isOverBudget) "${formatCurrency(-totalRemaining)} over limit" else "${formatCurrency(totalRemaining)} left",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BudgetSummaryValue("Spent", totalSpent, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(34.dp).background(MaterialTheme.colorScheme.outlineVariant))
                BudgetSummaryValue("Remaining", totalRemaining,
                    if (totalRemaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f).padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun BudgetSummaryValue(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(formatCurrency(amount), style = MaterialTheme.typography.titleMedium, color = color,
            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun BudgetProgressBar(ratio: Double, color: Color, height: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(
        Modifier.fillMaxWidth().height(height).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(Modifier.fillMaxWidth(ratio.toFloat().coerceIn(0f, 1f)).fillMaxHeight()
            .clip(CircleShape).background(color))
    }
}

@Composable
private fun BudgetStatus(ratio: Double, spent: Double, limit: Double) {
    val overBy = spent - limit
    Text(
        if (overBy > 0) "Over by ${formatCurrency(overBy)}"
        else "${String.format(java.util.Locale.getDefault(), "%.0f", ratio * 100)}% used",
        style = MaterialTheme.typography.labelSmall,
        color = if (overBy > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (overBy > 0) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Composable
private fun CategoryMark(name: String) {
    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BudgetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, danger: Boolean = false) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(36.dp).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp),
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OrphanBudgetCard(
    modifier: Modifier = Modifier,
    budgetWithSpending: BudgetRepository.BudgetWithSpending,
    categoryName: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val spent = budgetWithSpending.spent
    val limit = budgetWithSpending.budget.amount
    val ratio = if (limit > 0) spent / limit else 0.0
    val overBudget = spent > limit
    val progressColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        ratio >= 0.8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit
        ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(categoryName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Standalone category limit", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BudgetAction(Icons.Default.Edit, "Edit $categoryName budget", onEdit)
                Spacer(Modifier.width(8.dp))
                BudgetAction(Icons.Outlined.DeleteOutline, "Delete $categoryName budget", onDelete, danger = true)
            }
            Spacer(Modifier.height(18.dp))
            BudgetAmountRow(spent, limit)
            Spacer(Modifier.height(9.dp))
            BudgetProgressBar(ratio, progressColor)
            Spacer(Modifier.height(7.dp))
            BudgetStatus(ratio, spent, limit)
        }
    }
}

@Composable
private fun BudgetAmountRow(spent: Double, limit: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column {
            Text("SPENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCurrency(spent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (spent > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, softWrap = false)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("LIMIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCurrency(limit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun BudgetCard(
    modifier: Modifier = Modifier,
    parentBudget: BudgetRepository.BudgetWithSpending,
    categoryName: String,
    childBudgets: List<Pair<CategoryEntity, BudgetRepository.BudgetWithSpending>>,
    canAddSubcategoryBudget: Boolean,
    onAddSubcategoryBudget: () -> Unit,
    onDeleteParent: () -> Unit,
    onDeleteChild: (BudgetEntity) -> Unit,
    onEditParent: () -> Unit,
    onEditChild: (BudgetEntity) -> Unit,
) {
    val spent = parentBudget.spent
    val limit = parentBudget.budget.amount
    val ratio = if (limit > 0) spent / limit else 0.0
    val overBudget = spent > limit
    val progressColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        ratio >= 0.8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEditParent
        ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(categoryName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (childBudgets.isEmpty()) "Category limit" else "${childBudgets.size} subcategory ${if (childBudgets.size == 1) "limit" else "limits"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canAddSubcategoryBudget) {
                    BudgetAction(Icons.Default.Add, "Add subcategory budget for $categoryName", onAddSubcategoryBudget)
                    Spacer(Modifier.width(8.dp))
                }
                BudgetAction(Icons.Outlined.DeleteOutline, "Delete $categoryName budget", onDeleteParent, danger = true)
            }

            Spacer(Modifier.height(18.dp))
            BudgetAmountRow(spent, limit)
            Spacer(Modifier.height(9.dp))
            BudgetProgressBar(ratio, progressColor)
            Spacer(Modifier.height(7.dp))
            BudgetStatus(ratio, spent, limit)

            if (childBudgets.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    childBudgets.forEach { (cat, childWs) ->
                        SubBudgetRow(
                            budgetWithSpending = childWs,
                            categoryName = cat.name,
                            onDelete = { onDeleteChild(childWs.budget) },
                            onEdit = { onEditChild(childWs.budget) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubBudgetRow(
    budgetWithSpending: BudgetRepository.BudgetWithSpending,
    categoryName: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val spent = budgetWithSpending.spent
    val limit = budgetWithSpending.budget.amount
    val ratio = if (limit > 0) spent / limit else 0.0
    val overBudget = spent > limit
    val progressColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        ratio >= 0.8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit
        ),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatCurrency(spent)} / ${formatCurrency(limit)}", style = MaterialTheme.typography.bodySmall,
                        color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, softWrap = false)
                }
                BudgetAction(Icons.Default.Edit, "Edit $categoryName budget", onEdit)
                Spacer(Modifier.width(8.dp))
                BudgetAction(Icons.Default.Close, "Delete $categoryName budget", onDelete, danger = true)
            }
            Spacer(Modifier.height(9.dp))
            BudgetProgressBar(ratio, progressColor, height = 5.dp)
            Spacer(Modifier.height(6.dp))
            BudgetStatus(ratio, spent, limit)
        }
    }
}
