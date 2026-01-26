package com.expensetracker.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.expensetracker.app.ui.accounts.AccountsScreen
import com.expensetracker.app.ui.categories.CategoriesScreen
import com.expensetracker.app.ui.dashboard.DashboardScreen
import com.expensetracker.app.ui.dashboard.DashboardViewModel
import com.expensetracker.app.ui.premium.PremiumScreen
import com.expensetracker.app.ui.reports.MonthlyReportsScreen
import com.expensetracker.app.ui.reports.YearlyReportsScreen
import com.expensetracker.app.ui.settings.SettingsScreen
import com.expensetracker.app.ui.transaction.TransactionScreen

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object MonthlyReports : Screen("monthly_reports")
    data object YearlyReports : Screen("yearly_reports")
    data object AddTransaction : Screen("add_transaction")
    data object EditTransaction : Screen("edit_transaction/{expenseId}") {
        fun createRoute(expenseId: Long) = "edit_transaction/$expenseId"
    }
    data object Categories : Screen("categories")
    data object Accounts : Screen("accounts")
    data object Settings : Screen("settings")
    data object Premium : Screen("premium")
}

@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddTransaction = {
                    navController.navigate(Screen.AddTransaction.route)
                },
                onEditTransaction = { expenseId ->
                    navController.navigate(Screen.EditTransaction.createRoute(expenseId))
                },
                onNavigateToCategories = {
                    navController.navigate(Screen.Categories.route)
                },
                onNavigateToAccounts = {
                    navController.navigate(Screen.Accounts.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Categories.route) {
            CategoriesScreen(
                onNavigateBack = { navController.popBackStack() },
                onShowPremium = { navController.navigate(Screen.Premium.route) }
            )
        }

        composable(Screen.Accounts.route) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val currency by dashboardViewModel.currency.collectAsState()
            AccountsScreen(
                onNavigateBack = { navController.popBackStack() },
                currency = currency
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onShowPremium = { navController.navigate(Screen.Premium.route) }
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
