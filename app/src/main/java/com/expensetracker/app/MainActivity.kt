package com.expensetracker.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.animation.doOnEnd
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.app.data.preferences.PreferencesManager
import com.expensetracker.app.navigation.NavGraph
import com.expensetracker.app.navigation.Screen
import com.expensetracker.app.ui.theme.ExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Modern exit animation: icon zooms up and fades while background slides away
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = splashScreenView.iconView

            // Icon zoom up animation (creates "diving into app" effect)
            val iconScaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.5f)
            val iconScaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.5f)
            val iconFade = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)

            // Background slide up and fade
            val backgroundSlide = ObjectAnimator.ofFloat(
                splashScreenView.view,
                View.TRANSLATION_Y,
                0f,
                -splashScreenView.view.height.toFloat() * 0.3f
            )
            val backgroundFade = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)

            // Icon animation set
            val iconAnimator = AnimatorSet().apply {
                playTogether(iconScaleX, iconScaleY, iconFade)
                duration = 400L
                interpolator = DecelerateInterpolator(1.5f)
            }

            // Background animation set
            val backgroundAnimator = AnimatorSet().apply {
                playTogether(backgroundSlide, backgroundFade)
                duration = 350L
                startDelay = 100L
                interpolator = AnticipateOvershootInterpolator(0.5f)
            }

            // Play all animations
            AnimatorSet().apply {
                playTogether(iconAnimator, backgroundAnimator)
                doOnEnd { splashScreenView.remove() }
                start()
            }
        }

        enableEdgeToEdge()

        setContent {
            val isDarkMode by preferencesManager.isDarkMode.collectAsState(initial = true)

            ExpenseTrackerTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val bottomNavItems = listOf(
                    BottomNavItem("Transactions", Icons.Default.Home, Screen.Dashboard.route),
                    BottomNavItem("Monthly", Icons.Default.Assessment, Screen.MonthlyReports.route),
                    BottomNavItem("Yearly", Icons.Default.CalendarMonth, Screen.YearlyReports.route)
                )

                // Only show bottom nav on main screens
                val showBottomNav = currentDestination?.route in bottomNavItems.map { it.route }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomNav) {
                            Column(modifier = Modifier.navigationBarsPadding()) {
                                NavigationBar(
                                    modifier = Modifier.height(56.dp),
                                    windowInsets = WindowInsets(0, 0, 0, 0),
                                    tonalElevation = 0.dp
                                ) {
                                    bottomNavItems.forEach { item ->
                                        NavigationBarItem(
                                            icon = { Icon(item.icon, contentDescription = item.label) },
                                            label = { Text(item.label) },
                                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                                            onClick = {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { _ ->
                    NavGraph(
                        navController = navController,
                        preferencesManager = preferencesManager,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
