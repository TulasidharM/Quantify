package com.quantify.app.ui.screens.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.BudgetEntity
import com.quantify.app.data.repository.BudgetRepository
import com.quantify.app.ui.screens.dashboard.formatCurrency
import com.quantify.app.ui.viewmodel.BudgetViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val budgets by viewModel.budgets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showEditDialog by remember { mutableStateOf<BudgetEntity?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }

    val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    val currentMonth = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                actions = { Text("$currentYear / $currentMonth", style = MaterialTheme.typography.bodySmall) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (budgets.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("No budgets set", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(budgets) { bs ->
                    Card(modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val categoryName = categories.find { it.id == bs.budget.categoryId }?.name ?: bs.budget.categoryId
                                Text(categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${formatCurrency(bs.spent)} / ${formatCurrency(bs.budget.amount)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = (bs.spent / bs.budget.amount).toFloat().coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                                color = if (bs.spent > bs.budget.amount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (bs.spent > bs.budget.amount) "Over budget!" else "${String.format("%.0f", (bs.spent / bs.budget.amount) * 100)}% used",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bs.spent > bs.budget.amount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        var amount by remember { mutableStateOf("") }
        var categoryId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("New Budget") },
            text = {
                Column {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(categories) { cat ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                RadioButton(selected = cat.id == categoryId, onClick = { categoryId = cat.id })
                                Text(cat.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (amount.isNotBlank() && categoryId.isNotEmpty()) {
                        viewModel.upsertBudget(BudgetEntity(
                            id = categoryId,
                            categoryId = categoryId,
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            year = currentYear,
                            month = currentMonth
                        ))
                        showNewDialog = false
                    }
                }, enabled = amount.isNotBlank() && categoryId.isNotEmpty()) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("Cancel") } }
        )
    }
}
