package com.quantify.app.ui.screens.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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
    onAddBudget: () -> Unit = {},
    onAddSubcategoryBudget: (String) -> Unit = {},
    onEditBudget: (BudgetEntity) -> Unit = {},
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val budgets by viewModel.budgets.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val currentYear = currentDate.year
    val monthLabel = currentDate.month.name.lowercase()
        .replaceFirstChar { it.uppercase() }

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

    val summaryProgressColor = when {
        overallOverBudget -> MaterialTheme.colorScheme.error
        overallRatio >= 0.8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val summaryTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text("$monthLabel $currentYear", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp))
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (budgets.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Savings, contentDescription = null,
                            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Give your spending a plan", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Set a monthly limit for a category and track how much you have left.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onAddBudget) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create a budget")
                        }
                    }
                }
            } else {
                // Summary cards
                item("summary") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        // Overall progress card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "MONTHLY PLAN",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(formatCurrency(totalBudget),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("budgeted", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val trackW = size.width
                                        val trackH = size.height
                                        val progressW = trackW * overallRatio.toFloat()
                                        val radius = CornerRadius(trackH / 2f, trackH / 2f)
                                        drawRoundRect(
                                            color = summaryTrackColor,
                                            size = Size(trackW, trackH),
                                            cornerRadius = radius
                                        )
                                        if (progressW > 0) {
                                            drawRoundRect(
                                                color = summaryProgressColor,
                                                size = Size(progressW, trackH),
                                                cornerRadius = radius
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${String.format("%.0f", if (totalBudget > 0) totalSpent / totalBudget * 100 else 0.0)}% used",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        if (overallOverBudget) "${formatCurrency(-totalRemaining)} over plan" else "${formatCurrency(totalRemaining)} left",
                                        style = MaterialTheme.typography.bodySmall,
                                            color = if (overallOverBudget) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Spent and Remaining cards (side by side)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Spent card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.ShoppingCartCheckout,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Spent",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        formatCurrency(totalSpent),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Remaining card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.Savings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Remaining",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        formatCurrency(totalRemaining),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (totalRemaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                item("budget-section-title") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Category budgets", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${budgets.size} ${if (budgets.size == 1) "category" else "categories"}",
                            style = MaterialTheme.typography.labelMedium,
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
private fun OrphanBudgetCard(
    budgetWithSpending: BudgetRepository.BudgetWithSpending,
    categoryName: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val ratio = (budgetWithSpending.spent / budgetWithSpending.budget.amount).coerceIn(0.0, 1.0)
    val overBudget = budgetWithSpending.spent > budgetWithSpending.budget.amount
    val progressColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        ratio >= 0.8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatCurrency(budgetWithSpending.spent)} / ${formatCurrency(budgetWithSpending.budget.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val trackW = size.width
                    val trackH = size.height
                    val progressW = trackW * ratio.toFloat()
                    val radius = CornerRadius(trackH / 2f, trackH / 2f)
                    drawRoundRect(
                        color = trackColor,
                        size = Size(trackW, trackH),
                        cornerRadius = radius
                    )
                    if (progressW > 0) {
                        drawRoundRect(
                            color = progressColor,
                            size = Size(progressW, trackH),
                            cornerRadius = radius
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (overBudget) "Over budget!"
                else "${String.format("%.0f", ratio * 100)}% used",
                style = MaterialTheme.typography.bodySmall,
                color = if (overBudget) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun BudgetCard(
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
    val ratio = (parentBudget.spent / parentBudget.budget.amount).coerceIn(0.0, 1.0)
    val overBudget = parentBudget.spent > parentBudget.budget.amount
    val progressColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        ratio >= 0.8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEditParent
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Parent header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canAddSubcategoryBudget) {
                        IconButton(onClick = onAddSubcategoryBudget, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Add,
                                contentDescription = "Add subcategory budget for $categoryName",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onDeleteParent, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete $categoryName budget",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp))
                    }
                }
            }

            Text(
                "${formatCurrency(parentBudget.spent)} / ${formatCurrency(parentBudget.budget.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar with rounded caps
            Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val trackW = size.width
                    val trackH = size.height
                    val progressW = (trackW * ratio.toFloat())
                    val radius = CornerRadius(trackH / 2f, trackH / 2f)

                    drawRoundRect(
                        color = trackColor,
                        size = Size(trackW, trackH),
                        cornerRadius = radius
                    )

                    if (progressW > 0) {
                        drawRoundRect(
                            color = progressColor,
                            size = Size(progressW, trackH),
                            cornerRadius = radius
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage text
            Text(
                if (overBudget) "Over budget!"
                else "${String.format("%.0f", ratio * 100)}% used",
                style = MaterialTheme.typography.bodySmall,
                color = if (overBudget) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End)
            )

            // Child budget rows
            if (childBudgets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                childBudgets.forEachIndexed { index, (cat, childWs) ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
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

@Composable
private fun SubBudgetRow(
    budgetWithSpending: BudgetRepository.BudgetWithSpending,
    categoryName: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val ratio = (budgetWithSpending.spent / budgetWithSpending.budget.amount).coerceIn(0.0, 1.0)
    val overBudget = budgetWithSpending.spent > budgetWithSpending.budget.amount
    val progressColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        ratio >= 0.8 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                "${formatCurrency(budgetWithSpending.spent)} / ${formatCurrency(budgetWithSpending.budget.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Thin progress bar
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val trackW = size.width
                    val trackH = size.height
                    val progressW = trackW * ratio.toFloat()
                    val radius = CornerRadius(trackH / 2f, trackH / 2f)

                    drawRoundRect(
                        color = trackColor,
                        size = Size(trackW, trackH),
                        cornerRadius = radius
                    )
                    if (progressW > 0) {
                        drawRoundRect(
                            color = progressColor,
                            size = Size(progressW, trackH),
                            cornerRadius = radius
                        )
                    }
                }
            }
        }
    }
}
