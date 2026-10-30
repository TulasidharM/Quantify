package com.quantify.app.ui.screens.recurring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.RecurringRuleEntity
import com.quantify.app.ui.screens.dashboard.formatCurrency
import com.quantify.app.ui.viewmodel.RecurringRuleViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    viewModel: RecurringRuleViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsState()
    var showNewDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Recurring Transactions") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) { Text("+") }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (rules.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("No recurring rules", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(rules) { rule ->
                    Card(modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(rule.description ?: "No description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(formatCurrency(rule.amount), style = MaterialTheme.typography.bodyMedium, color = if (rule.type == "INCOME") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                            }
                            Text("${rule.frequency} ${if (rule.isActive) "• Active" else "• Paused"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            val nextDate = rule.nextRunDate.toLocalDateTime(TimeZone.currentSystemDefault())
                            Text("Next: ${nextDate.dayOfMonth}/${nextDate.monthNumber}/${nextDate.year}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = {
                                    viewModel.updateRule(rule.copy(isActive = !rule.isActive))
                                }) {
                                    Text(if (rule.isActive) "Pause" else "Resume")
                                }
                                TextButton(onClick = { viewModel.deleteRule(rule) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        NewRecurringRuleDialog(onDismiss = { showNewDialog = false }, onSave = { viewModel.addRule(it) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecurringRuleDialog(
    onDismiss: () -> Unit,
    onSave: (RecurringRuleEntity) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var type by remember { mutableStateOf("EXPENSE") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Recurring Rule") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("Expense") })
                    FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("Income") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = frequency, onValueChange = {}, modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text("Frequency") }, readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { f ->
                            DropdownMenuItem(text = { Text(f) }, onClick = { frequency = f; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (amount.isNotBlank()) {
                    onSave(RecurringRuleEntity(
                        id = "${Clock.System.now().epochSeconds}_${System.nanoTime()}",
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        type = type,
                        categoryId = "default",
                        paymentMethodId = null,
                        description = description,
                        frequency = frequency,
                        dayOfMonth = 1,
                        nextRunDateEpochSeconds = Clock.System.now().epochSeconds
                    ))
                    onDismiss()
                }
            }, enabled = amount.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
