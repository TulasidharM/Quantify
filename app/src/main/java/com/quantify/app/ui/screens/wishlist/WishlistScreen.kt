package com.quantify.app.ui.screens.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.WishlistItemEntity
import com.quantify.app.ui.screens.dashboard.formatCurrency
import com.quantify.app.ui.viewmodel.WishlistViewModel
import kotlinx.datetime.Month

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    onClose: () -> Unit,
    onAddItem: () -> Unit = {},
    monthlyBudgetedTotal: Double = 0.0,
    selectedYear: Int = 0,
    selectedMonth: Int = 0,
    viewModel: WishlistViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    var selectedItemIds by remember(items) { mutableStateOf(items.map { it.id }.toSet()) }
    val selectedWishlistTotal = items.filter { it.id in selectedItemIds }.sumOf { it.targetAmount }
    val overallTotal = monthlyBudgetedTotal + selectedWishlistTotal
    val periodLabel = if (selectedYear > 0 && selectedMonth in 1..12) {
        "${Month.values()[selectedMonth - 1].name.lowercase().replaceFirstChar { it.uppercase() }} $selectedYear"
    } else "This month"
    var itemToDelete by remember { mutableStateOf<WishlistItemEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wishlist") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddItem) {
                        Icon(Icons.Default.Add, contentDescription = "Add wishlist item")
                    }
                }
            )
        },
        floatingActionButton = {
            if (items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddItem,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add item") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WishlistSummary(
                    periodLabel = periodLabel,
                    monthlyBudgetedTotal = monthlyBudgetedTotal,
                    selectedWishlistTotal = selectedWishlistTotal,
                    overallTotal = overallTotal,
                    selectedCount = selectedItemIds.size
                )
            }
            if (items.isEmpty()) {
                item {
                    EmptyWishlist(onAddItem = onAddItem)
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Saved for later", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${items.size} ${if (items.size == 1) "item" else "items"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(items, key = { it.id }) { item ->
                    WishlistItemCard(
                        item = item,
                        selected = item.id in selectedItemIds,
                        onToggle = { checked ->
                            selectedItemIds = if (checked) selectedItemIds + item.id
                            else selectedItemIds - item.id
                        },
                        onDelete = { itemToDelete = item }
                    )
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Remove from wishlist?") },
            text = { Text("${item.name} will be removed from your wishlist.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item)
                    itemToDelete = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun WishlistSummary(
    periodLabel: String,
    monthlyBudgetedTotal: Double,
    selectedWishlistTotal: Double,
    overallTotal: Double,
    selectedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(periodLabel.uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            WishlistSummaryRow(label = "Budgeted", amount = formatCurrency(monthlyBudgetedTotal))
            Spacer(Modifier.height(10.dp))
            WishlistSummaryRow(
                label = "Wishlist selected",
                detail = "$selectedCount ${if (selectedCount == 1) "item" else "items"}",
                amount = formatCurrency(selectedWishlistTotal)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            WishlistSummaryRow(label = "Overall total", amount = formatCurrency(overallTotal))
        }
    }
}

@Composable
private fun WishlistSummaryRow(label: String, amount: String, detail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
            detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(amount, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun WishlistItemCard(
    item: WishlistItemEntity,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                IconButton(onClick = { onToggle(!selected) }, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (selected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (selected) "Exclude ${item.name} from calculation" else "Include ${item.name} in calculation",
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text("Target price", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatCurrency(item.targetAmount), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyWishlist(onAddItem: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null,
                    modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Keep the things you want in one place", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Add an item and its target price to see what your wishlist would cost.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAddItem) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add your first item")
        }
    }
}
