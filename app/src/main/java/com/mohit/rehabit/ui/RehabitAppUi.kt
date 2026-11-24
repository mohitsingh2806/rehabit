package com.mohit.rehabit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mohit.rehabit.ui.library.ExerciseDetailScreen
import com.mohit.rehabit.ui.library.LibraryScreen
import com.mohit.rehabit.ui.library.PlanDetailScreen
import com.mohit.rehabit.ui.player.PlayerRequest
import com.mohit.rehabit.ui.player.PlayerScreen
import com.mohit.rehabit.ui.progress.ProgressScreen
import com.mohit.rehabit.ui.settings.SettingsScreen
import com.mohit.rehabit.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Home),
    LIBRARY("library", "Library", Icons.AutoMirrored.Filled.List),
    PROGRESS("progress", "Progress", Icons.Filled.DateRange),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

private fun NavHostController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun RehabitAppUi() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val openPlan: (String) -> Unit = { id -> nav.navigate("plan/$id") }
    val openExercise: (String) -> Unit = { id -> nav.navigate("exercise/$id") }
    val startPhase: (String, String) -> Unit = { planId, phaseId -> nav.navigate("player/plan/$planId/$phaseId") }

    Scaffold(
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Tab.TODAY.route) {
                TodayScreen(
                    onOpenPlan = openPlan,
                    onStartRoutine = startPhase,
                    onBrowseLibrary = { nav.openTab(Tab.LIBRARY) },
                )
            }
            composable(Tab.LIBRARY.route) {
                LibraryScreen(
                    onOpenExercise = openExercise,
                    onOpenPlan = openPlan,
                    onShowProblems = { nav.openTab(Tab.SETTINGS) },
                )
            }
            composable(Tab.PROGRESS.route) { ProgressScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen() }
            composable(
                route = "exercise/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                ExerciseDetailScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenExercise = openExercise,
                    onTry = { id -> nav.navigate("player/exercise/$id") },
                )
            }
            composable(
                route = "plan/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                PlanDetailScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenExercise = openExercise,
                    onStart = startPhase,
                )
            }
            composable(
                route = "player/plan/{planId}/{phaseId}",
                arguments = listOf(
                    navArgument("planId") { type = NavType.StringType },
                    navArgument("phaseId") { type = NavType.StringType },
                ),
            ) { entry ->
                val request = PlayerRequest.PlanPhase(
                    planId = entry.arguments?.getString("planId").orEmpty(),
                    phaseId = entry.arguments?.getString("phaseId").orEmpty(),
                )
                PlayerScreen(request, onClose = { nav.popBackStack() })
            }
            composable(
                route = "player/exercise/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val request = PlayerRequest.SingleExercise(entry.arguments?.getString("id").orEmpty())
                PlayerScreen(request, onClose = { nav.popBackStack() })
            }
        }
    }
}
