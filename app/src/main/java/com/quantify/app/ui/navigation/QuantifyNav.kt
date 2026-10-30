package com.quantify.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.quantify.app.ui.screens.budget.BudgetScreen
import com.quantify.app.ui.screens.csvimport.CsvImportScreen
import com.quantify.app.ui.screens.edittransaction.EditTransactionScreen
import com.quantify.app.ui.screens.recurring.RecurringScreen
import com.quantify.app.ui.screens.transactions.TransactionsScreen
import com.quantify.app.ui.viewmodel.TransactionViewModel

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Transactions : Screen("transactions")
    object TransactionsByCategory : Screen("transactions/{categoryId}") {
        fun createRoute(categoryId: String) = "transactions/$categoryId"
    }
    object Stats : Screen("stats")
    object Budgets : Screen("budgets")
    object Recurring : Screen("recurring")
    object Manage : Screen("manage")
    object CsvImport : Screen("csv_import")
    object AddTransaction : Screen("add_transaction")
    object EditTransaction : Screen("edit_transaction/{transactionId}") {
        fun createRoute(id: String) = "edit_transaction/$id"
    }
}

/**
 * Secondary NavHost for non-bottom-tab routes: add/edit transaction, csv import,
 * transactions by category, budgets, recurring.
 *
 * The 4 bottom tabs (Dashboard, Transactions, Stats, Manage) are handled by
 * HorizontalPager in MainActivity — they are NOT in this NavHost.
 */
@Composable
fun QuantifyNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    transactionViewModel: TransactionViewModel? = null
) {
    NavHost(
        navController = navController,
        startDestination = "empty_start",
        modifier = modifier
    ) {
        composable("empty_start") { /* no-op — start is empty, tabs are in HorizontalPager */ }
        composable(
            Screen.TransactionsByCategory.route,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
            TransactionsScreen(
                viewModel = transactionViewModel ?: error("transactionViewModel required for TransactionsByCategory"),
                onAddTransaction = { navController.navigate(Screen.AddTransaction.route) },
                onEditTransaction = { id -> navController.navigate(Screen.EditTransaction.createRoute(id)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Budgets.route) { BudgetScreen() }
        composable(Screen.Recurring.route) { RecurringScreen() }
        composable(Screen.CsvImport.route) { CsvImportScreen(onClose = { navController.popBackStack() }) }
        composable(Screen.AddTransaction.route) {
            EditTransactionScreen(
                onClose = { navController.popBackStack() },
                transactionViewModel = transactionViewModel
            )
        }
        composable(
            Screen.EditTransaction.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("transactionId") ?: ""
            EditTransactionScreen(
                transactionId = id,
                onClose = { navController.popBackStack() },
                transactionViewModel = transactionViewModel
            )
        }
    }
}
