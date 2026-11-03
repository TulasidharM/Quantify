package com.quantify.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.activity.compose.BackHandler
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.BudgetEntity
import com.quantify.app.ui.backup.BackupViewModel
import com.quantify.app.ui.screens.budget.AddBudgetScreen
import com.quantify.app.ui.screens.budget.BudgetScreen
import com.quantify.app.ui.screens.csvimport.CsvImportScreen
import com.quantify.app.ui.screens.dashboard.DashboardScreen
import com.quantify.app.ui.screens.edittransaction.EditTransactionScreen
import com.quantify.app.ui.screens.manage.CategoriesScreen
import com.quantify.app.ui.screens.manage.ManageScreen
import com.quantify.app.ui.screens.stats.StatsScreen
import com.quantify.app.ui.screens.transactions.TransactionsScreen
import com.quantify.app.ui.screens.wishlist.AddWishlistScreen
import com.quantify.app.ui.screens.wishlist.WishlistScreen
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

    // Overlay state for secondary routes (add/edit transaction, csv import, categories, budget, wishlist)
    var showAddTransaction by remember { mutableStateOf(false) }
    var editTransactionId by remember { mutableStateOf<String?>(null) }
    var showCsvImport by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var showAddBudget by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var showWishlist by remember { mutableStateOf(false) }
    var showAddWishlist by remember { mutableStateOf(false) }

    // Activity-scoped ViewModels — scoped to the Activity so Dashboard + Stats share one instance
    val dashboardViewModel: DashboardViewModel = hiltViewModel(activity)
    val transactionViewModel: TransactionViewModel = hiltViewModel(activity)

    // Month name for the PDF filename
    val monthNames = remember {
        listOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
    }
    val selectedYear by transactionViewModel.selectedYearFlow.collectAsState()
    val selectedMonth by transactionViewModel.selectedMonthFlow.collectAsState()
    val monthName = if (selectedMonth in 1..12) monthNames[selectedMonth - 1] else ""

    fun sharePdf() {
        val fileName = "Quantify_${monthName}_${selectedYear}.pdf"
        val file = java.io.File(activity.cacheDir, fileName)
        val year = transactionViewModel.selectedYear
        val month = transactionViewModel.selectedMonth
        backupViewModel.generatePdf(
            transactions = transactionViewModel.transactionsByMonth.value,
            categories = transactionViewModel.categories.value,
            year = year,
            month = month,
            currency = backupViewModel.currency.value,
            file = file
        )
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Quantify - $monthName $year")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(
            Intent.createChooser(shareIntent, "Share PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // Handle back press: close overlays, then navigate to Home, then exit
    BackHandler(enabled = showAddTransaction || editTransactionId != null || showCsvImport || showCategories || showBudget || showAddBudget || showWishlist || showAddWishlist) {
        if (showAddTransaction) {
            showAddTransaction = false
        } else if (editTransactionId != null) {
            editTransactionId = null
            transactionViewModel.clearLoadedTransaction()
        } else if (showCsvImport) {
            showCsvImport = false
        } else if (showCategories) {
            showCategories = false
        } else if (showAddBudget) {
            showAddBudget = false
            editingBudget = null
        } else if (showBudget) {
            showBudget = false
            editingBudget = null
        } else if (showAddWishlist) {
            showAddWishlist = false
        } else if (showWishlist) {
            showWishlist = false
        }
    }

    BackHandler(enabled = pagerState.currentPage != 0 && !showAddTransaction && editTransactionId == null && !showCsvImport && !showCategories && !showBudget && !showAddBudget && !showWishlist && !showAddWishlist) {
        scope.launch { pagerState.scrollToPage(0) }
        transactionViewModel.clearFilterCategory()
    }

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
                            },
                            onSharePdf = { sharePdf() },
                            onAddTransaction = { showAddTransaction = true },
                            onNavigateToBudget = { showBudget = true },
                            onNavigateToWishlist = { showWishlist = true }
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
                            },
                            onSharePdf = { sharePdf() }
                        )
                        2 -> StatsScreen(viewModel = dashboardViewModel)
                        3 -> ManageScreen(
                            transactionViewModel = transactionViewModel,
                            backupViewModel = backupViewModel,
                            onCsvImport = { showCsvImport = true },
                            onCategories = { showCategories = true }
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
                        onClose = {
                            editTransactionId = null
                            transactionViewModel.clearLoadedTransaction()
                        },
                        transactionViewModel = transactionViewModel
                    )
                }
                if (showCsvImport) {
                    CsvImportScreen(onClose = { showCsvImport = false })
                }
                if (showCategories) {
                    CategoriesScreen(
                        viewModel = transactionViewModel,
                        onClose = { showCategories = false }
                    )
                }
                if (showBudget) {
                    BudgetScreen(
                        onClose = { showBudget = false; editingBudget = null },
                        onAddBudget = { editingBudget = null; showAddBudget = true },
                        onEditBudget = { budget -> editingBudget = budget; showAddBudget = true }
                    )
                }
                if (showAddBudget) {
                    AddBudgetScreen(
                        onClose = { showAddBudget = false; editingBudget = null },
                        editBudget = editingBudget
                    )
                }
                if (showWishlist) {
                    WishlistScreen(
                        onClose = { showWishlist = false },
                        onAddItem = { showAddWishlist = true }
                    )
                }
                if (showAddWishlist) {
                    AddWishlistScreen(onClose = { showAddWishlist = false })
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
                            showCategories = false
                            showBudget = false
                            showAddBudget = false
                            editingBudget = null
                            showWishlist = false
                            showAddWishlist = false
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
