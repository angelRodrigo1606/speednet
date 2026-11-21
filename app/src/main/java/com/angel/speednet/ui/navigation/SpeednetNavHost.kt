package com.angel.speednet.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.angel.speednet.ui.history.HistoryScreen
import com.angel.speednet.ui.home.HomeScreen
import com.angel.speednet.ui.results.ResultsScreen

@Composable
fun SpeednetNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onResult = { id ->
                    navController.navigate(Routes.results(id)) { launchSingleTop = true }
                },
                onOpenHistory = {
                    navController.navigate(Routes.HISTORY) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenResult = { id ->
                    navController.navigate(Routes.results(id)) { launchSingleTop = true }
                }
            )
        }

        composable(
            route = Routes.RESULTS,
            arguments = listOf(
                navArgument(Routes.ARG_MEASUREMENT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong(Routes.ARG_MEASUREMENT_ID) ?: -1L
            ResultsScreen(
                measurementId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
