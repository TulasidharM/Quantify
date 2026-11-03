package com.quantify.app.ui.screens.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.BudgetEntity
import com.quantify.app.ui.viewmodel.BudgetViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetScreen(
    onClose: () -> Unit,
    editBudget: BudgetEntity? = null,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val topLevelCategories by viewModel.topLevelCategories.collectAsState()

    // Determine category hierarchy for edit mode
    val categoryParentMap = categories.filter { it.parentId != null }.associate { it.id to it.parentId!! }

    var amount by remember { mutableStateOf(editBudget?.amount?.toString() ?: "") }
    var selectedParentId by remember {
        mutableStateOf<String>(
            editBudget?.let { budget ->
                // Find the parent of the edited category, or use the category itself if top-level
                categoryParentMap[budget.categoryId] ?: budget.categoryId
            } ?: ""
        )
    }
    var selectedCategoryId by remember { mutableStateOf(editBudget?.categoryId ?: "") }

    val subCategories = categories.filter { it.parentId == selectedParentId }

    val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    val currentMonth = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber

    val (parentBudgetAmount, siblingSubBudgetTotal) =
        if (selectedParentId.isNotBlank()) viewModel.getParentBudgetInfo(selectedParentId) else Pair(0.0, 0.0)

    val remainingSubBudget = parentBudgetAmount - siblingSubBudgetTotal
    val inputAmount = amount.toDoubleOrNull() ?: 0.0

    // For subcategory: parent must have a budget and input must fit within remaining
    val isSubcategoryBudget = selectedCategoryId != selectedParentId
    val parentConstraintSatisfied = !isSubcategoryBudget || (parentBudgetAmount > 0 && inputAmount <= remainingSubBudget)

    val enabled = amount.isNotBlank() && selectedCategoryId.isNotEmpty() && parentConstraintSatisfied

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editBudget != null) "Edit Budget" else "New Budget") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (enabled) {
                                viewModel.upsertBudget(BudgetEntity(
                                    id = selectedCategoryId,
                                    categoryId = selectedCategoryId,
                                    amount = inputAmount,
                                    year = currentYear,
                                    month = currentMonth
                                ))
                                onClose()
                            }
                        },
                        enabled = enabled
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(20.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Category (horizontal chips)
            Text("Category", modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelLarge)
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                wrapChipsIntoRows(topLevelCategories) { cat ->
                    FilterChip(
                        selected = cat.id == selectedParentId,
                        onClick = {
                            selectedParentId = cat.id
                            selectedCategoryId = cat.id
                        },
                        label = { Text(cat.name) }
                    )
                }
            }
            if (topLevelCategories.isEmpty()) {
                Text("None", modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }

            // Sub-category (horizontal chips, optional)
            if (subCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sub-category (optional)", modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.labelLarge)
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    wrapChipsIntoRows(subCategories) { sub ->
                        FilterChip(
                            selected = sub.id == selectedCategoryId,
                            onClick = { selectedCategoryId = sub.id },
                            label = { Text(sub.name) }
                        )
                    }
                }

                // Show parent constraint info when a subcategory is selected
                if (isSubcategoryBudget && parentBudgetAmount > 0) {
                    Text(
                        "Remaining for sub-categories: ${String.format("%.0f", remainingSubBudget)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                } else if (isSubcategoryBudget && parentBudgetAmount == 0.0) {
                    Text(
                        "Set a budget for the parent category first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() } },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    singleLine = true,
                    isError = isSubcategoryBudget && inputAmount > remainingSubBudget && remainingSubBudget >= 0
                )
            }

            if (isSubcategoryBudget && inputAmount > remainingSubBudget && remainingSubBudget >= 0) {
                Text(
                    "Exceeds remaining sub-category budget (${String.format("%.0f", remainingSubBudget)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun <T> wrapChipsIntoRows(
    items: List<T>,
    content: @Composable (T) -> Unit
) {
    val density = LocalDensity.current
    val screenPx = with(density) {
        LocalContext.current.resources.displayMetrics.widthPixels
    }
    val paddingPx = with(density) { 32.dp.toPx() }
    val spacingPx = with(density) { 8.dp.toPx() }
    val availableWidth = (screenPx - paddingPx).toInt()

    val avgCharCount = if (items.isEmpty()) 5 else items.map { it.toString().length }.average().toInt().coerceIn(2, 30)
    val estimatedChipWidth = (avgCharCount * 7) + 40
    val itemsPerRow = maxOf(1, availableWidth / (estimatedChipWidth + spacingPx.toInt()))

    val rows = items.chunked(itemsPerRow)
    rows.forEachIndexed { rowIndex, rowItems ->
        if (rowIndex > 0) Spacer(modifier = Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { item -> content(item) }
        }
    }
}
