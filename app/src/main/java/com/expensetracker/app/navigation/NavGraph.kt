package com.expensetracker.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.expensetracker.app.data.preferences.PreferencesManager
import com.expensetracker.app.ui.accounts.AccountsScreen
import com.expensetracker.app.ui.categories.CategoriesScreen
import com.expensetracker.app.ui.dashboard.DashboardScreen
import com.expensetracker.app.ui.dashboard.DashboardViewModel
import com.expensetracker.app.ui.premium.PremiumScreen
import com.expensetracker.app.ui.reports.MonthlyReportsScreen
import com.expensetracker.app.ui.reports.YearlyReportsScreen
import com.expensetracker.app.ui.settings.SettingsScreen
import com.expensetracker.app.ui.transaction.TransactionDetailScreen
import com.expensetracker.app.ui.transaction.TransactionScreen

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object MonthlyReports : Screen("monthly_reports")
    data object YearlyReports : Screen("yearly_reports")
    data object AddTransaction : Screen("add_transaction")
    data object EditTransaction : Screen("edit_transaction/{expenseId}") {
        fun createRoute(expenseId: Long) = "edit_transaction/$expenseId"
    }
    data object TransactionDetail : Screen("transaction_detail/{expenseId}") {
        fun createRoute(expenseId: Long) = "transaction_detail/$expenseId"
    }
    data object Categories : Screen("categories")
    data object Accounts : Screen("accounts")
    data object Settings : Screen("settings")
    data object Premium : Screen("premium")
    data object CopyTransaction : Screen("copy_transaction/{expenseId}/{useToday}") {
        fun createRoute(expenseId: Long, useToday: Boolean) = "copy_transaction/$expenseId/$useToday"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddTransaction = {
                    navController.navigate(Screen.AddTransaction.route)
                },
                onViewTransaction = { expenseId ->
                    navController.navigate(Screen.EditTransaction.createRoute(expenseId))
                }
            )
        }

        composable(Screen.AddTransaction.route) {
            TransactionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditTransaction.route,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.LongType }
            )
        ) {
            TransactionScreen(
                onNavigateBack = { navController.popBackStack() },
                onCopyTransaction = { expenseId, useToday ->
                    navController.navigate(Screen.CopyTransaction.createRoute(expenseId, useToday))
                }
            )
        }

        composable(
            route = Screen.CopyTransaction.route,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.LongType },
                navArgument("useToday") { type = NavType.BoolType }
            )
        ) {
            TransactionScreen(
                onNavigateBack = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
            )
        }

        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: -1L
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val currency by dashboardViewModel.currency.collectAsState()
            TransactionDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditTransaction = {
                    navController.navigate(Screen.EditTransaction.createRoute(expenseId))
                },
                currency = currency
            )
        }

        composable(Screen.Categories.route) {
            CategoriesScreen(
                onShowPremium = { navController.navigate(Screen.Premium.route) },
                preferencesManager = preferencesManager
            )
        }

        composable(Screen.Accounts.route) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val currency by dashboardViewModel.currency.collectAsState()
            AccountsScreen(
                currency = currency,
                preferencesManager = preferencesManager
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onShowPremium = { navController.navigate(Screen.Premium.route) },
                preferencesManager = preferencesManager
            )
        }

        composable(Screen.Premium.route) {
            PremiumScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MonthlyReports.route) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val currency by dashboardViewModel.currency.collectAsState()
            MonthlyReportsScreen(currency = currency)
        }

        composable(Screen.YearlyReports.route) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val currency by dashboardViewModel.currency.collectAsState()
            YearlyReportsScreen(currency = currency)
        }
    }
}
