package com.quantify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.ui.backup.BackupViewModel
import com.quantify.app.ui.screens.csvimport.CsvImportScreen
import com.quantify.app.ui.screens.dashboard.DashboardScreen
import com.quantify.app.ui.screens.edittransaction.EditTransactionScreen
import com.quantify.app.ui.screens.manage.ManageScreen
import com.quantify.app.ui.screens.stats.StatsScreen
import com.quantify.app.ui.screens.transactions.TransactionsScreen
import com.quantify.app.ui.theme.QuantifyTheme
import com.quantify.app.ui.viewmodel.DashboardViewModel
import com.quantify.app.ui.viewmodel.TransactionViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppContent()
        }
    }
}

@Composable
private fun AppContent() {
    val activity = LocalContext.current as ComponentActivity

    // Theme selection — read from BackupViewModel so it persists across process death
    val backupViewModel: BackupViewModel = hiltViewModel(activity)
    val currentTheme by backupViewModel.themeKey.collectAsState()

    QuantifyTheme(themeKey = currentTheme) {
        AppContentInner(backupViewModel = backupViewModel, currentTheme = currentTheme)
    }
}

@Composable
private fun AppContentInner(backupViewModel: BackupViewModel, currentTheme: String) {
    val activity = LocalContext.current as ComponentActivity

    val items = listOf(
        BottomNavItem("Home", Icons.Outlined.Home, Icons.Default.Home),
        BottomNavItem("Transactions", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
        BottomNavItem("Stats", Icons.Outlined.PieChart, Icons.Default.PieChart),
        BottomNavItem("Manage", Icons.Outlined.Settings, Icons.Default.Settings)
    )

    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    // Overlay state for secondary routes (add/edit transaction, csv import)
    var showAddTransaction by remember { mutableStateOf(false) }
    var editTransactionId by remember { mutableStateOf<String?>(null) }
    var showCsvImport by remember { mutableStateOf(false) }

    // Activity-scoped ViewModels — scoped to the Activity so Dashboard + Stats share one instance
    val dashboardViewModel: DashboardViewModel = hiltViewModel(activity)
    val transactionViewModel: TransactionViewModel = hiltViewModel(activity)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pager + overlays confined to weighted space — cannot cover the nav bar
            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            onNavigateToTransactions = { scope.launch { pagerState.scrollToPage(1) } },
                            onNavigateToCategory = { categoryId ->
                                transactionViewModel.setFilterCategoryId(categoryId)
                                scope.launch { pagerState.scrollToPage(1) }
                            }
                        )
                        1 -> TransactionsScreen(
                            viewModel = transactionViewModel,
                            onAddTransaction = {
                                showAddTransaction = true
                                scope.launch { pagerState.scrollToPage(1) }
                            },
                            onEditTransaction = { id ->
                                editTransactionId = id
                                scope.launch { pagerState.scrollToPage(1) }
                            },
                            onBack = {
                                transactionViewModel.clearFilterCategory()
                                scope.launch { pagerState.scrollToPage(0) }
                            }
                        )
                        2 -> StatsScreen(viewModel = dashboardViewModel)
                        3 -> ManageScreen(
                            transactionViewModel = transactionViewModel,
                            backupViewModel = backupViewModel,
                            onCsvImport = { showCsvImport = true }
                        )
                    }
                }

                // Overlay screens — drawn on top of pager, confined to weighted Box
                if (showAddTransaction) {
                    EditTransactionScreen(
                        onClose = { showAddTransaction = false },
                        transactionViewModel = transactionViewModel
                    )
                }
                if (editTransactionId != null) {
                    EditTransactionScreen(
                        transactionId = editTransactionId,
                        onClose = { editTransactionId = null },
                        transactionViewModel = transactionViewModel
                    )
                }
                if (showCsvImport) {
                    CsvImportScreen(onClose = { showCsvImport = false })
                }
            }

            // NavigationBar as sibling — always receives touches
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                items.forEachIndexed { index, item ->
                    val isSelected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            showAddTransaction = false
                            editTransactionId = null
                            showCsvImport = false
                            if (index == 1) transactionViewModel.clearFilterCategory()
                            scope.launch { pagerState.scrollToPage(index) }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    }
}
